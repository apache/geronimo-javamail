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

package org.apache.geronimo.javamail.transport.nntp;

import java.io.BufferedReader;
import java.io.BufferedOutputStream; 
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList; 
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;

import org.apache.geronimo.javamail.authentication.ClientAuthenticator; 
import org.apache.geronimo.javamail.authentication.AuthenticatorFactory; 
import org.apache.geronimo.javamail.util.MailConnection; 
import org.apache.geronimo.javamail.util.MIMEOutputStream;
import org.apache.geronimo.javamail.util.ProtocolProperties; 
import org.apache.geronimo.mail.util.Base64;
import org.apache.geronimo.mail.util.SessionUtil;

/**
 * Simple implementation of NNTP transport. Just does plain RFC977-ish delivery.
 * <p/> There is no way to indicate failure for a given recipient (it's possible
 * to have a recipient address rejected). The sun impl throws exceptions even if
 * others successful), but maybe we do a different way... <p/>
 * 
 * @version $Rev$ $Date$
 */
public class NNTPConnection extends MailConnection {

    /**
     * constants for EOL termination
     */
    protected static final char CR = '\r';

    protected static final char LF = '\n';

    /**
     * property keys for protocol properties.
     */
    protected static final int DEFAULT_NNTP_PORT = 119;
    // does the server support posting?
    protected boolean postingAllowed = true;
    
    // different authentication mechanisms 
    protected boolean authInfoUserAllowed = false; 
    protected boolean authInfoSaslAllowed = false; 
    
    // the last response line received from the server.
    protected NNTPReply lastServerResponse = null;

    // map of server extension arguments
    protected HashMap serverExtensionArgs;

    // the welcome string from the server.
    protected String welcomeString = null;
    
    // input reader wrapped around the socket input stream 
    protected BufferedReader reader; 
    // output writer wrapped around the socket output stream. 
    protected PrintWriter writer; 

    /**
     * Normal constructor for an NNTPConnection() object.
     * 
     * @param props  The property bundle for this protocol instance.
     */
    public NNTPConnection(ProtocolProperties props) {
        super(props);
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
        super.protocolConnect(host, port, username, password); 
        // create socket and connect to server.
        getConnection();

        // receive welcoming message
        getWelcome();
        
        return true; 
    }


    /**
     * Create a transport connection object and connect it to the
     * target server.
     *
     * @exception MessagingException
     */
    protected void getConnection() throws MessagingException
    {
        try {
            // do all of the non-protocol specific set up.  This will get our socket established 
            // and ready use. 
            super.getConnection(); 
        } catch (IOException e) {
            throw new MessagingException("Unable to obtain a connection to the NNTP server", e); 
        }
        
        // The NNTP protocol is inherently a string-based protocol, so we get 
        // string readers/writers for the connection streams 
        reader = new BufferedReader(new InputStreamReader(inputStream));
        writer = new PrintWriter(new BufferedOutputStream(outputStream));
    }
    

    /**
     * Close the connection. On completion, we'll be disconnected from the
     * server and unable to send more data.
     * 
     * @exception MessagingException
     */
    public void close() throws MessagingException {
        // if we're already closed, get outta here.
        if (socket == null) {
            return;
        }
        try {
            // say goodbye
            sendQuit();
        } finally {
            // and close up the connection. We do this in a finally block to
            // make sure the connection
            // is shut down even if quit gets an error.
            closeServerConnection();
            // get rid of our response processor too. 
            reader = null; 
            writer = null; 
        }
    }

    public String toString() {
        return "NNTPConnection host: " + serverHost + " port: " + serverPort;
    }
    

