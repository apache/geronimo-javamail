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

package org.apache.geronimo.javamail.store.pop3.connection;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;

import org.apache.geronimo.javamail.authentication.AuthenticatorFactory;
import org.apache.geronimo.javamail.authentication.ClientAuthenticator;
import org.apache.geronimo.javamail.store.pop3.POP3Constants;
import org.apache.geronimo.javamail.util.CommandFailedException;
import org.apache.geronimo.javamail.util.InvalidCommandException;
import org.apache.geronimo.javamail.util.MIMEInputReader;
import org.apache.geronimo.javamail.util.MailConnection;
import org.apache.geronimo.javamail.util.ProtocolProperties;
import org.apache.geronimo.mail.util.Base64;
import org.apache.geronimo.mail.util.Hex;

/**
 * Simple implementation of POP3 transport.
 *
 * @version $Rev$ $Date$
 */
public class POP3Connection extends MailConnection implements POP3Constants {

    static final protected String MAIL_APOP_ENABLED = "apop.enable";
    static final protected String MAIL_AUTH_ENABLED = "auth.enable";
    static final protected String MAIL_RESET_QUIT = "rsetbeforequit";
    static final protected String MAIL_DISABLE_TOP = "disabletop";
    static final protected String MAIL_FORGET_TOP = "forgettopheaders";

    // the initial greeting string, which might be required for APOP authentication.
    protected String greeting;
    // is use of the AUTH command enabled
    protected boolean authEnabled;
    // is use of APOP command enabled
    protected boolean apopEnabled;
    // input reader wrapped around the socket input stream
    protected BufferedReader reader;
    // output writer wrapped around the socket output stream.
    protected PrintWriter writer;
    // this connection was closed unexpectedly
    protected boolean closed;
    // indicates whether this conneciton is currently logged in.  Once
    // we send a QUIT, we're finished.
    protected boolean loggedIn;
    // indicates whether we need to avoid using the TOP command
    // when retrieving headers
    protected boolean topDisabled = false;

    /**
     * Normal constructor for an POP3Connection() object.
     *
     * @param store    The store we're associated with (source of parameter values).
     * @param host     The target host name of the IMAP server.
     * @param port     The target listening port of the server.  Defaults to 119 if
     *                 the port is specified as -1.
     * @param username The login user name (can be null unless authentication is
     *                 required).
     * @param password Password associated with the userid account.  Can be null if
     *                 authentication is not required.
     * @param sslConnection
     *                 True if this is targetted as an SSLConnection.
     * @param debug    The session debug flag.
     */
    public POP3Connection(ProtocolProperties props) {
        super(props);

        // get our login properties flags
        authEnabled = props.getBooleanProperty(MAIL_AUTH_ENABLED, false);
        apopEnabled = props.getBooleanProperty(MAIL_APOP_ENABLED, false);
        topDisabled = props.getBooleanProperty(MAIL_DISABLE_TOP, false);
    }


    /**
     * Connect to the server and do the initial handshaking.
     *
     * @exception MessagingException
     */
    public boolean protocolConnect(String host, int port, String authid, String realm, String username, String password) throws MessagingException {
        this.serverHost = host;
        this.serverPort = port;
        this.realm = realm;
        this.authid = authid;
        this.username = username;
        this.password = password;

        try {
            // create socket and connect to server.
            getConnection();
            // consume the welcome line
            getWelcome();

            // go login with the server
            if (login())
            {
                loggedIn = true;
                return true;
            }
            return false;
        } catch (IOException e) {
            if (debug) {
                debugOut("I/O exception establishing connection", e);
            }
            throw new MessagingException("Connection error", e);
        }
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
            throw new MessagingException("Unable to obtain a connection to the POP3 server", e);
        }

