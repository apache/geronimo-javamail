/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.geronimo.javamail.store.pop3;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;

import javax.mail.MessagingException;
import javax.mail.Session;

/**
 * Represents a connection with the POP3 mail server. The connection is owned by
 * a pop3 store and is only associated with one user who owns the respective
 * POP3Store instance
 * 
 * @version $Rev$ $Date$
 */


public class POP3Connection {

	protected static final String MAIL_SSLFACTORY_CLASS = "mail.SSLSocketFactory.class";
	
    protected static final String MAIL_POP3_FACTORY_CLASS = "socketFactory.class";

    protected static final String MAIL_POP3_FACTORY_FALLBACK = "socketFactory.fallback";

    protected static final String MAIL_POP3_FACTORY_PORT = "socketFactory.port";
    
    protected static final String MAIL_POP3_LOCALADDRESS = "localAddress";
    
    protected static final String MAIL_POP3_LOCALPORT = "localPort";
    
    protected static final String MAIL_POP3_TIMEOUT = "timeout";
	
    private Socket socket;

    private Session session;

    private String host;

    private int port;

    private PrintWriter writer;

    private BufferedReader reader;
    
    private String protocol;
    
    private boolean sslConnection;

    POP3Connection(Session session, String host, int port, boolean sslConnection, String protocol) {

        this.session = session;
        this.host = host;
        this.port = port;
        this.sslConnection = sslConnection;
        this.protocol = protocol;
    }

    public void open() throws Exception {
        try {

        	if (!sslConnection) {
        		getConnectedSocket();
        	} else {
        		getConnectedSSLSocket();
        	}

            if (session.getDebug()) {
                session.getDebugOut().println("Connection successful " + this.toString());
            }

            buildInputReader();
            buildOutputWriter();

            // consume the greeting
            if (session.getDebug()) {
                session.getDebugOut().println("Greeting from server " + reader.readLine());
            } else {
                reader.readLine();
            }

        } catch (IOException e) {
            Exception ex = new Exception("Error opening connection " + this.toString(), e);
            throw ex;
        }
    }

    void close() throws Exception {
        try {
            socket.close();
            if (session.getDebug()) {
                session.getDebugOut().println("Connection successfuly closed " + this.toString());
            }

        } catch (IOException e) {
            Exception ex = new Exception("Error closing connection " + this.toString(), e);
            throw ex;
        }

    }

    public synchronized POP3Response sendCommand(POP3Command cmd) throws MessagingException {
        if (socket.isConnected()) {

            // if the underlying output stream is down
            // attempt to rebuild the writer
            if (socket.isOutputShutdown()) {
                buildOutputWriter();
            }

            // if the underlying inout stream is down
            // attempt to rebuild the reader
            if (socket.isInputShutdown()) {
                buildInputReader();
            }

            if (session.getDebug()) {
                session.getDebugOut().println("\nCommand sent " + cmd.getCommand());
            }

            POP3Response res = null;

            // this method supresses IOException
            // but choose bcos of ease of use
            {
                writer.write(cmd.getCommand());
                writer.flush();
                res = POP3ResponseBuilder.buildResponse(session, reader, cmd.isMultiLineResponse());
            }

            return res;
        }

        throw new MessagingException("Connection to Mail Server is lost, connection " + this.toString());
    }