    /**
     * Get the servers welcome blob from the wire....
     */
    public void getWelcome() throws MessagingException {
        NNTPReply line = getReply();

        //
        if (line.isError()) {
            throw new MessagingException("Error connecting to news server: " + line.getMessage());
        }

        // remember we can post.
        if (line.getCode() == NNTPReply.POSTING_ALLOWED) {
            postingAllowed = true;
        } else {
            postingAllowed = false;
        }

        // the NNTP store will want to use the welcome string, so save it.
        welcomeString = line.getMessage();

        // find out what extensions this server supports.
        getExtensions();
    }

    
    /**
     * Sends the QUIT message and receieves the response
     */
    public void sendQuit() throws MessagingException {
        sendLine("QUIT");
    }
    

    /**
     * Tell the server to switch to a named group.
     * 
     * @param name
     *            The name of the target group.
     * 
     * @return The server response to the GROUP command.
     */
    public NNTPReply selectGroup(String name) throws MessagingException {
        // send the GROUP command
        return sendCommand("GROUP " + name);
    }
    

    /**
     * Ask the server what extensions it supports.
     * 
     * @return True if the command was accepted ok, false for any errors.
     * @exception MessagingException
     */
    protected void getExtensions() throws MessagingException {
        NNTPReply reply = sendCommand("LIST EXTENSIONS", NNTPReply.EXTENSIONS_SUPPORTED);

        // we get a 202 code back. The first line is just a greeting, and
        // extensions are delivered as data
        // lines terminated with a "." line.
        if (reply.getCode() != NNTPReply.EXTENSIONS_SUPPORTED) {
            return;
        }

        // get a fresh extension mapping table.
        capabilities = new HashMap();
        authentications = new ArrayList(); 

        // get the extension data lines.
        List extensions = reply.getData();

        // process all of the continuation lines
        for (int i = 0; i < extensions.size(); i++) {
            // go process the extention
            processExtension((String) extensions.get(i));
        }
    }

    
    /**
     * Process an extension string passed back as the LIST EXTENSIONS response.
     * 
     * @param extension
     *            The string value of the extension (which will be of the form
     *            "NAME arguments").
     */
    protected void processExtension(String extension) {
        String extensionName = extension.toUpperCase();
        String argument = "";

        int delimiter = extension.indexOf(' ');
        // if we have a keyword with arguments, parse them out and add to the
        // argument map.
        if (delimiter != -1) {
            extensionName = extension.substring(0, delimiter).toUpperCase();
            argument = extension.substring(delimiter + 1);
        }

        // add this to the map so it can be tested later.
        capabilities.put(extensionName, argument);

        // we need to determine which authentication mechanisms are supported here 
        if (extensionName.equals("AUTHINFO")) {
            StringTokenizer tokenizer = new StringTokenizer(argument); 
            
            while (tokenizer.hasMoreTokens()) {
                // we only know how to do USER or SASL 
                String mechanism = tokenizer.nextToken().toUpperCase();
                if (mechanism.equals("SASL")) {
                    authInfoSaslAllowed = true; 
                }
                else if (mechanism.equals("USER")) {
                    authInfoUserAllowed = true; 
                }
            }
        }
        // special case for some older servers.
        else if (extensionName.equals("SASL")) {
            // The security mechanisms are blank delimited tokens.
            StringTokenizer tokenizer = new StringTokenizer(argument);

            while (tokenizer.hasMoreTokens()) {
                String mechanism = tokenizer.nextToken().toUpperCase();
                authentications.add(mechanism);
            }
        }
    }
    

    /**
     * Retrieve any argument information associated with a extension reported
     * back by the server on the EHLO command.
     * 
     * @param name
     *            The name of the target server extension.
     * 
     * @return Any argument passed on a server extension. Returns null if the
     *         extension did not include an argument or the extension was not
     *         supported.
     */
    public String extensionParameter(String name) {
        if (serverExtensionArgs != null) {
            return (String) serverExtensionArgs.get(name);
        }
        return null;
    }

