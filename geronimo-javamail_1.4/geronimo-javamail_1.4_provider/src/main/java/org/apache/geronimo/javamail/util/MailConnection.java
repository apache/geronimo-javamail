/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.geronimo.javamail.util;

import java.io.IOException; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.PrintStream; 
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket; 
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer; 

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.net.ssl.SSLSocket;

import org.apache.geronimo.javamail.authentication.ClientAuthenticator; 
import org.apache.geronimo.javamail.authentication.CramMD5Authenticator; 
import org.apache.geronimo.javamail.authentication.DigestMD5Authenticator; 
import org.apache.geronimo.javamail.authentication.LoginAuthenticator; 
import org.apache.geronimo.javamail.authentication.PlainAuthenticator; 
import org.apache.geronimo.javamail.authentication.SASLAuthenticator; 
import org.apache.geronimo.javamail.util.CommandFailedException;      
import org.apache.geronimo.javamail.util.InvalidCommandException;      

/**
 * Base class for all mail Store/Transport connection.  Centralizes management
 * of a lot of common connection handling.  Actual protcol-specific 
 * functions are handled at the subclass level. 
 */
public class MailConnection {
    /**
     * constants for EOL termination
     */
    protected static final char CR = '\r';
    protected static final char LF = '\n';

    /**
     * property keys for protocol properties.
     */
    protected static final String MAIL_AUTH = "auth";
    protected static final String MAIL_PORT = "port";
    protected static final String MAIL_LOCALHOST = "localhost";
    protected static final String MAIL_STARTTLS_ENABLE = "starttls.enable";
    protected static final String MAIL_SSL_ENABLE = "ssl.enable";
    protected static final String MAIL_TIMEOUT = "timeout";
    protected static final String MAIL_SASL_ENABLE = "sasl.enable";
    protected static final String MAIL_SASL_REALM = "sasl.realm";
    protected static final String MAIL_AUTHORIZATIONID = "sasl.authorizationid"; 
    protected static final String MAIL_SASL_MECHANISMS = "sasl.mechanisms";
    protected static final String MAIL_PLAIN_DISABLE = "auth.plain.disable";
    protected static final String MAIL_LOGIN_DISABLE = "auth.login.disable";
    protected static final String MAIL_FACTORY_CLASS = "socketFactory.class";
    protected static final String MAIL_FACTORY_FALLBACK = "socketFactory.fallback";
    protected static final String MAIL_FACTORY_PORT = "socketFactory.port";
    protected static final String MAIL_SSL_PROTOCOLS = "ssl.protocols";
    protected static final String MAIL_SSL_CIPHERSUITES = "ssl.ciphersuites";
    protected static final String MAIL_LOCALADDRESS = "localaddress";
    protected static final String MAIL_LOCALPORT = "localport";
    protected static final String MAIL_ENCODE_TRACE = "encodetrace";

    protected static final int MIN_MILLIS = 1000 * 60;
    protected static final int TIMEOUT = MIN_MILLIS * 5;
    protected static final String DEFAULT_MAIL_HOST = "localhost";

    protected static final String CAPABILITY_STARTTLS = "STARTTLS";

    protected static final String AUTHENTICATION_PLAIN = "PLAIN";
    protected static final String AUTHENTICATION_LOGIN = "LOGIN";
    protected static final String AUTHENTICATION_CRAMMD5 = "CRAM-MD5";
    protected static final String AUTHENTICATION_DIGESTMD5 = "DIGEST-MD5";
    
    // The mail Session we're associated with
    protected Session session; 
    // The protocol we're implementing 
    protected String protocol; 
    // There are usually SSL and non-SSL versions of these protocols.  This 
    // indicates which version we're using.
    protected boolean sslConnection; 
    // This is the default port we should be using for making a connection.  Each 
    // protocol (and each ssl version of the protocol) normally has a different default that 
    // should be used. 
    protected int defaultPort; 
    
    // a wrapper around our session to provide easier lookup of protocol 
    // specific property values 
    protected ProtocolProperties props; 
    
    // The target server host 
    protected String serverHost;
    // The target server port 
    protected int serverPort; 
    
    // the connection socket...can be a plain socket or SSLSocket, if TLS is being used.
    protected Socket socket;
    
    // our local host name
    protected InetAddress localAddress;
    // our local port value 
    protected int localPort; 
    // our local host name
    protected String localHost;
    
    // our timeout value 
    protected int timeout; 
    
