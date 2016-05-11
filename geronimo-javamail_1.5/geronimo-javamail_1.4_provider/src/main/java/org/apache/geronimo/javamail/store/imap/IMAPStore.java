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


package org.apache.geronimo.javamail.store.imap;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.mail.AuthenticationFailedException;
import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Quota;
import javax.mail.QuotaAwareStore;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.event.StoreEvent; 

import org.apache.geronimo.javamail.store.imap.connection.IMAPConnection; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPConnectionPool; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPOkResponse; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPNamespaceResponse; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPNamespace; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPServerStatusResponse; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPUntaggedResponse; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPUntaggedResponseHandler; 
import org.apache.geronimo.javamail.util.ProtocolProperties;

/**
 * IMAP implementation of javax.mail.Store
 * POP protocol spec is implemented in
 * org.apache.geronimo.javamail.store.pop3.IMAPConnection
 *
 * @version $Rev$ $Date$
 */

public class IMAPStore extends Store implements QuotaAwareStore, IMAPUntaggedResponseHandler {
    // the default connection ports for secure and non-secure variations
    protected static final int DEFAULT_IMAP_PORT = 143;
    protected static final int DEFAULT_IMAP_SSL_PORT = 993;
    
    protected static final String MAIL_STATUS_TIMEOUT = "statuscacheimeout";
    protected static final int DEFAULT_STATUS_TIMEOUT = 1000; 
    
    // our accessor for protocol properties and the holder of 
    // protocol-specific information 
    protected ProtocolProperties props; 

    // the connection pool we use for access 
	protected IMAPConnectionPool connectionPool;

    // the root folder
    protected IMAPRootFolder root;

    // the list of open folders (which also represents an open connection).
    protected List openFolders = new LinkedList();

    // our session provided debug output stream.
    protected PrintStream debugStream;
    // the debug flag 
    protected boolean debug; 
    // until we're connected, we're closed 
    boolean closedForBusiness = true; 
    // The timeout value for our status cache 
    long statusCacheTimeout = 0; 

    /**
     * Construct an IMAPStore item.
     *
     * @param session The owning javamail Session.
     * @param urlName The Store urlName, which can contain server target information.
     */
	public IMAPStore(Session session, URLName urlName) {
        // we're the imap protocol, our default connection port is 119, and don't use 
        // an SSL connection for the initial hookup 
		this(session, urlName, "imap", false, DEFAULT_IMAP_PORT);
	}
                                                          
