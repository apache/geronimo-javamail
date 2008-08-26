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
 
import java.io.PrintStream; 
import java.util.LinkedList;
import java.util.List;

import javax.mail.AuthenticationFailedException;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;

import org.apache.geronimo.javamail.store.pop3.connection.POP3Connection; 
import org.apache.geronimo.javamail.store.pop3.connection.POP3ConnectionPool; 
import org.apache.geronimo.javamail.util.ProtocolProperties;

/**
 * POP3 implementation of javax.mail.Store POP protocol spec is implemented in
 * org.apache.geronimo.javamail.store.pop3.POP3Connection
 * 
 * @version $Rev$ $Date$
 */

public class POP3Store extends Store {
    protected static final int DEFAULT_POP3_PORT = 110;
    protected static final int DEFAULT_POP3_SSL_PORT = 995;
    
    
    // our accessor for protocol properties and the holder of 
    // protocol-specific information 
    protected ProtocolProperties props; 
    // our connection object    
    protected POP3ConnectionPool connectionPool; 
    // our session provided debug output stream.
    protected PrintStream debugStream;
    // the debug flag 
    protected boolean debug; 
    // the root folder 
    protected POP3RootFolder root; 
    // until we're connected, we're closed 
    boolean closedForBusiness = true; 
    protected LinkedList openFolders = new LinkedList(); 
    
    
    public POP3Store(Session session, URLName name) {
        this(session, name, "pop3", DEFAULT_POP3_PORT, false);
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
    protected POP3Store(Session session, URLName name, String protocol, int defaultPort, boolean sslConnection) {
        super(session, name);
        
        // create the protocol property holder.  This gives an abstraction over the different 
        // flavors of the protocol. 
        props = new ProtocolProperties(session, protocol, sslConnection, defaultPort); 

        // get our debug settings
        debugStream = session.getDebugOut();
        debug = session.getDebug(); 
        // the connection pool manages connections for the stores, folder, and message usage. 
        connectionPool = new POP3ConnectionPool(this, props); 
    }


    /**
     * Return a Folder object that represents the root of the namespace for the current user.
     *
     * Note that in some store configurations (such as IMAP4) the root folder might
     * not be the INBOX folder.
     *
     * @return the root Folder
     * @throws MessagingException if there was a problem accessing the store
     */
	public Folder getDefaultFolder() throws MessagingException {
		checkConnectionStatus();
        // if no root yet, create a root folder instance. 
        if (root == null) {
            return new POP3RootFolder(this);
        }
        return root;
	}

    /**
     * Return the Folder corresponding to the given name.
     * The folder might not physically exist; the {@link Folder#exists()} method can be used
     * to determine if it is real.
     * 
     * @param name   the name of the Folder to return
     * 
     * @return the corresponding folder
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
	public Folder getFolder(String name) throws MessagingException {
        return getDefaultFolder().getFolder(name);
	}

    
    /**
     * Return the folder identified by the URLName; the URLName must refer to this Store.
     * Implementations may use the {@link URLName#getFile()} method to determined the folder name.
     * 
     * @param url
     * 
     * @return the corresponding folder
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
	public Folder getFolder(URLName url) throws MessagingException {
        return getDefaultFolder().getFolder(url.getFile());
	}

    
    /**
     * @see javax.mail.Service#protocolConnect(java.lang.String, int,
     *      java.lang.String, java.lang.String)
     */
    protected synchronized boolean protocolConnect(String host, int port, String username, String password) throws MessagingException {
        
        if (debug) {
            debugOut("Connecting to server " + host + ":" + port + " for user " + username);
        }

        // the connection pool handles all of the details here. 
        if (connectionPool.protocolConnect(host, port, username, password)) 
        {
            // the store is now open 
            closedForBusiness = false; 
            return true; 
        }
        return false; 
    }
    
    
    /**
     * Get a connection for the store. 
     * 
     * @return The request connection object. 
     * @exception MessagingException
     */
    protected POP3Connection getConnection() throws MessagingException {
        return connectionPool.getConnection(); 
    }
    
    /**
     * Return a connection back to the connection pool after 
     * it has been used for a request. 
     * 
     * @param connection The return connection.
     * 
     * @exception MessagingException
     */
    protected void releaseConnection(POP3Connection connection) throws MessagingException {
        connectionPool.releaseConnection(connection); 
    }
    
    /**
     * Get a connection object for a folder to use. 
     * 
     * @param folder The requesting folder (always the inbox for POP3).
     * 
     * @return An active POP3Connection. 
     * @exception MessagingException
     */
    synchronized POP3Connection getFolderConnection(POP3Folder folder) throws MessagingException {
        POP3Connection connection = connectionPool.getConnection(); 
        openFolders.add(folder);
        return connection; 
    }
    
    /**
     * Release a connection object after a folder is 
     * finished with a request. 
     * 
     * @param folder     The requesting folder.
     * @param connection
     * 
     * @exception MessagingException
     */
    synchronized void releaseFolderConnection(POP3Folder folder, POP3Connection connection) throws MessagingException {
        openFolders.remove(folder); 
        // return this back to the pool 
        connectionPool.releaseConnection(connection); 
    }
    
    /**
     * Close all open folders.  We have a small problem here with a race condition.  There's no safe, single
     * synchronization point for us to block creation of new folders while we're closing.  So we make a copy of
     * the folders list, close all of those folders, and keep repeating until we're done.
     */
    protected void closeOpenFolders() {
        // we're no longer accepting additional opens.  Any folders that open after this point will get an
        // exception trying to get a connection.
        closedForBusiness = true;

        while (true) {
            List folders = null;

            // grab our lock, copy the open folders reference, and null this out.  Once we see a null
            // open folders ref, we're done closing.
            synchronized(connectionPool) {
                folders = openFolders;
                openFolders = new LinkedList();
            }

            // null folder, we're done
            if (folders.isEmpty()) {
                return;
            }
            // now close each of the open folders.
            for (int i = 0; i < folders.size(); i++) {
                POP3Folder folder = (POP3Folder)folders.get(i);
                try {
                    folder.close(false);
                } catch (MessagingException e) {
                }
            }
        }
    }
    

    /**
     * @see javax.mail.Service#isConnected()
     */
    public boolean isConnected() {
        try {
            POP3Connection connection = getConnection(); 
            // a null connection likely means we had a failure establishing a 
            // new connection to the POP3 server.  
            if (connection == null) {
                return false; 
            }
            try {
                // make sure the server is really there 
                connection.pingServer(); 
                return true; 
            }
            finally {
                // return the connection to the pool when finished 
                if (connection != null) {
                    releaseConnection(connection); 
                }
            }
        } catch (MessagingException e) {
        }
        return false; 
    }

    /**
     * Close the store, and any open folders associated with the 
     * store. 
     * 
     * @exception MessagingException
     */
	public synchronized void close() throws MessagingException{
        // if already closed, nothing to do. 
        if (closedForBusiness) {
            return; 
        }
        
        // close the folders first, then shut down the Store. 
        closeOpenFolders();
        
        connectionPool.close(); 
        connectionPool = null; 

		// make sure we do the superclass close operation first so 
        // notification events get broadcast properly. 
		super.close();
	}

    /**
     * Check the status of our connection. 
     * 
     * @exception MessagingException
     */
    private void checkConnectionStatus() throws MessagingException {
        if (!this.isConnected()) {
            throw new MessagingException("Not connected ");
        }
    }

    /**
     * Internal debug output routine.
     *
     * @param value  The string value to output.
     */
    void debugOut(String message) {
        debugStream.println("POP3Store DEBUG: " + message);
    }

    /**
     * Internal debugging routine for reporting exceptions.
     *
     * @param message A message associated with the exception context.
     * @param e       The received exception.
     */
    void debugOut(String message, Throwable e) {
        debugOut("Received exception -> " + message);
        debugOut("Exception message -> " + e.getMessage());
        e.printStackTrace(debugStream);
    }
    
    /**
     * Finalizer to perform IMAPStore() cleanup when 
     * no longer in use. 
     * 
     * @exception Throwable
     */
    protected void finalize() throws Throwable {
        super.finalize(); 
        close(); 
    }
}