    // our login username 
    protected String username; 
    // our login password 
    protected String password; 
    // our SASL security realm 
    protected String realm; 
    // our authorization id 
    protected String authid; 
    
    // input stream used to read data.  If Sasl is in use, this might be other than the
    // direct access to the socket input stream.
    protected InputStream inputStream;
    // the other end of the connection pipeline.
    protected OutputStream outputStream;

    // our session provided debug output stream.
    protected PrintStream debugStream;
    // our debug flag (passed from the hosting transport)
    protected boolean debug;

    // list of authentication mechanisms supported by the server
    protected List authentications;
    // map of server extension arguments
    protected Map capabilities;        
    // property list of authentication mechanisms
    protected List mechanisms; 
    
    protected MailConnection(ProtocolProperties props) 
    {
        // this is our properties retriever utility, which will look up 
        // properties based on the appropriate "mail.protocol." prefix. 
        // this also holds other information we might need for access, such as 
        // the protocol name and the Session; 
        this.props = props; 
        this.protocol = props.getProtocol(); 
        this.session = props.getSession(); 
        this.sslConnection = props.getSSLConnection(); 
        this.defaultPort = props.getDefaultPort(); 
        
        // initialize our debug settings from the session 
        debug = session.getDebug(); 
        debugStream = session.getDebugOut();
    }
    
    
    /**
     * Connect to the server and do the initial handshaking.
     * 
     * @param host     The target host name.
     * @param port     The target port
     * @param username The connection username (can be null)
     * @param password The authentication password (can be null).
     * 
     * @return true if we were able to obtain a connection and 
     *         authenticate.
     * @exception MessagingException
     */
    public boolean protocolConnect(String host, int port, String username, String password) throws MessagingException {
        // NOTE:  We don't check for the username/password being null at this point.  It's possible that 
        // the server will send back a PREAUTH response, which means we don't need to go through login 
        // processing.  We'll need to check the capabilities response after we make the connection to decide 
        // if logging in is necesssary. 
        
        // save this for subsequent connections.  All pool connections will use this info.
        // if the port is defaulted, then see if we have something configured in the session.
        // if not configured, we just use the default default.
        if (port == -1) {
            // check for a property and fall back on the default if it's not set.
            port = props.getIntProperty(MAIL_PORT, props.getDefaultPort());
            // it's possible that -1 might have been explicitly set, so one last check. 
            if (port == -1) {
                port = props.getDefaultPort(); 
            }
        }
    	
    	// Before we do anything, let's make sure that we succesfully received a host
    	if ( host == null ) {
    		host = DEFAULT_MAIL_HOST;
    	}
        
        this.serverHost = host;
        this.serverPort = port;
        this.username = username;
        this.password = password;
        
        // make sure we have the realm information 
        realm = props.getProperty(MAIL_SASL_REALM); 
        // get an authzid value, if we have one.  The default is to use the username.
        authid = props.getProperty(MAIL_AUTHORIZATIONID, username);
        return true; 
    }
    
    
    /**
     * Establish a connection using an existing socket. 
     * 
     * @param s      The socket to use.
     */
    public void connect(Socket s) {
        // just save the socket connection 
        this.socket = s; 
    }
    
    
    /**
     * Create a transport connection object and connect it to the
     * target server.
     *
     * @exception MessagingException
     */
    protected void getConnection() throws IOException, MessagingException
    {
        // We might have been passed a socket to connect with...if not, we need to create one of the correct type.
        if (socket == null) {
            // get the connection properties that control how we set this up. 
            getConnectionProperties(); 
            // if this is the SSL version of the protocol, we start with an SSLSocket
            if (sslConnection) {
                getConnectedSSLSocket();
            }
            else
            {
                getConnectedSocket();
            }
        }
        // if we already have a socket, get some information from it and override what we've been passed.
        else {
            localPort = socket.getPort();
            localAddress = socket.getInetAddress();
        }
        
        // now set up the input/output streams.
        getConnectionStreams(); 
    }

    /**
     * Get common connection properties before creating a connection socket. 
     */
    protected void getConnectionProperties() {

        // there are several protocol properties that can be set to tune the created socket.  We need to
        // retrieve those bits before creating the socket.
        timeout = props.getIntProperty(MAIL_TIMEOUT, -1);
        localAddress = null;
        // see if we have a local address override.
        String localAddrProp = props.getProperty(MAIL_LOCALADDRESS);
        if (localAddrProp != null) {
            try {
                localAddress = InetAddress.getByName(localAddrProp);
            } catch (UnknownHostException e) {
                // not much we can do if this fails. 
            }
        }

        // check for a local port...default is to allow socket to choose.
        localPort = props.getIntProperty(MAIL_LOCALPORT, 0);
    }
    