    /**
     * Protected common constructor used by both the IMAPStore and the IMAPSSLStore
     * to initialize the Store instance. 
     * 
     * @param session  The Session we're attached to.
     * @param urlName  The urlName.
     * @param protocol The protocol name.
     * @param sslConnection
     *                 The sslConnection flag.
     * @param defaultPort
     *                 The default connection port.
     */
    protected IMAPStore(Session session, URLName urlName, String protocol, boolean sslConnection, int defaultPort) {
        super(session, urlName); 
        // create the protocol property holder.  This gives an abstraction over the different 
        // flavors of the protocol. 
        props = new ProtocolProperties(session, protocol, sslConnection, defaultPort); 
        
        // get the status timeout value for the folders. 
        statusCacheTimeout = props.getIntProperty(MAIL_STATUS_TIMEOUT, DEFAULT_STATUS_TIMEOUT);

        // get our debug settings
        debugStream = session.getDebugOut();
        debug = session.getDebug(); 
        
        // create a connection pool we can retrieve connections from 
        connectionPool = new IMAPConnectionPool(this, props); 
    }
    
    
    /**
     * Attempt the protocol-specific connection; subclasses should override this to establish
     * a connection in the appropriate manner.
     * 
     * This method should return true if the connection was established.
     * It may return false to cause the {@link #connect(String, int, String, String)} method to
     * reattempt the connection after trying to obtain user and password information from the user.
     * Alternatively it may throw a AuthenticatedFailedException to abandon the conection attempt.
     * 
     * @param host     The target host name of the service.
     * @param port     The connection port for the service.
     * @param user     The user name used for the connection.
     * @param password The password used for the connection.
     * 
     * @return true if a connection was established, false if there was authentication 
     *         error with the connection.
     * @throws AuthenticationFailedException
     *                if authentication fails
     * @throws MessagingException
     *                for other failures
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
     * Close this service and terminate its physical connection.
     * The default implementation simply calls setConnected(false) and then
     * sends a CLOSED event to all registered ConnectionListeners.
     * Subclasses overriding this method should still ensure it is closed; they should
     * also ensure that it is called if the connection is closed automatically, for
     * for example in a finalizer.
     *
     *@throws MessagingException if there were errors closing; the connection is still closed
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
            return new IMAPRootFolder(this);
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
     * Return the root folders of the personal namespace belonging to the current user.
     *
     * The default implementation simply returns an array containing the folder returned by {@link #getDefaultFolder()}.
     * @return the root folders of the user's peronal namespaces
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] getPersonalNamespaces() throws MessagingException {
        IMAPNamespaceResponse namespaces = getNamespaces(); 
        
        // if nothing is returned, then use the API-defined default for this 
        if (namespaces.personalNamespaces.size() == 0) {
            return super.getPersonalNamespaces(); 
        }
        
        // convert the list into an array of Folders. 
        return getNamespaceFolders(namespaces.personalNamespaces); 
    }
    
    
    /**
     * Return the root folders of the personal namespaces belonging to the supplied user.
     *
     * The default implementation simply returns an empty array.
     *
     * @param user the user whose namespaces should be returned
     * @return the root folders of the given user's peronal namespaces
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] getUserNamespaces(String user) throws MessagingException {
        IMAPNamespaceResponse namespaces = getNamespaces(); 
        
        // if nothing is returned, then use the API-defined default for this 
        if (namespaces.otherUserNamespaces == null || namespaces.otherUserNamespaces.isEmpty()) {
            return super.getUserNamespaces(user); 
        }
        
        // convert the list into an array of Folders. 
        return getNamespaceFolders(namespaces.otherUserNamespaces); 
    }

    
    /**
     * Return the root folders of namespaces that are intended to be shared between users.
     *
     * The default implementation simply returns an empty array.
     * @return the root folders of all shared namespaces
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder[] getSharedNamespaces() throws MessagingException {
        IMAPNamespaceResponse namespaces = getNamespaces(); 
        
        // if nothing is returned, then use the API-defined default for this 
        if (namespaces.sharedNamespaces == null || namespaces.sharedNamespaces.isEmpty()) {
            return super.getSharedNamespaces(); 
        }
        
        // convert the list into an array of Folders. 
        return getNamespaceFolders(namespaces.sharedNamespaces); 
    }
    
    
    /**
     * Get the quotas for the specified root element.
     *
     * @param root   The root name for the quota information.
     *
     * @return An array of Quota objects defined for the root.
     * @throws MessagingException if the quotas cannot be retrieved
     */
    public Quota[] getQuota(String root) throws javax.mail.MessagingException {
        // get our private connection for access 
        IMAPConnection connection = getStoreConnection(); 
        try {
            // request the namespace information from the server 
            return connection.fetchQuota(root); 
        } finally {
            releaseStoreConnection(connection); 
        }
    }

    /**
     * Set a quota item.  The root contained in the Quota item identifies
     * the quota target.
     *
     * @param quota  The source quota item.
     * @throws MessagingException if the quota cannot be set
     */
    public void setQuota(Quota quota) throws javax.mail.MessagingException {
        // get our private connection for access 
        IMAPConnection connection = getStoreConnection(); 
        try {
            // request the namespace information from the server 
            connection.setQuota(quota); 
        } finally {
            releaseStoreConnection(connection); 
        }
    }

    /**
     * Verify that the server is in a connected state before 
     * performing operations that required that status. 
     * 
     * @exception MessagingException
     */
	private void checkConnectionStatus() throws MessagingException {
        // we just check the connection status with the superclass.  This 
        // tells us we've gotten a connection.  We don't want to do the 
        // complete connection checks that require pinging the server. 
		if (!super.isConnected()){
		    throw new MessagingException("Not connected ");
	    }
	}


    /**
     * Test to see if we're still connected.  This will ping the server
     * to see if we're still alive.
     *
     * @return true if we have a live, active culture, false otherwise.
     */
    public synchronized boolean isConnected() {
        // check if we're in a presumed connected state.  If not, we don't really have a connection
        // to check on.
        if (!super.isConnected()) {
            return false;
        }
        
        try {
            IMAPConnection connection = getStoreConnection(); 
            try {
                // check with the connecition to see if it's still alive. 
                // we use a zero timeout value to force it to check. 
                return connection.isAlive(0);
            } finally {
                releaseStoreConnection(connection); 
            }
        } catch (MessagingException e) {
            return false; 
        }
        
    }

