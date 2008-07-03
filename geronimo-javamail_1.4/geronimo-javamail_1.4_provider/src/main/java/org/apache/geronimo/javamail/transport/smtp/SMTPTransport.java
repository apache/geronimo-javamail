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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket; 
import java.util.ArrayList;

import javax.mail.Address;
import javax.mail.AuthenticationFailedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.event.TransportEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;     
import javax.mail.internet.MimePart;     

import org.apache.geronimo.javamail.util.ProtocolProperties; 
import org.apache.geronimo.javamail.transport.smtp.SMTPConnection.SendStatus; 

/**
 * Simple implementation of SMTP transport. Just does plain RFC821-ish delivery.
 * <p/> Supported properties : <p/>
 * <ul>
 * <li> mail.host : to set the server to deliver to. Default = localhost</li>
 * <li> mail.smtp.port : to set the port. Default = 25</li>
 * <li> mail.smtp.locahost : name to use for HELO/EHLO - default getHostName()</li>
 * </ul>
 * <p/> There is no way to indicate failure for a given recipient (it's possible
 * to have a recipient address rejected). The sun impl throws exceptions even if
 * others successful), but maybe we do a different way... <p/> TODO : lots.
 * ESMTP, user/pass, indicate failure, etc...
 *
 * @version $Rev$ $Date$
 */
public class SMTPTransport extends Transport {
    /**
     * property keys for protocol properties. The actual property name will be
     * appended with "mail." + protocol + ".", where the protocol is either
     * "smtp" or "smtps".
     */
    protected static final String MAIL_SMTP_DSN_NOTIFY = "dsn.notify";
    protected static final String MAIL_SMTP_SENDPARTIAL = "sendpartial";
    protected static final String MAIL_SMTP_EXTENSION = "mailextension";
    protected static final String DEFAULT_MAIL_HOST = "localhost";

    protected static final int DEFAULT_MAIL_SMTP_PORT = 25;
    protected static final int DEFAULT_MAIL_SMTPS_PORT = 465;


    // do we use SSL for our initial connection?
    protected boolean sslConnection = false;
    
    // our accessor for protocol properties and the holder of 
    // protocol-specific information 
    protected ProtocolProperties props; 
    // our active connection object 
    protected SMTPConnection connection;

    // the last response line received from the server.
    protected SMTPReply lastServerResponse = null;

    /**
     * Normal constructor for an SMTPTransport() object. This constructor is
     * used to build a transport instance for the "smtp" protocol.
     *
     * @param session
     *            The attached session.
     * @param name
     *            An optional URLName object containing target information.
     */
    public SMTPTransport(Session session, URLName name) {
        this(session, name, "smtp", DEFAULT_MAIL_SMTP_PORT, false);
    }
    

    /**
     * Common constructor used by the SMTPTransport and SMTPSTransport classes
     * to do common initialization of defaults.
     *
     * @param session
     *            The host session instance.
     * @param name
     *            The URLName of the target.
     * @param protocol
     *            The protocol type (either "smtp" or "smtps". This helps us in
     *            retrieving protocol-specific session properties.
     * @param defaultPort
     *            The default port used by this protocol. For "smtp", this will
     *            be 25. The default for "smtps" is 465.
     * @param sslConnection
     *            Indicates whether an SSL connection should be used to initial
     *            contact the server. This is different from the STARTTLS
     *            support, which switches the connection to SSL after the
     *            initial startup.
     */
    protected SMTPTransport(Session session, URLName name, String protocol, int defaultPort, boolean sslConnection) {
        super(session, name);
        
        // create the protocol property holder.  This gives an abstraction over the different 
        // flavors of the protocol. 
        props = new ProtocolProperties(session, protocol, sslConnection, defaultPort); 
        // the connection manages connection for the transport 
        connection = new SMTPConnection(props); 
    }

    
    /**
     * Connect to a server using an already created socket. This connection is
     * just like any other connection, except we will not create a new socket.
     *
     * @param socket
     *            The socket connection to use.
     */
    public void connect(Socket socket) throws MessagingException {
        connection.connect(socket); 
        super.connect();
    }
    

    /**
     * Do the protocol connection for an SMTP transport. This handles server
     * authentication, if possible. Returns false if unable to connect to the
     * server.
     *
     * @param host
     *            The target host name.
     * @param port
     *            The server port number.
     * @param user
     *            The authentication user (if any).
     * @param password
     *            The server password. Might not be sent directly if more
     *            sophisticated authentication is used.
     *
     * @return true if we were able to connect to the server properly, false for
     *         any failures.
     * @exception MessagingException
     */
    protected boolean protocolConnect(String host, int port, String username, String password)
            throws MessagingException {
        // the connection pool handles all of the details here. 
        return connection.protocolConnect(host, port, username, password);
    }