    /**
     * Creates a connected socket
     *
     * @exception MessagingException
     */
    protected void getConnectedSocket() throws IOException {
        debugOut("Attempting plain socket connection to server " + serverHost + ":" + serverPort);

        // check the properties that control how we connect. 
        getConnectionProperties(); 

        // the socket factory can be specified via a session property.  By default, we just directly
        // instantiate a socket without using a factory.
        String socketFactory = props.getProperty(MAIL_FACTORY_CLASS);

        // make sure the socket is nulled out to start 
        socket = null;

        // if there is no socket factory defined (normal), we just create a socket directly.
        if (socketFactory == null) {
            socket = new Socket(serverHost, serverPort, localAddress, localPort);
        }

        else {
            try {
                int socketFactoryPort = props.getIntProperty(MAIL_FACTORY_PORT, -1);

                // we choose the port used by the socket based on overrides.
                Integer portArg = new Integer(socketFactoryPort == -1 ? serverPort : socketFactoryPort);

                // use the current context loader to resolve this.
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Class factoryClass = loader.loadClass(socketFactory);

                // done indirectly, we need to invoke the method using reflection.
                // This retrieves a factory instance.
                Method getDefault = factoryClass.getMethod("getDefault", new Class[0]);
                Object defFactory = getDefault.invoke(new Object(), new Object[0]);

                // now that we have the factory, there are two different createSocket() calls we use,
                // depending on whether we have a localAddress override.

                if (localAddress != null) {
                    // retrieve the createSocket(String, int, InetAddress, int) method.
                    Class[] createSocketSig = new Class[] { String.class, Integer.TYPE, InetAddress.class, Integer.TYPE };
                    Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);

                    Object[] createSocketArgs = new Object[] { serverHost, portArg, localAddress, new Integer(localPort) };
                    socket = (Socket)createSocket.invoke(defFactory, createSocketArgs);
                }
                else {
                    // retrieve the createSocket(String, int) method.
                    Class[] createSocketSig = new Class[] { String.class, Integer.TYPE };
                    Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);

                    Object[] createSocketArgs = new Object[] { serverHost, portArg };
                    socket = (Socket)createSocket.invoke(defFactory, createSocketArgs);
                }
            } catch (Throwable e) {
                // if a socket factor is specified, then we may need to fall back to a default.  This behavior
                // is controlled by (surprise) more session properties.
                if (props.getBooleanProperty(MAIL_FACTORY_FALLBACK, false)) {
                    debugOut("First plain socket attempt failed, falling back to default factory", e);
                    socket = new Socket(serverHost, serverPort, localAddress, localPort);
                }
                // we have an exception.  We're going to throw an IOException, which may require unwrapping
                // or rewrapping the exception.
                else {
                    // we have an exception from the reflection, so unwrap the base exception
                    if (e instanceof InvocationTargetException) {
                        e = ((InvocationTargetException)e).getTargetException();
                    }

                    debugOut("Plain socket creation failure", e);

                    // throw this as an IOException, with the original exception attached.
                    IOException ioe = new IOException("Error connecting to " + serverHost + ", " + serverPort);
                    ioe.initCause(e);
                    throw ioe;
                }
            }
        }
        // if we have a timeout value, set that before returning 
        if (timeout >= 0) {
            socket.setSoTimeout(timeout);
        }
    }


    /**
     * Creates a connected SSL socket for an initial SSL connection.
     *
     * @exception MessagingException
     */
    protected void getConnectedSSLSocket() throws IOException {
        debugOut("Attempting SSL socket connection to server " + serverHost + ":" + serverPort);
        // the socket factory can be specified via a protocol property, a session property, and if all else
        // fails (which it usually does), we fall back to the standard factory class.
        String socketFactory = props.getProperty(MAIL_FACTORY_CLASS, "javax.net.ssl.SSLSocketFactory");

        // make sure this is null 
        socket = null;

        // we'll try this with potentially two different factories if we're allowed to fall back.
        boolean fallback = props.getBooleanProperty(MAIL_FACTORY_FALLBACK, false);

        while (true) {
            try {
                debugOut("Creating SSL socket using factory " + socketFactory);

                int socketFactoryPort = props.getIntProperty(MAIL_FACTORY_PORT, -1);

                // we choose the port used by the socket based on overrides.
                Integer portArg = new Integer(socketFactoryPort == -1 ? serverPort : socketFactoryPort);

                // use the current context loader to resolve this.
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Class factoryClass = loader.loadClass(socketFactory);

                // done indirectly, we need to invoke the method using reflection.
                // This retrieves a factory instance.
                Method getDefault = factoryClass.getMethod("getDefault", new Class[0]);
                Object defFactory = getDefault.invoke(new Object(), new Object[0]);

                // now that we have the factory, there are two different createSocket() calls we use,
                // depending on whether we have a localAddress override.

                if (localAddress != null) {
                    // retrieve the createSocket(String, int, InetAddress, int) method.
                    Class[] createSocketSig = new Class[] { String.class, Integer.TYPE, InetAddress.class, Integer.TYPE };
                    Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);

                    Object[] createSocketArgs = new Object[] { serverHost, portArg, localAddress, new Integer(localPort) };
                    socket = (Socket)createSocket.invoke(defFactory, createSocketArgs);
                    break; 
                }
                else {
                    // retrieve the createSocket(String, int) method.
                    Class[] createSocketSig = new Class[] { String.class, Integer.TYPE };
                    Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);

                    Object[] createSocketArgs = new Object[] { serverHost, portArg };
                    socket = (Socket)createSocket.invoke(defFactory, createSocketArgs);
                    break; 
                }
            } catch (Throwable e) {
                // if we're allowed to fallback, then use the default factory and try this again.  We only
                // allow this to happen once.
                if (fallback) {
                    debugOut("First attempt at creating SSL socket failed, falling back to default factory");
                    socketFactory = "javax.net.ssl.SSLSocketFactory";
                    fallback = false;
                    continue;
                }
                // we have an exception.  We're going to throw an IOException, which may require unwrapping
                // or rewrapping the exception.
                else {
                    // we have an exception from the reflection, so unwrap the base exception
                    if (e instanceof InvocationTargetException) {
                        e = ((InvocationTargetException)e).getTargetException();
                    }

                    debugOut("Failure creating SSL socket", e);
                    // throw this as an IOException, with the original exception attached.
                    IOException ioe = new IOException("Error connecting to " + serverHost + ", " + serverPort);
                    ioe.initCause(e);
                    throw ioe;
                }
            }
        }
        // and set the timeout value 
        if (timeout >= 0) {
            socket.setSoTimeout(timeout);
        }
        
        // if there is a list of protocols specified, we need to break this down into 
        // the individual names 
        String protocols = props.getProperty(MAIL_SSL_PROTOCOLS); 
        if (protocols != null) {
            ArrayList list = new ArrayList(); 
            StringTokenizer t = new StringTokenizer(protocols); 
            
            while (t.hasMoreTokens()) {
                list.add(t.nextToken()); 
            }
            
            ((SSLSocket)socket).setEnabledProtocols((String[])list.toArray(new String[list.size()])); 
        }
        
        // and do the same for any cipher suites 
        String suites = props.getProperty(MAIL_SSL_CIPHERSUITES); 
        if (suites != null) {
            ArrayList list = new ArrayList(); 
            StringTokenizer t = new StringTokenizer(suites); 
            
            while (t.hasMoreTokens()) {
                list.add(t.nextToken()); 
            }
            
            ((SSLSocket)socket).setEnabledCipherSuites((String[])list.toArray(new String[list.size()])); 
        }
    }


    /**
     * Switch the connection to using TLS level security,
     * switching to an SSL socket.
     */
    protected void getConnectedTLSSocket() throws MessagingException {
     	// it worked, now switch the socket into TLS mode
     	try {

            // we use the same target and port as the current connection.
            String host = socket.getInetAddress().getHostName();
            int port = socket.getPort();

            // the socket factory can be specified via a session property.  By default, we use
            // the native SSL factory.
            String socketFactory = props.getProperty(MAIL_FACTORY_CLASS, "javax.net.ssl.SSLSocketFactory");

            // use the current context loader to resolve this.
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class factoryClass = loader.loadClass(socketFactory);

            // done indirectly, we need to invoke the method using reflection.
            // This retrieves a factory instance.
            Method getDefault = factoryClass.getMethod("getDefault", new Class[0]);
            Object defFactory = getDefault.invoke(new Object(), new Object[0]);

            // now we need to invoke createSocket()
            Class[] createSocketSig = new Class[] { Socket.class, String.class, Integer.TYPE, Boolean.TYPE };
            Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);

            Object[] createSocketArgs = new Object[] { socket, host, new Integer(port), Boolean.TRUE };

            // and finally create the socket
            Socket sslSocket = (Socket)createSocket.invoke(defFactory, createSocketArgs);

            // if this is an instance of SSLSocket (very common), try setting the protocol to be
            // "TLSv1".  If this is some other class because of a factory override, we'll just have to
            // accept that things will work.
            if (sslSocket instanceof SSLSocket) {
                ((SSLSocket)sslSocket).setEnabledProtocols(new String[] {"TLSv1"} );
                ((SSLSocket)sslSocket).setUseClientMode(true);
                debugOut("Initiating STARTTLS handshake"); 
                ((SSLSocket)sslSocket).startHandshake();
            }


            // this is our active socket now
            socket = sslSocket;
            getConnectionStreams(); 
            debugOut("TLS connection established"); 
     	}
        catch (Exception e) {
            debugOut("Failure attempting to convert connection to TLS", e);
     	    throw new MessagingException("Unable to convert connection to SSL", e);
     	}
    }
    
    
    /**
     * Set up the input and output streams for server communications once the 
     * socket connection has been made. 
     * 
     * @exception MessagingException
     */
    protected void getConnectionStreams() throws MessagingException, IOException {
        // and finally, as a last step, replace our input streams with the secure ones.
        // now set up the input/output streams.
        inputStream = new TraceInputStream(socket.getInputStream(), debugStream, debug, props.getBooleanProperty(
                MAIL_ENCODE_TRACE, false));
        outputStream = new TraceOutputStream(socket.getOutputStream(), debugStream, debug, props.getBooleanProperty(
                MAIL_ENCODE_TRACE, false));
    }
    

    /**
     * Close the server connection at termination.
     */
    public void closeServerConnection()
    {
        try {
            socket.close();
        } catch (IOException ignored) {
        }

        socket = null;
        inputStream = null;
        outputStream = null;
    }
    
    
    /**
     * Verify that we have a good connection before 
     * attempting to send a command. 
     * 
     * @exception MessagingException
     */
    protected void checkConnected() throws MessagingException {
        if (socket == null || !socket.isConnected()) {
            throw new MessagingException("no connection");
        }
    }


    /**
     * Retrieve the SASL realm used for DIGEST-MD5 authentication.
     * This will either be explicitly set, or retrieved using the
     * mail.imap.sasl.realm session property.
     *
     * @return The current realm information (which can be null).
     */
    public String getSASLRealm() {
        // if the realm is null, retrieve it using the realm session property.
        if (realm == null) {
            realm = props.getProperty(MAIL_SASL_REALM);
        }
        return realm;
    }


    /**
     * Explicitly set the SASL realm used for DIGEST-MD5 authenticaiton.
     *
     * @param name   The new realm name.
     */
    public void setSASLRealm(String name) {
        realm = name;
    }


    /**
     * Get a list of the SASL mechanisms we're configured to accept.
     *
     * @return A list of mechanisms we're allowed to use.
     */
    protected List getSaslMechanisms() {
        if (mechanisms == null) {
            mechanisms = new ArrayList();
            String mechList = props.getProperty(MAIL_SASL_MECHANISMS);
            if (mechList != null) {
                // the mechanisms are a blank or comma-separated list
                StringTokenizer tokenizer = new StringTokenizer(mechList, " ,");

                while (tokenizer.hasMoreTokens()) {
                    String mech = tokenizer.nextToken().toUpperCase();
                    mechanisms.add(mech);
                }
            }
        }
        return mechanisms;
    }
    
    
    /**
     * Get the list of authentication mechanisms the server
     * is supposed to support. 
     * 
     * @return A list of the server supported authentication 
     *         mechanisms.
     */
    protected List getServerMechanisms() {
        return authentications; 
    }
    
    
    /**
     * Merge the configured SASL mechanisms with the capabilities that the 
     * server has indicated it supports, returning a merged list that can 
     * be used for selecting a mechanism. 
     * 
     * @return A List representing the intersection of the configured list and the 
     *         capabilities list.
     */
    protected List selectSaslMechanisms() {
        List configured = getSaslMechanisms(); 
        List supported = getServerMechanisms(); 
        
        // if not restricted, then we'll select from anything supported. 
        if (configured.isEmpty()) {
            return supported; 
        }
        
        List merged = new ArrayList(); 
        
        // we might need a subset of the supported ones 
        for (int i = 0; i < configured.size(); i++) {
            // if this is in both lists, add to the merged one. 
            String mech = (String)configured.get(i); 
            if (supported.contains(mech)) {
                merged.add(mech); 
            }
        }
        return merged; 
    }


    /**
     * Process SASL-type authentication.
     *
     * @return An authenticator to process the login challenge/response handling.    
     * @exception MessagingException
     */
    protected ClientAuthenticator getLoginAuthenticator() throws MessagingException {
        
        // get the list of mechanisms we're allowed to use. 
        List mechs = selectSaslMechanisms(); 

        try {
            String[] mechArray = (String[])mechs.toArray(new String[0]); 
            // create a SASLAuthenticator, if we can.  A failure likely indicates we're not 
            // running on a Java 5 VM, and the Sasl API isn't available. 
            return new SASLAuthenticator(mechArray, session.getProperties(), protocol, serverHost, getSASLRealm(), authid, username, password); 
        } catch (Throwable e) {
        }
        

        // now go through the progression of mechanisms we support, from the most secure to the
        // least secure.

        if (mechs.contains(AUTHENTICATION_DIGESTMD5)) {
            return new DigestMD5Authenticator(serverHost, username, password, getSASLRealm());
        }
        else if (mechs.contains(AUTHENTICATION_CRAMMD5)) {
            return new CramMD5Authenticator(username, password);
        }
        else if (mechs.contains(AUTHENTICATION_LOGIN)) {
            return new LoginAuthenticator(username, password);
        }
        else if (mechs.contains(AUTHENTICATION_PLAIN)) {
            return new PlainAuthenticator(authid, username, password);
        }
        else {
            // can't find a mechanism we support in common
            return null;  
        }
    }
    
    
    /**
     * Internal debug output routine.
     *
     * @param value  The string value to output.
     */
    protected void debugOut(String message) {
        if (debug) {
            debugStream.println(protocol + " DEBUG: " + message);
        }
    }

    /**
     * Internal debugging routine for reporting exceptions.
     *
     * @param message A message associated with the exception context.
     * @param e       The received exception.
     */
    protected void debugOut(String message, Throwable e) {
        if (debug) {
            debugOut("Received exception -> " + message);
            debugOut("Exception message -> " + e.getMessage());
            e.printStackTrace(debugStream);
        }
    }
    
    
    /**
     * Test if this connection has a given capability. 
     * 
     * @param capability The capability name.
     * 
     * @return true if this capability is in the list, false for a mismatch. 
     */
    public boolean hasCapability(String capability) {
        return capabilities.containsKey(capability); 
    }
    
    /**
     * Get the capabilities map. 
     * 
     * @return The capabilities map for the connection. 
     */
    public Map getCapabilities() {
        return capabilities; 
    }
    
    
    /**
     * Test if the server supports a given mechanism. 
     * 
     * @param mech   The mechanism name.
     * 
     * @return true if the server has asserted support for the named 
     *         mechanism.
     */
    public boolean supportsMechanism(String mech) {
        return authentications.contains(mech); 
    }
    
    
    /**
     * Retrieve the connection host. 
     * 
     * @return The host name. 
     */
    public String getHost() {
        return serverHost; 
    }
    

    /**
     * Retrieve the local client host name.
     *
     * @return The string version of the local host name.
     * @exception SMTPTransportException
     */
    public String getLocalHost() throws MessagingException {
        if (localHost == null) {

            if (localHost == null) {
                localHost = props.getProperty(MAIL_LOCALHOST);
            }

            if (localHost == null) {
                localHost = props.getSessionProperty(MAIL_LOCALHOST);
            }

            if (localHost == null) {
        	try {
            	    localHost = InetAddress.getLocalHost().getHostName();
                } catch (UnknownHostException e) {
	            // fine, we're misconfigured - ignore
    	        }
	    }

            if (localHost == null) {
                throw new MessagingException("Can't get local hostname. "
                        + " Please correctly configure JDK/DNS or set mail.smtp.localhost");
            }
        }

        return localHost;
    }

    
    /**
     * Explicitly set the local host information.
     *
     * @param localHost
     *            The new localHost name.
     */
    public void setLocalHost(String localHost) {
        this.localHost = localHost;
    }
}