    /**
     * Internal debug output routine.
     *
     * @param value  The string value to output.
     */
    void debugOut(String message) {
        debugStream.println("IMAPStore DEBUG: " + message);
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
     * Retrieve the server connection created by this store.
     *
     * @return The active connection object.
     */
    protected IMAPConnection getStoreConnection() throws MessagingException {
        return connectionPool.getStoreConnection(); 
    }
    
    protected void releaseStoreConnection(IMAPConnection connection) throws MessagingException {
        // This is a bit of a pain.  We need to delay processing of the 
        // unsolicited responses until after each user of the connection has 
        // finished processing the expected responses.  We need to do this because 
        // the unsolicited responses may include EXPUNGED messages.  The EXPUNGED 
        // messages will alter the message sequence numbers for the messages in the 
        // cache.  Processing the EXPUNGED messages too early will result in 
        // updates getting applied to the wrong message instances.  So, as a result, 
        // we delay that stage of the processing until all expected responses have 
        // been handled.  
        
        // process any pending messages before returning. 
        connection.processPendingResponses(); 
        // return this to the connectin pool 
        connectionPool.releaseStoreConnection(connection); 
    }
    
    synchronized IMAPConnection getFolderConnection(IMAPFolder folder) throws MessagingException {
        IMAPConnection connection = connectionPool.getFolderConnection(); 
        openFolders.add(folder);
        return connection; 
    }
    
    
    synchronized void releaseFolderConnection(IMAPFolder folder, IMAPConnection connection) throws MessagingException {
        openFolders.remove(folder); 
        // return this to the connectin pool 
        // NB:  It is assumed that the Folder has already triggered handling of 
        // unsolicited responses on this connection before returning it. 
        connectionPool.releaseFolderConnection(connection); 
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
                IMAPFolder folder = (IMAPFolder)folders.get(i);
                try {
                    folder.close(false);
                } catch (MessagingException e) {
                }
            }
        }
    }
    
    /**
     * Get the namespace information from the IMAP server.
     * 
     * @return An IMAPNamespaceResponse with the namespace information. 
     * @exception MessagingException
     */
    protected IMAPNamespaceResponse getNamespaces() throws MessagingException {
        // get our private connection for access 
        IMAPConnection connection = getStoreConnection(); 
        try {
            // request the namespace information from the server 
            return connection.getNamespaces(); 
        } finally {
            releaseStoreConnection(connection); 
        }
    }
    
    
    /**
     * Convert a List of IMAPNamespace definitions into an array of Folder 
     * instances. 
     * 
     * @param namespaces The namespace List
     * 
     * @return An array of the same size as the namespace list containing a Folder 
     *         instance for each defined namespace.
     * @exception MessagingException
     */
    protected Folder[] getNamespaceFolders(List namespaces) throws MessagingException {
        Folder[] folders = new Folder[namespaces.size()]; 
        
        // convert each of these to a Folder instance. 
        for (int i = 0; i < namespaces.size(); i++) {
            IMAPNamespace namespace = (IMAPNamespace)namespaces.get(i); 
            folders[i] = new IMAPNamespaceFolder(this, namespace); 
        }
        return folders; 
    }
    
    
    /**
     * Test if this connection has a given capability. 
     * 
     * @param capability The capability name.
     * 
     * @return true if this capability is in the list, false for a mismatch. 
     */
    public boolean hasCapability(String capability) {
        return connectionPool.hasCapability(capability); 
    }
    
    
    /**
     * Handle an unsolicited response from the server.  Most unsolicited responses 
     * are replies to specific commands sent to the server.  The remainder must 
     * be handled by the Store or the Folder using the connection.  These are 
     * critical to handle, as events such as expunged messages will alter the 
     * sequence numbers of the live messages.  We need to keep things in sync.
     * 
     * @param response The UntaggedResponse to process.
     * 
     * @return true if we handled this response and no further handling is required.  false
     *         means this one wasn't one of ours.
     */
    public boolean handleResponse(IMAPUntaggedResponse response) {
        // Some sort of ALERT response from the server?
        // we need to broadcast this to any of the listeners 
        if (response.isKeyword("ALERT")) {
            notifyStoreListeners(StoreEvent.ALERT, ((IMAPOkResponse)response).getMessage()); 
            return true; 
        }
        // potentially some sort of unsolicited OK notice.  This is also an event. 
        else if (response.isKeyword("OK")) {
            String message = ((IMAPOkResponse)response).getMessage(); 
            if (message.length() > 0) {
                notifyStoreListeners(StoreEvent.NOTICE, message); 
            }
            return true; 
        }
        // potentially some sort of unsolicited notice.  This is also an event. 
        else if (response.isKeyword("BAD") || response.isKeyword("NO")) {
            String message = ((IMAPServerStatusResponse)response).getMessage(); 
            if (message.length() > 0) {
                notifyStoreListeners(StoreEvent.NOTICE, message); 
            }
            return true; 
        }
        // this is a BYE response on our connection.  Folders should be handling the 
        // BYE events on their connections, so we should only be seeing this if 
        // it's on the store connection.  
        else if (response.isKeyword("BYE")) {
            // this is essentially a close event.  We need to clean everything up 
            try {
                close();                
            } catch (MessagingException e) {
            }
            return true; 
        }
        return false; 
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
    
    /**
     * Retrieve the protocol properties for the Store. 
     * 
     * @return The protocol properties bundle. 
     */
    ProtocolProperties getProperties() {
        return props; 
    }
}
