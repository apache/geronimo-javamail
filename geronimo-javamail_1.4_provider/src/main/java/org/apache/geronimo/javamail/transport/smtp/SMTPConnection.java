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

package org.apache.geronimo.javamail.transport.smtp;

import java.io.BufferedReader;
import java.io.BufferedOutputStream; 
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException; 
import java.util.ArrayList; 
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage; 
import javax.mail.internet.MimeMultipart; 
import javax.mail.internet.MimePart;
import javax.mail.Session;

import org.apache.geronimo.javamail.authentication.ClientAuthenticator; 
import org.apache.geronimo.javamail.authentication.AuthenticatorFactory; 
import org.apache.geronimo.javamail.util.CountingOutputStream;
import org.apache.geronimo.javamail.util.MailConnection; 
import org.apache.geronimo.javamail.util.MIMEOutputStream;
import org.apache.geronimo.javamail.util.ProtocolProperties; 
import org.apache.geronimo.mail.util.Base64;
import org.apache.geronimo.mail.util.XText;

/**
 * Simple implementation of SMTP transport. Just does plain RFC977-ish delivery.
 * 
 * @version $Rev$ $Date$
 */
public class SMTPConnection extends MailConnection {
    protected static final String MAIL_SMTP_QUITWAIT = "quitwait";
    protected static final String MAIL_SMTP_EXTENSION = "mailextension";
    protected static final String MAIL_SMTP_EHLO = "ehlo";
    protected static final String MAIL_SMTP_ALLOW8BITMIME = "allow8bitmime";
    protected static final String MAIL_SMTP_REPORT_SUCCESS = "reportsuccess";
    protected static final String MAIL_SMTP_STARTTLS_ENABLE = "starttls.enable";
    protected static final String MAIL_SMTP_AUTH = "auth";
    protected static final String MAIL_SMTP_FROM = "from";
    protected static final String MAIL_SMTP_DSN_RET = "dsn.ret";
    protected static final String MAIL_SMTP_SUBMITTER = "submitter";

    /**
     * property keys for protocol properties.
     */
    protected static final int DEFAULT_NNTP_PORT = 119;
    
    // the last response line received from the server.
    protected SMTPReply lastServerResponse = null;
    
    // input reader wrapped around the socket input stream 
    protected BufferedReader reader; 
    // output writer wrapped around the socket output stream. 
    protected PrintWriter writer; 
    
    // do we report success after completion of each mail send.
    protected boolean reportSuccess;
    // does the server support transport level security?
    protected boolean serverTLS = false;
    // is TLS enabled on our part?
    protected boolean useTLS = false;
    // should we use 8BITMIME encoding if supported by the server?
    protected boolean use8bit = false; 