    /**
     * Tests whether the target server supports a named extension.
     * 
     * @param name
     *            The target extension name.
     * 
     * @return true if the target server reported on the EHLO command that is
     *         supports the targer server, false if the extension was not
     *         supported.
     */
    public boolean supportsExtension(String name) {
        // this only returns null if we don't have this extension
        return extensionParameter(name) != null;
    }

    
    /**
     * Sends the data in the message down the socket. This presumes the server
     * is in the right place and ready for getting the DATA message and the data
     * right place in the sequence
     */
    public synchronized void sendPost(Message msg) throws MessagingException {

        // send the POST command
        NNTPReply line = sendCommand("POST");

        if (line.getCode() != NNTPReply.SEND_ARTICLE) {
            throw new MessagingException("Server rejected POST command: " + line);
        }

        // we've received permission to send the data, so ask the message to
        // write itself out.
        try {
            // the data content has two requirements we need to meet by
            // filtering the
            // output stream. Requirement 1 is to conicalize any line breaks.
            // All line
            // breaks will be transformed into properly formed CRLF sequences.
            //
            // Requirement 2 is to perform byte-stuff for any line that begins
            // with a "."
            // so that data is not confused with the end-of-data marker (a
            // "\r\n.\r\n" sequence.
            //
            // The MIME output stream performs those two functions on behalf of
            // the content
            // writer.
            MIMEOutputStream mimeOut = new MIMEOutputStream(outputStream);

            msg.writeTo(mimeOut);

            // now to finish, we send a CRLF sequence, followed by a ".".
            mimeOut.writeSMTPTerminator();           
            // and flush the data to send it along 
            mimeOut.flush();   
        } catch (IOException e) {
            throw new MessagingException("I/O error posting message", e);
        } catch (MessagingException e) {
            throw new MessagingException("Exception posting message", e);
        }

        // use a longer time out here to give the server time to process the
        // data.
        line = new NNTPReply(receiveLine());

        if (line.getCode() != NNTPReply.POSTED_OK) {
            throw new MessagingException("Server rejected POST command: " + line);
        }
    }

    /**
     * Issue a command and retrieve the response. If the given success indicator
     * is received, the command is returning a longer response, terminated by a
     * "crlf.crlf" sequence. These lines are attached to the reply.
     * 
     * @param command
     *            The command to issue.
     * @param success
     *            The command reply that indicates additional data should be
     *            retrieved.
     * 
     * @return The command reply.
     */
    public synchronized NNTPReply sendCommand(String command, int success) throws MessagingException {
        NNTPReply reply = sendCommand(command);
        if (reply.getCode() == success) {
            reply.retrieveData(reader);
        }
        return reply;
    }

    /**
     * Send a command to the server, returning the first response line back as a
     * reply.
     * 
     * @param data
     *            The data to send.
     * 
     * @return A reply object with the reply line.
     * @exception MessagingException
     */
    public NNTPReply sendCommand(String data) throws MessagingException {
        sendLine(data);
        NNTPReply reply = getReply();
        // did the server just inform us we need to authenticate? The spec
        // allows this
        // response to be sent at any time, so we need to try to authenticate
        // and then retry the command.
        if (reply.getCode() == NNTPReply.AUTHINFO_REQUIRED || reply.getCode() == NNTPReply.AUTHINFO_SIMPLE_REQUIRED) {
            debugOut("Authentication required received from server.");
            // authenticate with the server, if necessary
            processAuthentication(reply.getCode());
            // if we've safely authenticated, we can reissue the command and
            // process the response.
            sendLine(data);
            reply = getReply();
        }
        return reply;
    }

    /**
     * Send a command to the server, returning the first response line back as a
     * reply.
     * 
     * @param data
     *            The data to send.
     * 
     * @return A reply object with the reply line.
     * @exception MessagingException
     */
    public NNTPReply sendAuthCommand(String data) throws MessagingException {
        sendLine(data);
        return getReply();
    }