    private void buildInputReader() throws MessagingException {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            throw new MessagingException("Error obtaining input stream " + this.toString(), e);
        }
    }

    private void buildOutputWriter() throws MessagingException {
        try {
            writer = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()));
        } catch (IOException e) {
            throw new MessagingException("Error obtaining output stream " + this.toString(), e);
        }
    }

    public String toString() {
        return "POP3Connection host: " + host + " port: " + port;
    }


	/**
	 * Creates a connected socket
	 *
	 * @exception MessagingException
	 */
	protected void getConnectedSocket() throws IOException {
	
	    // the socket factory can be specified via a session property. By
	    // default, we just directly
	    // instantiate a socket without using a factor.
	    String socketFactory = getProtocolProperty(MAIL_POP3_FACTORY_CLASS);
	
	    // there are several protocol properties that can be set to tune the
	    // created socket. We need to
	    // retrieve those bits before creating the socket.
	    int timeout = getIntProtocolProperty(MAIL_POP3_TIMEOUT, -1);
	    InetAddress localAddress = null;
	    // see if we have a local address override.
	    String localAddrProp = getProtocolProperty(MAIL_POP3_LOCALADDRESS);
	    if (localAddrProp != null) {
	        localAddress = InetAddress.getByName(localAddrProp);
	    }
	
	    // check for a local port...default is to allow socket to choose.
	    int localPort = getIntProtocolProperty(MAIL_POP3_LOCALPORT, 0);
	
	    socket = null;
	
	    // if there is no socket factory defined (normal), we just create a
	    // socket directly.
	    if (socketFactory == null) {
	        socket = new Socket(host, port, localAddress, localPort);
	    }
	
	    else {
	        try {
	            int socketFactoryPort = getIntProtocolProperty(MAIL_POP3_FACTORY_PORT, -1);
	
	            // we choose the port used by the socket based on overrides.
	            Integer portArg = new Integer(socketFactoryPort == -1 ? port : socketFactoryPort);
	
	            // use the current context loader to resolve this.
	            ClassLoader loader = Thread.currentThread().getContextClassLoader();
	            Class factoryClass = loader.loadClass(socketFactory);
	
	            // done indirectly, we need to invoke the method using
	            // reflection.
	            // This retrieves a factory instance.
	            Method getDefault = factoryClass.getMethod("getDefault", new Class[0]);
	            Object defFactory = getDefault.invoke(new Object(), new Object[0]);
	
	            // now that we have the factory, there are two different
	            // createSocket() calls we use,
	            // depending on whether we have a localAddress override.
	
	            if (localAddress != null) {
	                // retrieve the createSocket(String, int, InetAddress, int)
	                // method.
	                Class[] createSocketSig = new Class[] { String.class, Integer.TYPE, InetAddress.class, Integer.TYPE };
	                Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);
	
	                Object[] createSocketArgs = new Object[] { host, portArg, localAddress, new Integer(localPort) };
	                socket = (Socket) createSocket.invoke(defFactory, createSocketArgs);
	            } else {
	                // retrieve the createSocket(String, int) method.
	                Class[] createSocketSig = new Class[] { String.class, Integer.TYPE };
	                Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);
	
	                Object[] createSocketArgs = new Object[] { host, portArg };
	                socket = (Socket) createSocket.invoke(defFactory, createSocketArgs);
	            }
	        } catch (Throwable e) {
	            // if a socket factory is specified, then we may need to fall
	            // back to a default. This behavior
	            // is controlled by (surprise) more session properties.
	            if (isProtocolPropertyTrue(MAIL_POP3_FACTORY_FALLBACK)) {
	                socket = new Socket(host, port, localAddress, localPort);
	            }
	            // we have an exception. We're going to throw an IOException,
	            // which may require unwrapping
	            // or rewrapping the exception.
	            else {
	                // we have an exception from the reflection, so unwrap the
	                // base exception
	                if (e instanceof InvocationTargetException) {
	                    e = ((InvocationTargetException) e).getTargetException();
	                }

	
	                // throw this as an IOException, with the original exception
	                // attached.
	                IOException ioe = new IOException("Error connecting to " + host + ", " + port);
	                ioe.initCause(e);
	                throw ioe;
	            }
	        }
	    }
	
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

	    if (session.getDebug()) {
	        session.getDebugOut().println("Attempting SSL socket connection to server " + host + ":" + port);
	    }
	    // the socket factory can be specified via a protocol property, a
	    // session property, and if all else
	    // fails (which it usually does), we fall back to the standard factory
	    // class.
	    String socketFactory = getProtocolProperty(MAIL_POP3_FACTORY_CLASS, getSessionProperty(MAIL_SSLFACTORY_CLASS,
	            "javax.net.ssl.SSLSocketFactory"));
	
	    // there are several protocol properties that can be set to tune the
	    // created socket. We need to
	    // retrieve those bits before creating the socket.
	    int timeout = getIntProtocolProperty(MAIL_POP3_TIMEOUT, -1);
	    InetAddress localAddress = null;
	    // see if we have a local address override.
	    String localAddrProp = getProtocolProperty(MAIL_POP3_LOCALADDRESS);
	    if (localAddrProp != null) {
	        localAddress = InetAddress.getByName(localAddrProp);
	    }
	
	    // check for a local port...default is to allow socket to choose.
	    int localPort = getIntProtocolProperty(MAIL_POP3_LOCALPORT, 0);
	
	    socket = null;
	
	    // if there is no socket factory defined (normal), we just create a
	    // socket directly.
	    if (socketFactory == null) {
	    	System.out.println("SocketFactory was null so creating the connection using a default");
	        socket = new Socket(host, port, localAddress, localPort);
	    }
	
	    else {
	        // we'll try this with potentially two different factories if we're
	        // allowed to fall back.
	        boolean fallback = isProtocolPropertyTrue(MAIL_POP3_FACTORY_FALLBACK);
	        while(true) {
	            try {
	            	
	            	
	            	if (socket != null) {
	            		if (socket.isConnected())
	            		break;
	            	}
	            	
	                if (session.getDebug()) {
	                    session.getDebugOut().println("Creating SSL socket using factory " + socketFactory);
	                }
	                
	                int socketFactoryPort = getIntProtocolProperty(MAIL_POP3_FACTORY_PORT, -1);
	
	                // we choose the port used by the socket based on overrides.
	                Integer portArg = new Integer(socketFactoryPort == -1 ? port : socketFactoryPort);
	
	                // use the current context loader to resolve this.
	                ClassLoader loader = Thread.currentThread().getContextClassLoader();
	                Class factoryClass = loader.loadClass(socketFactory);
	
	                // done indirectly, we need to invoke the method using
	                // reflection.
	                // This retrieves a factory instance.
	                Method getDefault = factoryClass.getMethod("getDefault", new Class[0]);
	                Object defFactory = getDefault.invoke(new Object(), new Object[0]);
	
	                // now that we have the factory, there are two different
	                // createSocket() calls we use,
	                // depending on whether we have a localAddress override.
	
	                if (localAddress != null) {
	                    // retrieve the createSocket(String, int, InetAddress,
	                    // int) method.
	                    Class[] createSocketSig = new Class[] { String.class, Integer.TYPE, InetAddress.class,
	                            Integer.TYPE };
	                    Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);
	
	                    Object[] createSocketArgs = new Object[] { host, portArg, localAddress, new Integer(localPort) };
	                    socket = (Socket) createSocket.invoke(defFactory, createSocketArgs);
	                } else {
	                    // retrieve the createSocket(String, int) method.
	                    Class[] createSocketSig = new Class[] { String.class, Integer.TYPE };
	                    Method createSocket = factoryClass.getMethod("createSocket", createSocketSig);
	
	                    Object[] createSocketArgs = new Object[] { host, portArg };
	                    socket = (Socket) createSocket.invoke(defFactory, createSocketArgs);
	                }
	            } catch (Throwable e) {
	                // if we're allowed to fallback, then use the default
	                // factory and try this again. We only
	                // allow this to happen once.
                    if (session.getDebug()) {
                        session.getDebugOut().println("First attempt at creating SSL socket failed, falling back to default factory");
                    }
	                if (fallback) {
	                    socketFactory = "javax.net.ssl.SSLSocketFactory";
	                    fallback = false;
	                    continue;
	                }
	                // we have an exception. We're going to throw an
	                // IOException, which may require unwrapping
	                // or rewrapping the exception.
	                else {
	                    // we have an exception from the reflection, so unwrap
	                    // the base exception
	                    if (e instanceof InvocationTargetException) {
	                        e = ((InvocationTargetException) e).getTargetException();
	                    }
	
	                    if (session.getDebug()) {
	                        session.getDebugOut().println("Failure creating SSL socket: " + e);
	                    }
	                    // throw this as an IOException, with the original
	                    // exception attached.
	                    IOException ioe = new IOException("Error connecting to " + host + ", " + port);
	                    ioe.initCause(e);
	                    throw ioe;
	                }
	            }
	        }
	    }
	
	    if (timeout >= 0) {
	        socket.setSoTimeout(timeout);
	    }
	}
	
    /**
     * Process a session property as a boolean value, returning either true or
     * false.
     *
     * @return True if the property value is "true". Returns false for any other
     *         value (including null).
     */
    protected boolean isProtocolPropertyTrue(String name) {
        // the name we're given is the least qualified part of the name. We
        // construct the full property name
        // using the protocol ("pop3").
        String fullName = "mail." + protocol + "." + name;
        return isSessionPropertyTrue(fullName);
    }

    /**
     * Process a session property as a boolean value, returning either true or
     * false.
     *
     * @return True if the property value is "true". Returns false for any other
     *         value (including null).
     */
    protected boolean isSessionPropertyTrue(String name) {
        String property = session.getProperty(name);
        if (property != null) {
            return property.equals("true");
        }
        return false;
    }
    
    /**
     * Get a property associated with this mail session as an integer value.
     * Returns the default value if the property doesn't exist or it doesn't
     * have a valid int value.
     *
     * @param name
     *            The name of the property.
     * @param defaultValue
     *            The default value to return if the property doesn't exist.
     *
     * @return The property value converted to an int.
     */
    protected int getIntProtocolProperty(String name, int defaultValue) {
        // the name we're given is the least qualified part of the name. We
        // construct the full property name
        // using the protocol (pop3).
        String fullName = "mail." + protocol + "." + name;
        return getIntSessionProperty(fullName, defaultValue);
    }
    
    /**
     * Get a property associated with this mail session as an integer value.
     * Returns the default value if the property doesn't exist or it doesn't
     * have a valid int value.
     *
     * @param name
     *            The name of the property.
     * @param defaultValue
     *            The default value to return if the property doesn't exist.
     *
     * @return The property value converted to an int.
     */
    protected int getIntSessionProperty(String name, int defaultValue) {
        String result = getSessionProperty(name);
        if (result != null) {
            try {
                // convert into an int value.
                return Integer.parseInt(result);
            } catch (NumberFormatException e) {
            }
        }
        // return default value if it doesn't exist is isn't convertable.
        return defaultValue;
    }

    /**
     * Get a property associated with this mail session. Returns the provided
     * default if it doesn't exist.
     *
     * @param name
     *            The name of the property.
     * @param defaultValue
     *            The default value to return if the property doesn't exist.
     *
     * @return The property value (returns defaultValue if the property has not
     *         been set).
     */
    protected String getSessionProperty(String name, String defaultValue) {
        String result = session.getProperty(name);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    /**
     * Get a property associated with this mail session. Returns the provided
     * default if it doesn't exist.
     *
     * @param name
     *            The name of the property.
     * @param defaultValue
     *            The default value to return if the property doesn't exist.
     *
     * @return The property value (returns defaultValue if the property has not
     *         been set).
     */
    protected String getProtocolProperty(String name, String defaultValue) {
        // the name we're given is the least qualified part of the name. We
        // construct the full property name
        // using the protocol ("pop3").
        String fullName = "mail." + protocol + "." + name;
        return getSessionProperty(fullName, defaultValue);
    }
    
    /**
     * Get a property associated with this mail protocol.
     *
     * @param name
     *            The name of the property.
     *
     * @return The property value (returns null if the property has not been
     *         set).
     */
    protected String getProtocolProperty(String name) {
        // the name we're given is the least qualified part of the name. We
        // construct the full property name
        // using the protocol ("pop3").
        String fullName = "mail." + protocol + "." + name;
        return getSessionProperty(fullName);
    }

    /**
     * Get a property associated with this mail session.
     *
     * @param name
     *            The name of the property.
     *
     * @return The property value (returns null if the property has not been
     *         set).
     */
    protected String getSessionProperty(String name) {
        return session.getProperty(name);
    }
}