    /**
     * Send a message to multiple addressees.
     *
     * @param message
     *            The message we're sending.
     * @param addresses
     *            An array of addresses to send to.
     *
     * @exception MessagingException
     */
    public void sendMessage(Message message, Address[] addresses) throws MessagingException {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected");
        }
        // don't bother me w/ null messages or no addreses
        if (message == null) {
            throw new MessagingException("Null message");
        }

        // SMTP only handles instances of MimeMessage, not the more general
        // message case.
        if (!(message instanceof MimeMessage)) {
            throw new MessagingException("SMTP can only send MimeMessages");
        }

        // we must have a message list.
        if (addresses == null || addresses.length == 0) {
            throw new MessagingException("Null or empty address array");
        }
        
        boolean reportSuccess = getReportSuccess(); 
        
        // now see how we're configured for this send operation.
        boolean partialSends = false;

        // this can be attached directly to the message.
        if (message instanceof SMTPMessage) {
            partialSends = ((SMTPMessage) message).getSendPartial();
        }

        // if still false on the message object, check for a property
        // version also
        if (!partialSends) {
            partialSends = props.getBooleanProperty(MAIL_SMTP_SENDPARTIAL, false);
        }

        boolean haveGroup = false;

        // enforce the requirement that all of the targets are InternetAddress
        // instances.
        for (int i = 0; i < addresses.length; i++) {
            if (addresses[i] instanceof InternetAddress) {
                // and while we're here, see if we have a groups in the address
                // list. If we do, then
                // we're going to need to expand these before sending.
                if (((InternetAddress) addresses[i]).isGroup()) {
                    haveGroup = true;
                }
            } else {
                throw new MessagingException("Illegal InternetAddress " + addresses[i]);
            }
        }

        // did we find a group? Time to expand this into our full target list.
        if (haveGroup) {
            addresses = expandGroups(addresses);
        }

        SendStatus[] stats = new SendStatus[addresses.length];

        // create our lists for notification and exception reporting.
        Address[] sent = null;
        Address[] unsent = null;
        Address[] invalid = null;