    /**
     * Sends a message down the socket and terminates with the appropriate CRLF
     */
    public void sendLine(String data) throws MessagingException {
        if (socket == null || !socket.isConnected()) {
            throw new MessagingException("no connection");
        }
        try {
            outputStream.write(data.getBytes());
            outputStream.write(CR);
            outputStream.write(LF);
            outputStream.flush();
        } catch (IOException e) {
            throw new MessagingException(e.toString());
        }
    }

    /**
     * Get a reply line for an NNTP command.
     * 
     * @return An NNTP reply object from the stream.
     */
    public NNTPReply getReply() throws MessagingException {
        lastServerResponse = new NNTPReply(receiveLine());
        return lastServerResponse;
    }

    /**
     * Retrieve the last response received from the NNTP server.
     * 
     * @return The raw response string (including the error code) returned from
     *         the NNTP server.
     */
    public String getLastServerResponse() {
        if (lastServerResponse == null) {
            return "";
        }
        return lastServerResponse.getReply();
    }

    /**
     * Receives one line from the server. A line is a sequence of bytes
     * terminated by a CRLF
     * 
     * @return the line from the server as String
     */
    public String receiveLine() throws MessagingException {
        if (socket == null || !socket.isConnected()) {
            throw new MessagingException("no connection");
        }

        try {
            String line = reader.readLine();
            if (line == null) {
                throw new MessagingException("Unexpected end of stream");
            }
            return line;
        } catch (IOException e) {
            throw new MessagingException("Error reading from server", e);
        }
    }

    
    /**
     * Authenticate with the server, if necessary (or possible).
     */
    protected void processAuthentication(int request) throws MessagingException {
        // we need to authenticate, but we don't have userid/password
        // information...fail this
        // immediately.
        if (username == null || password == null) {
            throw new MessagingException("Server requires user authentication");
        }

        if (request == NNTPReply.AUTHINFO_SIMPLE_REQUIRED) {
            processAuthinfoSimple();
        } else {
            if (!processSaslAuthentication()) {
                processAuthinfoUser();
            }
        }
    }

    /**
     * Process an AUTHINFO SIMPLE command. Not widely used, but if the server
     * asks for it, we can respond.
     * 
     * @exception MessagingException
     */
    protected void processAuthinfoSimple() throws MessagingException {
        NNTPReply reply = sendAuthCommand("AUTHINFO SIMPLE");
        if (reply.getCode() != NNTPReply.AUTHINFO_CONTINUE) {
            throw new MessagingException("Error authenticating with server using AUTHINFO SIMPLE");
        }
        reply = sendAuthCommand(username + " " + password);
        if (reply.getCode() != NNTPReply.AUTHINFO_ACCEPTED) {
            throw new MessagingException("Error authenticating with server using AUTHINFO SIMPLE");
        }
    }

    
    /**
     * Process SASL-type authentication.
     * 
     * @return Returns true if the server support a SASL authentication mechanism and
     *         accepted reponse challenges.
     * @exception MessagingException
     */
    protected boolean processSaslAuthentication() throws MessagingException {
        // only do this if permitted 
        if (!authInfoSaslAllowed) {
            return false; 
        }
        // if unable to get an appropriate authenticator, just fail it. 
        ClientAuthenticator authenticator = getSaslAuthenticator(); 
        if (authenticator == null) {
            throw new MessagingException("Unable to obtain SASL authenticator"); 
        }
        
        // go process the login.
        return processLogin(authenticator);
    }
    
    /**
     * Attempt to retrieve a SASL authenticator for this 
     * protocol. 
     * 
     * @return A SASL authenticator, or null if a suitable one 
     *         was not located.
     */
    protected ClientAuthenticator getSaslAuthenticator() {
        return AuthenticatorFactory.getAuthenticator(props, selectSaslMechanisms(), serverHost, username, password, authid, realm); 
    }