    /**
     * Normal constructor for an SMTPConnection() object.
     * 
     * @param props  The property bundle for this protocol instance.
     */
    public SMTPConnection(ProtocolProperties props) {
        super(props);
        
        // check to see if we need to throw an exception after a send operation.
        reportSuccess = props.getBooleanProperty(MAIL_SMTP_REPORT_SUCCESS, false);
        // and also check for TLS enablement.
        useTLS = props.getBooleanProperty(MAIL_SMTP_STARTTLS_ENABLE, false);
        // and also check for 8bitmime support  
        use8bit = props.getBooleanProperty(MAIL_SMTP_ALLOW8BITMIME, false);
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

        // now check to see if we need to authenticate. If we need this, then
        // we must have a username and
        // password specified. Failing this may result in a user prompt to
        // collect the information.
        boolean mustAuthenticate = props.getBooleanProperty(MAIL_SMTP_AUTH, false);

        // if we need to authenticate, and we don't have both a userid and
        // password, then we fail this
        // immediately. The Service.connect() method will try to obtain the user
        // information and retry the
        // connection one time.
        if (mustAuthenticate && (username == null || password == null)) {
            debugOut("Failing connection for missing authentication information");
            return false;
        }
        
        super.protocolConnect(host, port, username, password); 
        
        try {
            // create socket and connect to server.
            getConnection();

            // receive welcoming message
            if (!getWelcome()) {
                debugOut("Error getting welcome message"); 
                throw new MessagingException("Error in getting welcome msg");
            }

            // say hello
            if (!sendHandshake()) {
                debugOut("Error getting processing handshake message"); 
                throw new MessagingException("Error in saying EHLO to server");
            }

            // authenticate with the server, if necessary
            if (!processAuthentication()) {
                debugOut("User authentication failure");
                throw new AuthenticationFailedException("Error authenticating with server");
            }
        } catch (IOException e) {
            debugOut("I/O exception establishing connection", e);
            throw new MessagingException("Connection error", e);
        }
        debugOut("Successful connection"); 
        return true;
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
        }
    }

    public String toString() {
        return "SMTPConnection host: " + serverHost + " port: " + serverPort;
    }

    
    /**
     * Set the sender for this mail.
     * 
     * @param message
     *                   The message we're sending.
     * 
     * @return True if the command was accepted, false otherwise. 
     * @exception MessagingException
     */
    protected boolean sendMailFrom(Message message) throws MessagingException {

        // need to sort the from value out from a variety of sources.
        String from = null;

        // first potential source is from the message itself, if it's an
        // instance of SMTPMessage.
        if (message instanceof SMTPMessage) {
            from = ((SMTPMessage) message).getEnvelopeFrom();
        }

        // if not available from the message, check the protocol property next
        if (from == null || from.length() == 0) {
            // the from value can be set explicitly as a property
            from = props.getProperty(MAIL_SMTP_FROM);
        }

        // if not there, see if we have something in the message header.
        if (from == null || from.length() == 0) {
            Address[] fromAddresses = message.getFrom();

            // if we have some addresses in the header, then take the first one
            // as our From: address
            if (fromAddresses != null && fromAddresses.length > 0) {
                from = ((InternetAddress) fromAddresses[0]).getAddress();
            }
            // get what the InternetAddress class believes to be the local
            // address.
            else {
                InternetAddress local = InternetAddress.getLocalAddress(session);
                if (local != null) {
                    from = local.getAddress(); 
                }
            }
        }

        if (from == null || from.length() == 0) {
            throw new MessagingException("no FROM address");
        }

        StringBuffer command = new StringBuffer();

        // start building up the command
        command.append("MAIL FROM: ");
        command.append(fixEmailAddress(from));
        
        // If the server supports the 8BITMIME extension, we might need to change the 
        // transfer encoding for the content to allow for direct transmission of the 
        // 8-bit codes. 
        if (supportsExtension("8BITMIME")) {
            // we only do this if the capability was enabled via a property option or 
            // by explicitly setting the property on the message object. 
            if (use8bit || (message instanceof SMTPMessage && ((SMTPMessage)message).getAllow8bitMIME())) {
                // make sure we add the BODY= option to the FROM message. 
                command.append(" BODY=8BITMIME"); 
                
                // go check the content and see if the can convert the transfer encoding to 
                // allow direct 8-bit transmission. 
                if (convertTransferEncoding((MimeMessage)message)) {
                    // if we changed the encoding on any of the parts, then we 
                    // need to save the message again 
                    message.saveChanges(); 
                }
            }
        }
        
        // some servers ask for a size estimate on the initial send 
        if (supportsExtension("SIZE")) {
            int estimate = getSizeEstimate(message); 
            if (estimate > 0) {
                command.append(" SIZE=" + estimate); 
            }
        }

        // does this server support Delivery Status Notification? Then we may
        // need to add some extra to the command.
        if (supportsExtension("DSN")) {
            String returnNotification = null;

            // the return notification stuff might be set as value on the
            // message object itself.
            if (message instanceof SMTPMessage) {
                // we need to convert the option into a string value.
                switch (((SMTPMessage) message).getReturnOption()) {
                case SMTPMessage.RETURN_FULL:
                    returnNotification = "FULL";
                    break;

                case SMTPMessage.RETURN_HDRS:
                    returnNotification = "HDRS";
                    break;
                }
            }

            // if not obtained from the message object, it can also be set as a
            // property.
            if (returnNotification == null) {
                // the DSN value is set by yet another property.
                returnNotification = props.getProperty(MAIL_SMTP_DSN_RET);
            }

            // if we have a target, add the notification stuff to our FROM
            // command.
            if (returnNotification != null) {
                command.append(" RET=");
                command.append(returnNotification);
            }
        }

        // if this server supports AUTH and we have submitter information, then
        // we also add the
        // "AUTH=" keyword to the MAIL FROM command (see RFC 2554).

        if (supportsExtension("AUTH")) {
            String submitter = null;

            // another option that can be specified on the message object.
            if (message instanceof SMTPMessage) {
                submitter = ((SMTPMessage) message).getSubmitter();
            }
            // if not part of the object, try for a propery version.
            if (submitter == null) {
                // we only send the extra keyword is a submitter is specified.
                submitter = props.getProperty(MAIL_SMTP_SUBMITTER);
            }
            // we have one...add the keyword, plus the submitter info in xtext
            // format (defined by RFC 1891).
            if (submitter != null) {
                command.append(" AUTH=");
                try {
                    // add this encoded
                    command.append(new String(XText.encode(submitter.getBytes("US-ASCII"))));
                } catch (UnsupportedEncodingException e) {
                    throw new MessagingException("Invalid submitter value " + submitter);
                }
            }
        }

        String extension = null;

        // now see if we need to add any additional extension info to this
        // command. The extension is not
        // checked for validity. That's the reponsibility of the caller.
        if (message instanceof SMTPMessage) {
            extension = ((SMTPMessage) message).getMailExtension();
        }
        // this can come either from the object or from a set property.
        if (extension == null) {
            extension = props.getProperty(MAIL_SMTP_EXTENSION);
        }

        // have something real to add?
        if (extension != null && extension.length() != 0) {
            // tack this on the end with a blank delimiter.
            command.append(' ');
            command.append(extension);
        }

        // and finally send the command
        SMTPReply line = sendCommand(command.toString());

        // 250 response indicates success.
        return line.getCode() == SMTPReply.COMMAND_ACCEPTED;
    }
    
    
    /**
     * Check to see if a MIME body part can have its 
     * encoding changed from quoted-printable or base64
     * encoding to 8bit encoding.  In order for this 
     * to work, it must follow the rules laid out in 
     * RFC 2045.  To qualify for conversion, the text 
     * must be: 
     * 
     * 1)  No more than 998 bytes long 
     * 2)  All lines are terminated with CRLF sequences
     * 3)  CR and LF characters only occur in properly 
     * formed line separators 
     * 4)  No null characters are allowed. 
     * 
     * The conversion will only be applied to text 
     * elements, and this will recurse through the 
     * different elements of MultiPart content. 
     * 
     * @param bodyPart The bodyPart to convert. Initially, this will be
     *                 the message itself.
     * 
     * @return true if any conversion was performed, false if 
     *         nothing was converted.
     */
    protected boolean convertTransferEncoding(MimePart bodyPart)
    {
        boolean converted = false; 
        try {
            // if this is a multipart element, apply the conversion rules 
            // to each of the parts. 
            if (bodyPart.isMimeType("multipart/")) {
                MimeMultipart parts = (MimeMultipart)bodyPart.getContent(); 
                for (int i = 0; i < parts.getCount(); i++) {
                    // convert each body part, and accumulate the conversion result 
                    converted = converted && convertTransferEncoding((MimePart)parts.getBodyPart(i)); 
                }
            }
            else {
                // we only do this if the encoding is quoted-printable or base64
                String encoding =  bodyPart.getEncoding(); 
                if (encoding != null) {
                    encoding = encoding.toLowerCase(); 
                    if (encoding.equals("quoted-printable") || encoding.equals("base64")) {
                        // this requires encoding.  Read the actual content to see if 
                        // it conforms to the 8bit encoding rules. 
                        if (isValid8bit(bodyPart.getInputStream())) {
                            // There's a huge hidden gotcha lurking under the covers here. 
                            // If the content just exists as an encoded byte array, then just 
                            // switching the transfer encoding will mess things up because the 
                            // already encoded data gets transmitted in encoded form, but with 
                            // and 8bit encoding style.  As a result, it doesn't get unencoded on 
                            // the receiving end.  This is a nasty problem to debug.  
                            //
                            // The solution is to get the content as it's object type, set it back 
                            // on the the message in raw form.  Requesting the content will apply the 
                            // current transfer encoding value to the data.  Once we have set the 
                            // content value back, we can reset the transfer encoding. 
                            bodyPart.setContent(bodyPart.getContent(), bodyPart.getContentType()); 
                            
                            // it's valid, so change the transfer encoding to just 
                            // pass the data through.  
                            bodyPart.setHeader("Content-Transfer-Encoding", "8bit"); 
                            converted = true;   // we've changed something
                        }
                    }
                }
            }
        } catch (MessagingException e) {
        } catch (IOException e) {
        }
        return converted; 
    }

    
    /**
     * Get the server's welcome blob from the wire....
     */
    protected boolean getWelcome() throws MessagingException {
        SMTPReply line = getReply();
        // just return the error status...we don't care about any of the 
        // response information
        return !line.isError();
    }
    
    
    /**
     * Get an estimate of the transmission size for this 
     * message.  This size is the complete message as it is 
     * encoded and transmitted on the DATA command, not counting 
     * the terminating ".CRLF". 
     * 
     * @param msg    The message we're sending.
     * 
     * @return The count of bytes, if it can be calculated. 
     */
    protected int getSizeEstimate(Message msg) {
        // now the data... I could look at the type, but
        try {
            CountingOutputStream outputStream = new CountingOutputStream(); 
            
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

            // now to finish, we make sure there's a line break at the end.  
            mimeOut.forceTerminatingLineBreak();   
            // and flush the data to send it along 
            mimeOut.flush();   
            
            return outputStream.getCount();   
        } catch (IOException e) {
            return 0;     // can't get an estimate 
        } catch (MessagingException e) {
            return 0;     // can't get an estimate 
        }
    }
    

    /**
     * Sends the data in the message down the socket. This presumes the server
     * is in the right place and ready for getting the DATA message and the data
     * right place in the sequence
     */
    protected void sendData(Message msg) throws MessagingException {

        // send the DATA command
        SMTPReply line = sendCommand("DATA");

        if (line.isError()) {
            throw new MessagingException("Error issuing SMTP 'DATA' command: " + line);
        }

        // now the data... I could look at the type, but
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
            throw new MessagingException(e.toString());
        } catch (MessagingException e) {
            throw new MessagingException(e.toString());
        }

        // use a longer time out here to give the server time to process the
        // data.
        try {
            line = new SMTPReply(receiveLine(TIMEOUT * 2));
        } catch (MalformedSMTPReplyException e) {
            throw new MessagingException(e.toString());
        } catch (MessagingException e) {
            throw new MessagingException(e.toString());
        }

        if (line.isError()) {
            throw new MessagingException("Error issuing SMTP 'DATA' command: " + line);
        }
    }

    /**
     * Sends the QUIT message and receieves the response
     */
    protected void sendQuit() throws MessagingException {
        // there's yet another property that controls whether we should wait for
        // a reply for a QUIT command. If true, we're suppposed to wait for a response 
        // from the QUIT command.  Otherwise we just send the QUIT and bail.  The default 
        // is "false"
        if (props.getBooleanProperty(MAIL_SMTP_QUITWAIT, true)) {
            // handle as a real command...we're going to ignore the response.
            sendCommand("QUIT");
        } else {
            // just send the command without waiting for a response. 
            sendLine("QUIT");
        }
    }

    /**
     * Sets a receiver address for the current message
     *
     * @param addr
     *            The target address.
     * @param dsn
     *            An optional DSN option appended to the RCPT TO command.
     *
     * @return The status for this particular send operation.
     * @exception MessagingException
     */
    public SendStatus sendRcptTo(InternetAddress addr, String dsn) throws MessagingException {
        // compose the command using the fixed up email address. Normally, this
        // involves adding
        // "<" and ">" around the address.

        StringBuffer command = new StringBuffer();

        // compose the first part of the command
        command.append("RCPT TO: ");
        command.append(fixEmailAddress(addr.getAddress()));

        // if we have DSN information, append it to the command.
        if (dsn != null) {
            command.append(" NOTIFY=");
            command.append(dsn);
        }

        // get a string version of this command.
        String commandString = command.toString();

        SMTPReply line = sendCommand(commandString);

        switch (line.getCode()) {
        // these two are both successful transmissions
        case SMTPReply.COMMAND_ACCEPTED:
        case SMTPReply.ADDRESS_NOT_LOCAL:
            // we get out of here with the status information.
            return new SendStatus(SendStatus.SUCCESS, addr, commandString, line);

        // these are considered invalid address errors
        case SMTPReply.PARAMETER_SYNTAX_ERROR:
        case SMTPReply.INVALID_COMMAND_SEQUENCE:
        case SMTPReply.MAILBOX_NOT_FOUND:
        case SMTPReply.INVALID_MAILBOX:
        case SMTPReply.USER_NOT_LOCAL:
            // we get out of here with the status information.
            return new SendStatus(SendStatus.INVALID_ADDRESS, addr, commandString, line);

        // the command was valid, but something went wrong in the server.
        case SMTPReply.SERVICE_NOT_AVAILABLE:
        case SMTPReply.MAILBOX_BUSY:
        case SMTPReply.PROCESSING_ERROR:
        case SMTPReply.INSUFFICIENT_STORAGE:
        case SMTPReply.MAILBOX_FULL:
            // we get out of here with the status information.
            return new SendStatus(SendStatus.SEND_FAILURE, addr, commandString, line);

        // everything else is considered really bad...
        default:
            // we get out of here with the status information.
            return new SendStatus(SendStatus.GENERAL_ERROR, addr, commandString, line);
        }
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
    protected SMTPReply sendCommand(String data) throws MessagingException {
        sendLine(data);
        return getReply();
    }

    /**
     * Sends a message down the socket and terminates with the appropriate CRLF
     */
    protected void sendLine(String data) throws MessagingException {
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
     * Receives one line from the server. A line is a sequence of bytes
     * terminated by a CRLF
     *
     * @return the line from the server as String
     */
    protected String receiveLine() throws MessagingException {
        return receiveLine(TIMEOUT);
    }

    /**
     * Get a reply line for an SMTP command.
     *
     * @return An SMTP reply object from the stream.
     */
    protected SMTPReply getReply() throws MessagingException {
        try {
            lastServerResponse = new SMTPReply(receiveLine());
            // if the first line we receive is a continuation, continue 
            // reading lines until we reach the non-continued one. 
            while (lastServerResponse.isContinued()) {
                lastServerResponse.addLine(receiveLine()); 
            }
        } catch (MalformedSMTPReplyException e) {
            throw new MessagingException(e.toString());
        } catch (MessagingException e) {
            throw e;
        }
        return lastServerResponse;
    }

    /**
     * Retrieve the last response received from the SMTP server.
     *
     * @return The raw response string (including the error code) returned from
     *         the SMTP server.
     */
    public SMTPReply getLastServerResponse() {
        return lastServerResponse; 
    }
    

    /**
     * Receives one line from the server. A line is a sequence of bytes
     * terminated by a CRLF
     *
     * @return the line from the server as String
     */
    protected String receiveLine(int delayMillis) throws MessagingException {
        if (socket == null || !socket.isConnected()) {
            throw new MessagingException("no connection");
        }

        int timeout = 0;

        try {
            // for now, read byte for byte, looking for a CRLF
            timeout = socket.getSoTimeout();

            socket.setSoTimeout(delayMillis);

            StringBuffer buff = new StringBuffer();

            int c;
            boolean crFound = false, lfFound = false;

            while ((c = inputStream.read()) != -1 && crFound == false && lfFound == false) {
                // we're looking for a CRLF sequence, so mark each one as seen.
                // Any other
                // character gets appended to the end of the buffer.
                if (c == CR) {
                    crFound = true;
                } else if (c == LF) {
                    lfFound = true;
                } else {
                    buff.append((char) c);
                }
            }

            String line = buff.toString();
            return line;

        } catch (SocketException e) {
            throw new MessagingException(e.toString());
        } catch (IOException e) {
            throw new MessagingException(e.toString());
        } finally {
            try {
                socket.setSoTimeout(timeout);
            } catch (SocketException e) {
                // ignore - was just trying to do the decent thing...
            }
        }
    }

    /**
     * Convert an InternetAddress into a form sendable on an SMTP mail command.
     * InternetAddress.getAddress() generally returns just the address portion
     * of the full address, minus route address markers. We need to ensure we
     * have an address with '<' and '>' delimiters.
     *
     * @param mail
     *            The mail address returned from InternetAddress.getAddress().
     *
     * @return A string formatted for sending.
     */
    protected String fixEmailAddress(String mail) {
        if (mail.charAt(0) == '<') {
            return mail;
        }
        return "<" + mail + ">";
    }

    /**
     * Start the handshake process with the server, including setting up and
     * TLS-level work. At the completion of this task, we should be ready to
     * authenticate with the server, if needed.
     */
    protected boolean sendHandshake() throws MessagingException {
        // check to see what sort of initial handshake we need to make.
        boolean useEhlo = props.getBooleanProperty(MAIL_SMTP_EHLO, true);
        // if we're to use Ehlo, send it and then fall back to just a HELO
        // message if it fails.
        if (useEhlo) {
            if (!sendEhlo()) {
                sendHelo();
            }
        } else {
            // send the initial hello response.
            sendHelo();
        }

        if (useTLS) {
            // if we've been told to use TLS, and this server doesn't support
            // it, then this is a failure
            if (!serverTLS) {
                throw new MessagingException("Server doesn't support required transport level security");
            }
            // if the server supports TLS, then use it for the connection.
            // on our connection.
            getConnectedTLSSocket();

            // some servers (gmail is one that I know of) only send a STARTTLS
            // extension message on the
            // first EHLO command. Now that we have the TLS handshaking
            // established, we need to send a
            // second EHLO message to retrieve the AUTH records from the server.
            if (!sendEhlo()) {
                throw new MessagingException("Failure sending EHLO command to SMTP server");
            }
        }

        // this worked.
        return true;
    }


    /**
     * Switch the connection to using TLS level security, switching to an SSL
     * socket.
     */
    protected void getConnectedTLSSocket() throws MessagingException {
        debugOut("Attempting to negotiate STARTTLS with server " + serverHost);
        // tell the server of our intention to start a TLS session
        SMTPReply line = sendCommand("STARTTLS");

        if (line.getCode() != SMTPReply.SERVICE_READY) {
            debugOut("STARTTLS command rejected by SMTP server " + serverHost);
            throw new MessagingException("Unable to make TLS server connection");
        }
        
        debugOut("STARTTLS command accepted"); 
        
        // the base class handles the socket switch details 
        super.getConnectedTLSSocket(); 
    }

    
    /**
     * Send the EHLO command to the SMTP server.
     *
     * @return True if the command was accepted ok, false for any errors.
     * @exception SMTPTransportException
     * @exception MalformedSMTPReplyException
     * @exception MessagingException
     */
    protected boolean sendEhlo() throws MessagingException {
        sendLine("EHLO " + getLocalHost());

        SMTPReply reply = getReply();

        // we get a 250 code back. The first line is just a greeting, and
        // extensions are identifed on
        // continuations. If this fails, then we'll try once more with HELO to
        // establish bona fides.
        if (reply.getCode() != SMTPReply.COMMAND_ACCEPTED) {
            return false;
        }

        // create a fresh mapping and authentications table 
        capabilities = new HashMap();
        authentications = new ArrayList(); 

        List lines = reply.getLines(); 
        // process all of the continuation lines
        for (int i = 1; i < lines.size(); i++) {
            // go process the extention
            processExtension((String)lines.get(i));
        }
        return true;
    }

    /**
     * Send the HELO command to the SMTP server.
     *
     * @exception MessagingException
     */
    protected void sendHelo() throws MessagingException {
        // create a fresh mapping and authentications table 
        // these will be empty, but it will prevent NPEs 
        capabilities = new HashMap();
        authentications = new ArrayList(); 
        
        sendLine("HELO " + getLocalHost());

        SMTPReply line = getReply();

        // we get a 250 code back. The first line is just a greeting, and
        // extensions are identifed on
        // continuations. If this fails, then we'll try once more with HELO to
        // establish bona fides.
        if (line.getCode() != SMTPReply.COMMAND_ACCEPTED) {
            throw new MessagingException("Failure sending HELO command to SMTP server");
        }
    }

    /**
     * Return the current startTLS property.
     *
     * @return The current startTLS property.
     */
    public boolean getStartTLS() {
        return useTLS;
    }

    
    /**
     * Set a new value for the startTLS property.
     *
     * @param start
     *            The new setting.
     */
    public void setStartTLS(boolean start) {
        useTLS = start;
    }

    
    /**
     * Process an extension string passed back as the EHLP response.
     *
     * @param extension
     *            The string value of the extension (which will be of the form
     *            "NAME arguments").
     */
    protected void processExtension(String extension) {
        debugOut("Processing extension " + extension); 
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

        // process a few special ones that don't require extra parsing.
        // AUTH and AUTH=LOGIN are handled the same
        if (extensionName.equals("AUTH")) {
            // if we don't have an argument on AUTH, this means LOGIN.
            if (argument == null) {
                authentications.add("LOGIN");
            } else {
                // The security mechanisms are blank delimited tokens.
                StringTokenizer tokenizer = new StringTokenizer(argument);

                while (tokenizer.hasMoreTokens()) {
                    String mechanism = tokenizer.nextToken().toUpperCase();
                    authentications.add(mechanism);
                }
            }
        }
        // special case for some older servers.
        else if (extensionName.equals("AUTH=LOGIN")) {
            authentications.add("LOGIN");
        }
        // does this support transport level security?
        else if (extensionName.equals("STARTTLS")) {
            // flag this for later
            serverTLS = true;
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
        if (capabilities != null) {
            return (String)capabilities.get(name);
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
     * Authenticate with the server, if necessary (or possible).
     *
     * @return true if we are ok to proceed, false for an authentication
     *         failures.
     */
    protected boolean processAuthentication() throws MessagingException {
        // no authentication defined?
        if (!props.getBooleanProperty(MAIL_SMTP_AUTH, false)) {
            return true;
        }

        // we need to authenticate, but we don't have userid/password
        // information...fail this
        // immediately.
        if (username == null || password == null) {
            return false;
        }
        
        // if unable to get an appropriate authenticator, just fail it. 
        ClientAuthenticator authenticator = getSaslAuthenticator(); 
        if (authenticator == null) {
            throw new MessagingException("Unable to obtain SASL authenticator"); 
        }
        
        
        if (debug) {
            debugOut("Authenticating for user: " + username + " using " + authenticator.getMechanismName());
        }

        // if the authenticator has some initial data, we compose a command
        // containing the initial data.
        if (authenticator.hasInitialResponse()) {
            StringBuffer command = new StringBuffer();
            // the auth command initiates the handshaking.
            command.append("AUTH ");
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
            command.append("AUTH ");
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
            SMTPReply line;
            try {
                line = new SMTPReply(receiveLine());
            } catch (MalformedSMTPReplyException e) {
                throw new MessagingException(e.toString());
            } catch (MessagingException e) {
                throw e;
            }

            // if we get a completion return, we've passed muster, so give an
            // authentication response.
            if (line.getCode() == SMTPReply.AUTHENTICATION_COMPLETE) {
                debugOut("Successful SMTP authentication");
                return true;
            }
            // we have an additional challenge to process.
            else if (line.getCode() == SMTPReply.AUTHENTICATION_CHALLENGE) {
                // Does the authenticator think it is finished? We can't answer
                // an additional challenge,
                // so fail this.
                if (authenticator.isComplete()) {
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
                if (debug) {
                    debugOut("Authentication failure " + line);
                }
                return false;
            }
        }
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
     * Read the bytes in a stream a test to see if this 
     * conforms to the RFC 2045 rules for 8bit encoding. 
     * 
     * 1)  No more than 998 bytes long 
     * 2)  All lines are terminated with CRLF sequences
     * 3)  CR and LF characters only occur in properly 
     * formed line separators 
     * 4)  No null characters are allowed. 
     * 
     * @param inStream The source input stream.
     * 
     * @return true if this can be transmitted successfully 
     *         using 8bit encoding, false if an alternate encoding
     *         will be required.
     */
    protected boolean isValid8bit(InputStream inStream) { 
        try {
            int ch;  
            int lineLength = 0; 
            while ((ch = inStream.read()) >= 0) {
                // nulls are decidedly not allowed 
                if (ch == 0) {
                    return false; 
                }
                // start of a CRLF sequence (potentially) 
                else if (ch == '\r') {
                    // check the next character.  There must be one, 
                    // and it must be a LF for this to be value 
                    ch = inStream.read(); 
                    if (ch != '\n') {
                        return false; 
                    }
                    // reset the line length 
                    lineLength = 0; 
                }
                else {
                    // a normal character 
                    lineLength++; 
                    // make sure the line is not too long 
                    if (lineLength > 998) {
                        return false; 
                    }
                }
                
            }
        } catch (IOException e) {
            return false;  // can't read this, don't try passing it 
        }
        // this converted ok 
        return true; 
    }

    
    /**
     * Simple holder class for the address/send status duple, as we can have
     * mixed success for a set of addresses and a message
     */
    static public class SendStatus {
        public final static int SUCCESS = 0;

        public final static int INVALID_ADDRESS = 1;

        public final static int SEND_FAILURE = 2;

        public final static int GENERAL_ERROR = 3;

        // the status type of the send operation.
        int status;

        // the address associated with this status
        InternetAddress address;

        // the command string send to the server.
        String cmd;

        // the reply from the server.
        SMTPReply reply;

        /**
         * Constructor for a SendStatus item.
         *
         * @param s
         *            The status type.
         * @param a
         *            The address this is the status for.
         * @param c
         *            The command string associated with this status.
         * @param r
         *            The reply information from the server.
         */
        public SendStatus(int s, InternetAddress a, String c, SMTPReply r) {
            this.cmd = c;
            this.status = s;
            this.address = a;
            this.reply = r;
        }

        /**
         * Get the status information for this item.
         *
         * @return The current status code.
         */
        public int getStatus() {
            return this.status;
        }

        /**
         * Retrieve the InternetAddress object associated with this send
         * operation.
         *
         * @return The associated address object.
         */
        public InternetAddress getAddress() {
            return this.address;
        }

        /**
         * Retrieve the reply information associated with this send operati
         *
         * @return The SMTPReply object received for the operation.
         */
        public SMTPReply getReply() {
            return reply;
        }

        /**
         * Get the command string sent for this send operation.
         *
         * @return The command string for the MAIL TO command sent to the
         *         server.
         */
        public String getCommand() {
            return cmd;
        }

        /**
         * Get an exception object associated with this send operation. There is
         * a mechanism for reporting send success via a send operation, so this
         * will be either a success or failure exception.
         *
         * @param reportSuccess
         *            Indicates if we want success operations too.
         *
         * @return A newly constructed exception object.
         */
        public MessagingException getException(boolean reportSuccess) {
            if (status != SUCCESS) {
                return new SMTPAddressFailedException(address, cmd, reply.getCode(), reply.getMessage());
            } else {
                if (reportSuccess) {
                    return new SMTPAddressSucceededException(address, cmd, reply.getCode(), reply.getMessage());
                }
            }
            return null;
        }
    }

    
    /**
     * Reset the server connection after an error.
     *
     * @exception MessagingException
     */
    public void resetConnection() throws MessagingException {
        // we want the caller to retrieve the last response responsbile for
        // requiring the reset, so save and
        // restore that info around the reset.
        SMTPReply last = lastServerResponse;

        // send a reset command.
        SMTPReply line = sendCommand("RSET");

        // if this did not reset ok, just close the connection
        if (line.getCode() != SMTPReply.COMMAND_ACCEPTED) {
            close();
        }
        // restore this.
        lastServerResponse = last;
    }

    
    /**
     * Return the current reportSuccess property.
     *
     * @return The current reportSuccess property.
     */
    public boolean getReportSuccess() {
        return reportSuccess; 
    }

    /**
     * Set a new value for the reportSuccess property.
     *
     * @param report
     *            The new setting.
     */
    public void setReportSuccess(boolean report) {
        reportSuccess = report; 
    }
}