        try {
            // send sender first. If this failed, send a failure notice of the
            // event, using the full list of
            // addresses as the unsent, and nothing for the rest.
            if (!connection.sendMailFrom(message)) {
                unsent = addresses;
                sent = new Address[0];
                invalid = new Address[0];
                // notify of the error.
                notifyTransportListeners(TransportEvent.MESSAGE_NOT_DELIVERED, sent, unsent, invalid, message);

                // include the reponse information here.
                SMTPReply last = connection.getLastServerResponse(); 
                // now send an "uber-exception" to indicate the failure.
                throw new SMTPSendFailedException("MAIL FROM", last.getCode(), last.getMessage(), null, sent, unsent,
                        invalid);
            }

            // get the additional notification status, if available 
            String dsn = getDeliveryStatusNotification(message);

            // we need to know about any failures once we've gone through the
            // complete list, so keep a
            // failure flag.
            boolean sendFailure = false;

            // event notifcation requires we send lists of successes and
            // failures broken down by category.
            // The categories are:
            //
            // 1) addresses successfully processed.
            // 2) addresses deemed valid, but had a processing failure that
            // prevented sending.
            // 3) addressed deemed invalid (basically all other processing
            // failures).
            ArrayList sentAddresses = new ArrayList();
            ArrayList unsentAddresses = new ArrayList();
            ArrayList invalidAddresses = new ArrayList();

            // Now we add a MAIL TO record for each recipient. At this point, we
            // just collect
            for (int i = 0; i < addresses.length; i++) {
                InternetAddress target = (InternetAddress) addresses[i];

                // write out the record now.
                SendStatus status = connection.sendRcptTo(target, dsn);
                stats[i] = status;

                switch (status.getStatus()) {
                    // successfully sent
                    case SendStatus.SUCCESS:
                        sentAddresses.add(target);
                        break;

                    // we have an invalid address of some sort, or a general sending
                    // error (which we'll
                    // interpret as due to an invalid address.
                    case SendStatus.INVALID_ADDRESS:
                    case SendStatus.GENERAL_ERROR:
                        sendFailure = true;
                        invalidAddresses.add(target);
                        break;

                    // good address, but this was a send failure.
                    case SendStatus.SEND_FAILURE:
                        sendFailure = true;
                        unsentAddresses.add(target);
                        break;
                    }
            }

            // if we had a send failure, then we need to check if we allow
            // partial sends. If not allowed,
            // we abort the send operation now.
            if (sendFailure) {
                // if we're not allowing partial successes or we've failed on
                // all of the addresses, it's
                // time to abort.
                if (!partialSends || sentAddresses.isEmpty()) {
                    // we send along the valid and invalid address lists on the
                    // notifications and
                    // exceptions.
                    // however, since we're aborting the entire send, the
                    // successes need to become
                    // members of the failure list.
                    unsentAddresses.addAll(sentAddresses);

                    // this one is empty.
                    sent = new Address[0];
                    unsent = (Address[]) unsentAddresses.toArray(new Address[0]);
                    invalid = (Address[]) invalidAddresses.toArray(new Address[0]);

                    // go reset our connection so we can process additional
                    // sends.
                    connection.resetConnection();

                    // get a list of chained exceptions for all of the failures.
                    MessagingException failures = generateExceptionChain(stats, false);

                    // now send an "uber-exception" to indicate the failure.
                    throw new SMTPSendFailedException("MAIL TO", 0, "Invalid Address", failures, sent, unsent, invalid);
                }
            }

            try {
                // try to send the data
                connection.sendData(message);
            } catch (MessagingException e) {
                // If there's an error at this point, this is a complete
                // delivery failure.
                // we send along the valid and invalid address lists on the
                // notifications and
                // exceptions.
                // however, since we're aborting the entire send, the successes
                // need to become
                // members of the failure list.
                unsentAddresses.addAll(sentAddresses);

                // this one is empty.
                sent = new Address[0];
                unsent = (Address[]) unsentAddresses.toArray(new Address[0]);
                invalid = (Address[]) invalidAddresses.toArray(new Address[0]);
                // notify of the error.
                notifyTransportListeners(TransportEvent.MESSAGE_NOT_DELIVERED, sent, unsent, invalid, message);
                // send a send failure exception.
                throw new SMTPSendFailedException("DATA", 0, "Send failure", e, sent, unsent, invalid);
            }

            // create our lists for notification and exception reporting from
            // this point on.
            sent = (Address[]) sentAddresses.toArray(new Address[0]);
            unsent = (Address[]) unsentAddresses.toArray(new Address[0]);
            invalid = (Address[]) invalidAddresses.toArray(new Address[0]);

            // if sendFailure is true, we had an error during the address phase,
            // but we had permission to
            // process this as a partial send operation. Now that the data has
            // been sent ok, it's time to
            // report the partial failure.
            if (sendFailure) {
                // notify our listeners of the partial delivery.
                notifyTransportListeners(TransportEvent.MESSAGE_PARTIALLY_DELIVERED, sent, unsent, invalid, message);

                // get a list of chained exceptions for all of the failures (and
                // the successes, if reportSuccess has been
                // turned on).
                MessagingException failures = generateExceptionChain(stats, reportSuccess);

                // now send an "uber-exception" to indicate the failure.
                throw new SMTPSendFailedException("MAIL TO", 0, "Invalid Address", failures, sent, unsent, invalid);
            }

            // notify our listeners of successful delivery.
            notifyTransportListeners(TransportEvent.MESSAGE_DELIVERED, sent, unsent, invalid, message);

            // we've not had any failures, but we've been asked to report
            // success as an exception. Do
            // this now.
            if (reportSuccess) {
                // generate the chain of success exceptions (we already know
                // there are no failure ones to report).
                MessagingException successes = generateExceptionChain(stats, reportSuccess);
                if (successes != null) {
                    throw successes;
                }
            }
        } catch (SMTPSendFailedException e) {
            // if this is a send failure, we've already handled
            // notifications....just rethrow it.
            throw e;
        } catch (MessagingException e) {
            // notify of the error.
            notifyTransportListeners(TransportEvent.MESSAGE_NOT_DELIVERED, sent, unsent, invalid, message);
            throw e;
        }
    }
    
    
    /**
     * Determine what delivery status notification should
     * be added to the RCPT TO: command. 
     * 
     * @param message The message we're sending.
     * 
     * @return The string NOTIFY= value to add to the command. 
     */
    protected String getDeliveryStatusNotification(Message message) {
        String dsn = null;

        // there's an optional notification argument that can be added to
        // MAIL TO. See if we've been
        // provided with one.

        // an SMTPMessage object is the first source
        if (message instanceof SMTPMessage) {
            // get the notification options
            int options = ((SMTPMessage) message).getNotifyOptions();

            switch (options) {
            // a zero value indicates nothing is set.
            case 0:
                break;

            case SMTPMessage.NOTIFY_NEVER:
                dsn = "NEVER";
                break;

            case SMTPMessage.NOTIFY_SUCCESS:
                dsn = "SUCCESS";
                break;

            case SMTPMessage.NOTIFY_FAILURE:
                dsn = "FAILURE";
                break;

            case SMTPMessage.NOTIFY_DELAY:
                dsn = "DELAY";
                break;

            // now for combinations...there are few enough combinations here
            // that we can just handle this in the switch statement rather
            // than have to
            // concatentate everything together.
            case (SMTPMessage.NOTIFY_SUCCESS + SMTPMessage.NOTIFY_FAILURE):
                dsn = "SUCCESS,FAILURE";
                break;

            case (SMTPMessage.NOTIFY_SUCCESS + SMTPMessage.NOTIFY_DELAY):
                dsn = "SUCCESS,DELAY";
                break;

            case (SMTPMessage.NOTIFY_FAILURE + SMTPMessage.NOTIFY_DELAY):
                dsn = "FAILURE,DELAY";
                break;

            case (SMTPMessage.NOTIFY_SUCCESS + SMTPMessage.NOTIFY_FAILURE + SMTPMessage.NOTIFY_DELAY):
                dsn = "SUCCESS,FAILURE,DELAY";
                break;
            }
        }

        // if still null, grab a property value (yada, yada, yada...)
        if (dsn == null) {
            dsn = props.getProperty(MAIL_SMTP_DSN_NOTIFY);
        }
        return dsn; 
    }
    


    /**
     * Close the connection. On completion, we'll be disconnected from the
     * server and unable to send more data.
     * 
     * @exception MessagingException
     */
    public void close() throws MessagingException {
        // This is done to ensure proper event notification.
        super.close();
        // NB:  We reuse the connection if asked to reconnect 
        connection.close();
    }
    
    
    /**
     * Turn a series of send status items into a chain of exceptions indicating
     * the state of each send operation.
     *
     * @param stats
     *            The list of SendStatus items.
     * @param reportSuccess
     *            Indicates whether we should include the report success items.
     *
     * @return The head of a chained list of MessagingExceptions.
     */
    protected MessagingException generateExceptionChain(SendStatus[] stats, boolean reportSuccess) {
        MessagingException current = null;

        for (int i = 0; i < stats.length; i++) {
            SendStatus status = stats[i];

            if (status != null) {
                MessagingException nextException = stats[i].getException(reportSuccess);
                // if there's an exception associated with this status, chain it
                // up with the rest.
                if (nextException != null) {
                    if (current == null) {
                        current = nextException;
                    } else {
                        current.setNextException(nextException);
                        current = nextException;
                    }
                }
            }
        }
        return current;
    }

    /**
     * Expand the address list by converting any group addresses into single
     * address targets.
     *
     * @param addresses
     *            The input array of addresses.
     *
     * @return The expanded array of addresses.
     * @exception MessagingException
     */
    protected Address[] expandGroups(Address[] addresses) throws MessagingException {
        ArrayList expandedAddresses = new ArrayList();

        // run the list looking for group addresses, and add the full group list
        // to our targets.
        for (int i = 0; i < addresses.length; i++) {
            InternetAddress address = (InternetAddress) addresses[i];
            // not a group? Just copy over to the other list.
            if (!address.isGroup()) {
                expandedAddresses.add(address);
            } else {
                // get the group address and copy each member of the group into
                // the expanded list.
                InternetAddress[] groupAddresses = address.getGroup(true);
                for (int j = 1; j < groupAddresses.length; j++) {
                    expandedAddresses.add(groupAddresses[j]);
                }
            }
        }

        // convert back into an array.
        return (Address[]) expandedAddresses.toArray(new Address[0]);
    }
    

    /**
     * Retrieve the local client host name.
     *
     * @return The string version of the local host name.
     * @exception SMTPTransportException
     */
    public String getLocalHost() throws MessagingException {
        return connection.getLocalHost(); 
    }

    
    /**
     * Explicitly set the local host information.
     *
     * @param localHost
     *            The new localHost name.
     */
    public void setLocalHost(String localHost) {
        connection.setLocalHost(localHost); 
    }

    
    /**
     * Return the current reportSuccess property.
     *
     * @return The current reportSuccess property.
     */
    public boolean getReportSuccess() {
        return connection.getReportSuccess(); 
    }

    /**
     * Set a new value for the reportSuccess property.
     *
     * @param report
     *            The new setting.
     */
    public void setReportSuccess(boolean report) {
        connection.setReportSuccess(report); 
    }

    /**
     * Return the current startTLS property.
     *
     * @return The current startTLS property.
     */
    public boolean getStartTLS() {
        return connection.getStartTLS(); 
    }

    /**
     * Set a new value for the startTLS property.
     *
     * @param start
     *            The new setting.
     */
    public void setStartTLS(boolean start) {
        connection.setStartTLS(start); 
    }

    /**
     * Retrieve the SASL realm used for DIGEST-MD5 authentication. This will
     * either be explicitly set, or retrieved using the mail.smtp.sasl.realm
     * session property.
     *
     * @return The current realm information (which can be null).
     */
    public String getSASLRealm() {
        return connection.getSASLRealm(); 
    }

    /**
     * Explicitly set the SASL realm used for DIGEST-MD5 authenticaiton.
     *
     * @param name
     *            The new realm name.
     */
    public void setSASLRealm(String name) {
        connection.setSASLRealm(name); 
    }
}