    /**
     * Process a login using the provided authenticator object.
     * 
     * NB:  This method is synchronized because we have a multi-step process going on 
     * here.  No other commands should be sent to the server until we complete. 
     *
     * @return Returns true if the server support a SASL authentication mechanism and
     * accepted reponse challenges.
     * @exception MessagingException
     */
    protected synchronized boolean processLogin(ClientAuthenticator authenticator) throws MessagingException {
        debugOut("Authenticating for user: " + username + " using " + authenticator.getMechanismName());

        // if the authenticator has some initial data, we compose a command
        // containing the initial data.
        if (authenticator.hasInitialResponse()) {
            StringBuffer command = new StringBuffer();
            // the auth command initiates the handshaking.
            command.append("AUTHINFO SASL ");
            // and tell the server which mechanism we're using.
            command.append(authenticator.getMechanismName());
            command.append(" ");
            // and append the response data
            command.append(new String(Base64.encode(authenticator.evaluateChallenge(null))));
            // send the command now
            sendLine(command.toString());
        }
        // we just send an auth command with the command type.
        else {
            StringBuffer command = new StringBuffer();
            // the auth command initiates the handshaking.
            command.append("AUTHINFO SASL");
            // and tell the server which mechanism we're using.
            command.append(authenticator.getMechanismName());
            // send the command now
            sendLine(command.toString());
        }

        // now process the challenge sequence. We get a 235 response back when
        // the server accepts the
        // authentication, and a 334 indicates we have an additional challenge.
        while (true) {
            // get the next line, and if it is an error response, return now.
            NNTPReply line = getReply();

            // if we get a completion return, we've passed muster, so give an
            // authentication response.
            if (line.getCode() == NNTPReply.AUTHINFO_ACCEPTED || line.getCode() == NNTPReply.AUTHINFO_ACCEPTED_FINAL) {
                debugOut("Successful SMTP authentication");
                return true;
            }
            // we have an additional challenge to process.
            else if (line.getCode() == NNTPReply.AUTHINFO_CHALLENGE) {
                // Does the authenticator think it is finished? We can't answer
                // an additional challenge,
                // so fail this.
                if (authenticator.isComplete()) {
                    debugOut("Extra authentication challenge " + line);
                    return false;
                }

                // we're passed back a challenge value, Base64 encoded.
                byte[] challenge = Base64.decode(line.getMessage().getBytes());

                // have the authenticator evaluate and send back the encoded
                // response.
                sendLine(new String(Base64.encode(authenticator.evaluateChallenge(challenge))));
            }
            // completion or challenge are the only responses we know how to
            // handle. Anything else must
            // be a failure.
            else {
                debugOut("Authentication failure " + line);
                return false;
            }
        }
    }

    
    /**
     * Process an AUTHINFO USER command. Most common form of NNTP
     * authentication.
     * 
     * @exception MessagingException
     */
    protected void processAuthinfoUser() throws MessagingException {
        // only do this if allowed by the server 
        if (!authInfoUserAllowed) {
            return; 
        }
        NNTPReply reply = sendAuthCommand("AUTHINFO USER " + username);
        // accepted without a password (uncommon, but allowed), we're done
        if (reply.getCode() == NNTPReply.AUTHINFO_ACCEPTED) {
            return;
        }
        // the only other non-error response is continue.
        if (reply.getCode() != NNTPReply.AUTHINFO_CONTINUE) {
            throw new MessagingException("Error authenticating with server using AUTHINFO USER: " + reply);
        }
        // now send the password. We expect an accepted response.
        reply = sendAuthCommand("AUTHINFO PASS " + password);
        if (reply.getCode() != NNTPReply.AUTHINFO_ACCEPTED) {
            throw new MessagingException("Error authenticating with server using AUTHINFO SIMPLE");
        }
    }

    
    /**
     * Indicate whether posting is allowed for a given server.
     * 
     * @return True if the server allows posting, false if the server is
     *         read-only.
     */
    public boolean isPostingAllowed() {
        return postingAllowed;
    }

    /**
     * Retrieve the welcome string sent back from the server.
     * 
     * @return The server provided welcome string.
     */
    public String getWelcomeString() {
        return welcomeString;
    }
}
