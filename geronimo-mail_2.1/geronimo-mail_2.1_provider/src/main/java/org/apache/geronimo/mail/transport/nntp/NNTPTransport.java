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

package org.apache.geronimo.mail.transport.nntp;

import java.util.ArrayList;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;
import jakarta.mail.event.TransportEvent;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.NewsAddress;

import org.apache.geronimo.mail.util.ProtocolProperties;

/**
 * Simple implementation of NNTP transport. Just does plain RFC977-ish delivery.
 * <p/> There is no way to indicate failure for a given recipient (it's possible
 * to have a recipient address rejected). The sun impl throws exceptions even if
 * others successful), but maybe we do a different way... <p/>
 * 
 * @version $Rev$ $Date$
 */
public class NNTPTransport extends Transport {
    /**
     * property keys for protocol properties.
     */
    protected static final String NNTP_FROM = "from";

    protected static final int DEFAULT_NNTP_PORT = 119;
    protected static final int DEFAULT_NNTP_SSL_PORT = 563;

    // our accessor for protocol properties and the holder of 
    // protocol-specific information 
    protected ProtocolProperties props; 
    // our active connection object (shared code with the NNTPStore).
    protected NNTPConnection connection;

    /**
     * Normal constructor for an NNTPTransport() object. This constructor is
     * used to build a transport instance for the "smtp" protocol.
     * 
     * @param session
     *            The attached session.
     * @param name
     *            An optional URLName object containing target information.
     */
    public NNTPTransport(Session session, URLName name) {
        this(session, name, "nntp-post", DEFAULT_NNTP_PORT, false);
    }

    /**
     * Common constructor used by the POP3Store and POP3SSLStore classes
     * to do common initialization of defaults.
     *
     * @param session
     *            The host session instance.
     * @param name
     *            The URLName of the target.
     * @param protocol
     *            The protocol type ("pop3"). This helps us in
     *            retrieving protocol-specific session properties.
     * @param defaultPort
     *            The default port used by this protocol. For pop3, this will
     *            be 110. The default for pop3 with ssl is 995.
     * @param sslConnection
     *            Indicates whether an SSL connection should be used to initial
     *            contact the server. This is different from the STARTTLS
     *            support, which switches the connection to SSL after the
     *            initial startup.
     */
    protected NNTPTransport(Session session, URLName name, String protocol, int defaultPort, boolean sslConnection) {
        super(session, name);
        
        // create the protocol property holder.  This gives an abstraction over the different 
        // flavors of the protocol. 
        props = new ProtocolProperties(session, protocol, sslConnection, defaultPort); 
        // the connection manages connection for the transport 
        connection = new NNTPConnection(props); 
    }

    /**
     * Do the protocol connection for an NNTP transport. This handles server
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

        if (!connection.isPostingAllowed()) {
            throw new MessagingException("Posting disabled for host server");
        }
        // don't bother me w/ null messages or no addreses
        if (message == null) {
            throw new MessagingException("Null message");
        }

        // NNTP only handles instances of MimeMessage, not the more general
        // message case.
        if (!(message instanceof MimeMessage)) {
            throw new MessagingException("NNTP can only send MimeMessages");
        }

        // need to sort the from value out from a variety of sources.
        InternetAddress from = null;

        Address[] fromAddresses = message.getFrom();

        // If the message has a From address set, we just use that. Otherwise,
        // we set a From using
        // the property version, if available.
        if (fromAddresses == null || fromAddresses.length == 0) {
            // the from value can be set explicitly as a property
            String defaultFrom = props.getProperty(NNTP_FROM);
            if (defaultFrom == null) {
                message.setFrom(new InternetAddress(defaultFrom));
            }
        }

        // we must have a message list.
        if (addresses == null || addresses.length == 0) {
            throw new MessagingException("Null or empty address array");
        }

        boolean haveGroup = false;

        // enforce the requirement that all of the targets are NewsAddress
        // instances.
        for (int i = 0; i < addresses.length; i++) {
            if (!(addresses[i] instanceof NewsAddress)) {
                throw new MessagingException("Illegal NewsAddress " + addresses[i]);
            }
        }

        // event notifcation requires we send lists of successes and failures
        // broken down by category.
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

        boolean sendFailure = false;

        // now try to post this message to the different news groups.
        for (int i = 0; i < addresses.length; i++) {
            try {
                // select the target news group
                NNTPReply reply = connection.selectGroup(((NewsAddress) addresses[i]).getNewsgroup());

                if (reply.getCode() != NNTPReply.GROUP_SELECTED) {
                    invalidAddresses.add(addresses[i]);
                    sendFailure = true;
                } else {
                    // send data
                    connection.sendPost(message);
                    sentAddresses.add(addresses[i]);
                }
            } catch (MessagingException e) {
                unsentAddresses.add(addresses[i]);
                sendFailure = true;
            }
        }

        // create our lists for notification and exception reporting from this
        // point on.
        Address[] sent = (Address[]) sentAddresses.toArray(new Address[0]);
        Address[] unsent = (Address[]) unsentAddresses.toArray(new Address[0]);
        Address[] invalid = (Address[]) invalidAddresses.toArray(new Address[0]);

        if (sendFailure) {
            // did we deliver anything at all?
            if (sent.length == 0) {
                // notify of the error.
                notifyTransportListeners(TransportEvent.MESSAGE_NOT_DELIVERED, sent, unsent, invalid, message);
            } else {
                // notify that we delivered at least part of this
                notifyTransportListeners(TransportEvent.MESSAGE_PARTIALLY_DELIVERED, sent, unsent, invalid, message);
            }

            throw new MessagingException("Error posting NNTP message");
        }

        // notify our listeners of successful delivery.
        notifyTransportListeners(TransportEvent.MESSAGE_DELIVERED, sent, unsent, invalid, message);
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
}