        // The POp3 protocol is inherently a string-based protocol, so we get
        // string readers/writers for the connection streams.  Note that we explicitly
        // set the encoding to ensure that an inappropriate native encoding is not picked up.
        Charset iso88591 = Charset.forName("ISO8859-1");
        reader = new BufferedReader(new InputStreamReader(inputStream, iso88591));
        writer = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(outputStream), iso88591));
    }

    protected void getWelcome() throws IOException {
        // just read the line and consume it.  If debug is
        // enabled, there I/O stream will be traced
        greeting = reader.readLine();
    }

    public String toString() {
        return "POP3Connection host: " + serverHost + " port: " + serverPort;
    }


    /**
     * Close the connection.  On completion, we'll be disconnected from
     * the server and unable to send more data.
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
            logout();
        } finally {
            // and close up the connection.  We do this in a finally block to make sure the connection
            // is shut down even if quit gets an error.
            closeServerConnection();
            // get rid of our response processor too.
            reader = null;
            writer = null;
        }
    }


    /**
     * Tag this connection as having been closed by the
     * server.  This will not be returned to the
     * connection pool.
     */
    public void setClosed() {
        closed = true;
    }

    /**
     * Test if the connnection has been forcibly closed.
     *
     * @return True if the server disconnected the connection.
     */
    public boolean isClosed() {
        return closed;
    }

    protected POP3Response sendCommand(String cmd) throws MessagingException {
        return sendCommand(cmd, false);
    }

    protected POP3Response sendMultiLineCommand(String cmd) throws MessagingException {
        return sendCommand(cmd, true);
    }

    protected synchronized POP3Response sendCommand(String cmd, boolean multiLine) throws MessagingException {
        if (socket.isConnected()) {
            {
                // NOTE:  We don't use println() because it uses the platform concept of a newline rather
                // than using CRLF, which is required by the POP3 protocol.
                writer.write(cmd);
                writer.write("\r\n");
                writer.flush();

                POP3Response response = buildResponse(multiLine);
                if (response.isError()) {
                    throw new CommandFailedException("Error issuing POP3 command: " + cmd);
                }
                return response;
            }
        }
        throw new MessagingException("Connection to Mail Server is lost, connection " + this.toString());
    }

    /**
     * Build a POP3Response item from the response stream.
     *
     * @param isMultiLineResponse
     *               If true, this command is expecting multiple lines back from the server.
     *
     * @return A POP3Response item with all of the command response data.
     * @exception MessagingException
     */
    protected POP3Response buildResponse(boolean isMultiLineResponse) throws MessagingException {
        int status = ERR;
        byte[] data = null;

        String line;
        MIMEInputReader source = new MIMEInputReader(reader);

        try {
            line = reader.readLine();
        } catch (IOException e) {
            throw new MessagingException("Error in receving response");
        }

        if (line == null || line.trim().equals("")) {
            throw new MessagingException("Empty Response");
        }

        if (line.startsWith("+OK")) {
            status = OK;
            line = removeStatusField(line);
            if (isMultiLineResponse) {
                data = getMultiLineResponse();
            }
        } else if (line.startsWith("-ERR")) {
            status = ERR;
            line = removeStatusField(line);
        }else if (line.startsWith("+")) {
        	status = CHALLENGE;
        	line = removeStatusField(line);
        	if (isMultiLineResponse) {
        		data = getMultiLineResponse();
        	}
        } else {
            throw new MessagingException("Unexpected response: " + line);
        }
        return new POP3Response(status, line, data);
    }

    private static String removeStatusField(String line) {
        return line.substring(line.indexOf(SPACE) + 1);
    }

    /**
     * This could be a multiline response
     */
    private byte[] getMultiLineResponse() throws MessagingException {

        MIMEInputReader source = new MIMEInputReader(reader);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // it's more efficient to do this a buffer at a time.
        // the MIMEInputReader takes care of the byte-stuffing and
        // ".\r\n" input terminator for us.
        OutputStreamWriter outWriter = new OutputStreamWriter(out);
        char buffer[] = new char[500];
        try {
            int charsRead = -1;
            while ((charsRead = source.read(buffer)) >= 0) {
                outWriter.write(buffer, 0, charsRead);
            }
            outWriter.flush();
        } catch (IOException e) {
            throw new MessagingException("Error processing a multi-line response", e);
        }

        return out.toByteArray();
    }


    /**
     * Retrieve the raw message content from the POP3
     * server.  This is all of the message data, including
     * the header.
     *
     * @param sequenceNumber
     *               The message sequence number.
     *
     * @return A byte array containing all of the message data.
     * @exception MessagingException
     */
    public byte[] retrieveMessageData(int sequenceNumber) throws MessagingException {
        POP3Response msgResponse = sendMultiLineCommand("RETR " + sequenceNumber);
        // we want the data directly in this case.
        return msgResponse.getData();
    }

    /**
     * Retrieve the message header information for a given
     * message, returned as an input stream suitable
     * for loading the message data.
     *
     * @param sequenceNumber
     *               The server sequence number for the message.
     *
     * @return An inputstream that can be used to read the message
     *         data.
     * @exception MessagingException
     */
    public ByteArrayInputStream retrieveMessageHeaders(int sequenceNumber) throws MessagingException {
        POP3Response msgResponse;

        // some POP3 servers don't correctly implement TOP, so this can be disabled.  If
        // we can't use TOP, then use RETR and retrieve everything.  We can just hand back
        // the stream, as the header loading routine will stop at the first
        // null line.
        if (topDisabled) {
            msgResponse = sendMultiLineCommand("RETR " + sequenceNumber);
        }
        else {
            msgResponse = sendMultiLineCommand("TOP " + sequenceNumber + " 0");
        }

        // just load the returned message data as a set of headers
        return msgResponse.getContentStream();
    }

    /**
     * Retrieve the total message size from the mail
     * server.  This is the size of the headers plus
     * the size of the message content.
     *
     * @param sequenceNumber
     *               The message sequence number.
     *
     * @return The full size of the message.
     * @exception MessagingException
     */
    public int retrieveMessageSize(int sequenceNumber) throws MessagingException {
        POP3Response msgResponse = sendCommand("LIST " + sequenceNumber);
        // Convert this into the parsed response type we need.
        POP3ListResponse list = new POP3ListResponse(msgResponse);
        // this returns the total message size
        return list.getSize();
    }

    /**
     * Retrieve the mail drop status information.
     *
     * @return An object representing the returned mail drop status.
     * @exception MessagingException
     */
    public POP3StatusResponse retrieveMailboxStatus() throws MessagingException {
        // issue the STAT command and return this into a status response
        return new POP3StatusResponse(sendCommand("STAT"));
    }


    /**
     * Retrieve the UID for an individual message.
     *
     * @param sequenceNumber
     *               The target message sequence number.
     *
     * @return The string UID maintained by the server.
     * @exception MessagingException
     */
    public String retrieveMessageUid(int sequenceNumber) throws MessagingException {
        POP3Response msgResponse = sendCommand("UIDL " + sequenceNumber);

        String message = msgResponse.getFirstLine();
        // the UID is everything after the blank separating the message number and the UID.
        // there's not supposed to be anything else on the message, but trim it of whitespace
        // just to be on the safe side.
        return message.substring(message.indexOf(' ') + 1).trim();
    }


    /**
     * Delete a single message from the mail server.
     *
     * @param sequenceNumber
     *               The sequence number of the message to delete.
     *
     * @exception MessagingException
     */
    public void deleteMessage(int sequenceNumber) throws MessagingException {
        // just issue the command...we ignore the command response
        sendCommand("DELE " + sequenceNumber);
    }

    /**
     * Logout from the mail server.  This sends a QUIT
     * command, which will likely sever the mail connection.
     *
     * @exception MessagingException
     */
    public void logout() throws MessagingException {
        // we may have already sent the QUIT command
        if (!loggedIn) {
            return;
        }
        // just issue the command...we ignore the command response
        sendCommand("QUIT");
        loggedIn = false;
    }

    /**
     * Perform a reset on the mail server.
     *
     * @exception MessagingException
     */
    public void reset() throws MessagingException {
        // some mail servers mark retrieved messages for deletion
        // automatically.  This will reset the read flags before
        // we go through normal cleanup.
        if (props.getBooleanProperty(MAIL_RESET_QUIT, false)) {
            // just send an RSET command first
            sendCommand("RSET");
        }
    }

    /**
     * Ping the mail server to see if we still have an active connection.
     *
     * @exception MessagingException thrown if we do not have an active connection.
     */
    public void pingServer() throws MessagingException {
        // just issue the command...we ignore the command response
        sendCommand("NOOP");
    }

    /**
     * Login to the mail server, using whichever method is
     * configured.  This will try multiple methods, if allowed,
     * in decreasing levels of security.
     *
     * @return true if the login was successful.
     * @exception MessagingException
     */
    public synchronized boolean login() throws MessagingException {
        // permitted to use the AUTH command?
        if (authEnabled) {
            try {
                // go do the SASL thing
                return processSaslAuthentication();
            } catch (MessagingException e) {
                // Any error here means fall back to the next mechanism
            }
        }

        if (apopEnabled) {
            try {
                // go do the SASL thing
                return processAPOPAuthentication();
            } catch (MessagingException e) {
                // Any error here means fall back to the next mechanism
            }
        }

        try {
            // do the tried and true login processing.
            return processLogin();
        } catch (MessagingException e) {
        }
        // everything failed...can't get in
        return false;
    }


    /**
     * Process a basic LOGIN operation, using the
     * plain test USER/PASS command combo.
     *
     * @return true if we logged successfully.
     * @exception MessagingException
     */
    public boolean processLogin() throws MessagingException {
        // start by sending the USER command, followed by
        // the PASS command
        sendCommand("USER " + username);
        sendCommand("PASS " + password);
        return true;       // we're in
    }

    /**
     * Process logging in using the APOP command.  Only
     * works on servers that give a timestamp value
     * in the welcome response.
     *
     * @return true if the login was accepted.
     * @exception MessagingException
     */
    public boolean processAPOPAuthentication() throws MessagingException {
        int timeStart = greeting.indexOf('<');
        // if we didn't get an APOP challenge on the greeting, throw an exception
        // the main login processor will swallow that and fall back to the next
        // mechanism
        if (timeStart == -1) {
            throw new MessagingException("POP3 Server does not support APOP");
        }
        int timeEnd = greeting.indexOf('>');
        String timeStamp = greeting.substring(timeStart, timeEnd + 1);

        // we create the digest password using the timestamp value sent to use
        // concatenated with the password.
        String digestPassword = timeStamp + password;

        byte[] digest;

        try {
            // create a digest value from the password.
            MessageDigest md = MessageDigest.getInstance("MD5");
            digest = md.digest(digestPassword.getBytes("iso-8859-1"));
        } catch (NoSuchAlgorithmException e) {
            // this shouldn't happen, but if it does, we'll just try a plain
            // login.
            throw new MessagingException("Unable to create MD5 digest", e);
        } catch (UnsupportedEncodingException e) {
            // this shouldn't happen, but if it does, we'll just try a plain
            // login.
            throw new MessagingException("Unable to create MD5 digest", e);
        }
        // this will throw an exception if it gives an error failure
        sendCommand("APOP " + username + " " + Hex.encode(digest));
        // no exception, we must have passed
        return true;
    }


    /**
     * Process SASL-type authentication.
     *
     * @return Returns true if the server support a SASL authentication mechanism and
     *         accepted reponse challenges.
     * @exception MessagingException
     */
    protected boolean processSaslAuthentication() throws MessagingException {
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
        if (debug) {
            debugOut("Authenticating for user: " + username + " using " + authenticator.getMechanismName());
        }

        POP3Response response = sendCommand("AUTH " + authenticator.getMechanismName());

        // now process the challenge sequence.  We get a continuation response back for each stage of the
        // authentication, and finally an OK when everything passes muster.
        while (true) {
            // this should be a continuation reply, if things are still good.
            if (response.isChallenge()) {
                // we're passed back a challenge value, Base64 encoded.
                byte[] challenge = response.decodeChallengeResponse();

                try {
                    String responseString = new String(Base64.encode(authenticator.evaluateChallenge(challenge)), "US_ASCII");

                    // have the authenticator evaluate and send back the encoded response.
                    response = sendCommand(responseString);
                } catch (UnsupportedEncodingException ex) {
                }
            }
            else {
                // there are only two choices here, OK or a continuation.  OK means
                // we've passed muster and are in.
                return true;
            }
        }
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
        // just return the set that have been explicity permitted
        return getSaslMechanisms();
    }
}

