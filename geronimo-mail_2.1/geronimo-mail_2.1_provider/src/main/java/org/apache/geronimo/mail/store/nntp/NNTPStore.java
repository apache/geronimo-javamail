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

package org.apache.geronimo.mail.store.nntp;

import java.io.File;
import java.util.Iterator;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.URLName;

import org.apache.geronimo.mail.store.nntp.newsrc.NNTPNewsrc;
import org.apache.geronimo.mail.store.nntp.newsrc.NNTPNewsrcFile;
import org.apache.geronimo.mail.store.nntp.newsrc.NNTPNewsrcGroup;
import org.apache.geronimo.mail.transport.nntp.NNTPConnection;
import org.apache.geronimo.mail.util.ProtocolProperties;
import org.apache.geronimo.mail.util.SessionUtil;

/**
 * NNTP implementation of javax.mail.Store POP protocol spec is implemented in
 * org.apache.geronimo.mail.store.pop3.NNTPConnection
 * 
 * @version $Rev$ $Date$
 */
public class NNTPStore extends Store {
    protected static final String NNTP_NEWSRC = "newsrc";

    protected static final String protocol = "nntp";

    protected static final int DEFAULT_NNTP_PORT = 119;
    protected static final int DEFAULT_NNTP_SSL_PORT = 563;

    // our accessor for protocol properties and the holder of 
    // protocol-specific information 
    protected ProtocolProperties props; 
    // our active connection object (shared code with the NNTPStore).
    protected NNTPConnection connection;

    // the root folder
    protected NNTPRootFolder root;
    // the newsrc file where we store subscriptions and seen message markers.
    protected NNTPNewsrc newsrc;
    
    /**
     * Construct an NNTPStore item. This will load the .newsrc file associated
     * with the server.
     * 
     * @param session
     *            The owning mail Session.
     * @param name
     *            The Store urlName, which can contain server target
     *            information.
     */
    public NNTPStore(Session session, URLName name) {
        this(session, name, "nntp", DEFAULT_NNTP_PORT, false);
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
     *            The protocol type ("nntp" or "nntps"). This helps us in
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
    protected NNTPStore(Session session, URLName name, String protocol, int defaultPort, boolean sslConnection) {
        super(session, name);
        
        // create the protocol property holder.  This gives an abstraction over the different 
        // flavors of the protocol. 
        props = new ProtocolProperties(session, protocol, sslConnection, defaultPort); 

        // the connection manages connection for the transport 
        connection = new NNTPConnection(props); 
    }

    /**
     * @see Store#getDefaultFolder()
     * 
     * This returns a root folder object for all of the news groups.
     */
    public Folder getDefaultFolder() throws MessagingException {
        checkConnectionStatus();
        if (root == null) {
            return new NNTPRootFolder(this, connection.getHost(), connection.getWelcomeString());
        }
        return root;
    }

    /**
     * @see Store#getFolder(String)
     */
    public Folder getFolder(String name) throws MessagingException {
        return getDefaultFolder().getFolder(name);
    }

    /**
     * 
     * @see Store#getFolder(URLName)
     */
    public Folder getFolder(URLName url) throws MessagingException {
        return getDefaultFolder().getFolder(url.getFile());
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
        // the connection pool handles all of the details here. But don't proceed 
        // without a connection 
        if (!connection.protocolConnect(host, port, username, password)) {
            return false; 
        }

        // see if we have a newsrc file location specified
        String newsrcFile = props.getProperty(NNTP_NEWSRC);

        File source = null;

        // not given as a property? Then look for a file in user.home
        if (newsrcFile != null) {
            source = new File(newsrcFile);
        } else {
            // ok, look for a file in the user.home directory. If possible,
            // we'll try for a file
            // with the hostname appended.
            String home = SessionUtil.getProperty("user.home");

            // try for a host-specific file first. If not found, use (and
            // potentially create) a generic
            // .newsrc file.
            newsrcFile = ".newsrc-" + host;
            source = new File(home, newsrcFile);
            if (!source.exists()) {
                source = new File(home, ".newsrc");
            }
        }

        // now create a newsrc read and load the file.
        newsrc = new NNTPNewsrcFile(source);
        newsrc.load();

        // we're going to return success here, but in truth, the server may end
        // up asking for our bonafides at any time, and we'll be expected to authenticate then.
        return true;
    }
    

    /**
     * @see javax.mail.Service#close()
     */
    public void close() throws MessagingException {
        // This is done to ensure proper event notification.
        super.close();
        // persist the newsrc file, if possible
        if (newsrc != null) {
            newsrc.close();
            newsrc = null; 
        }
        connection.close();
        connection = null;
    }

    private void checkConnectionStatus() throws MessagingException {
        if (!this.isConnected()) {
            throw new MessagingException("Not connected ");
        }
    }

    /**
     * Retrieve the server connection created by this store.
     * 
     * @return The active connection object.
     */
    NNTPConnection getConnection() {
        return connection;
    }

    /**
     * Retrieve the Session object this Store is operating under.
     * 
     * @return The attached Session instance.
     */
    Session getSession() {
        return session;
    }

    /**
     * Retrieve all of the groups we nave persistent store information about.
     * 
     * @return The set of groups contained in the newsrc file.
     */
    Iterator getNewsrcGroups() {
        return newsrc.getGroups();
    }

    /**
     * Retrieve the newsrc group information for a named group. If the file does
     * not currently include this group, an unsubscribed group will be added to
     * the file.
     * 
     * @param name
     *            The name of the target group.
     * 
     * @return The NNTPNewsrcGroup item corresponding to this name.
     */
    NNTPNewsrcGroup getNewsrcGroup(String name) {
        return newsrc.getGroup(name);
    }
}
