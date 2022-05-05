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

package org.apache.geronimo.mail.store.imap.connection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.mail.MessagingException; 
import jakarta.mail.Session;
import jakarta.mail.Store;

import jakarta.mail.StoreClosedException;

import org.apache.geronimo.mail.store.imap.IMAPStore;
import org.apache.geronimo.mail.util.ProtocolProperties;

public class IMAPConnectionPool {

    protected static final String MAIL_PORT = "port";
    protected static final String MAIL_POOL_SIZE = "connectionpoolsize";
    protected static final String MAIL_POOL_TIMEOUT = "connectionpooltimeout";
    protected static final String MAIL_SEPARATE_STORE_CONNECTION = "separatestoreconnection";
    
    protected static final String MAIL_SASL_REALM = "sasl.realm"; 
    protected static final String MAIL_AUTHORIZATIONID = "sasl.authorizationid"; 

    // 45 seconds, by default.
    protected static final int DEFAULT_POOL_TIMEOUT = 45000;
    protected static final String DEFAULT_MAIL_HOST = "localhost";
    
    protected static final int MAX_CONNECTION_RETRIES = 3; 
    protected static final int MAX_POOL_WAIT = 500; 


    // Our hosting Store instance
    protected IMAPStore store;
    // our Protocol abstraction 
    protected ProtocolProperties props; 
    // our list of created connections
    protected List poolConnections = new ArrayList();
    // our list of available connections 
    protected List availableConnections = new ArrayList();
    
    // the dedicated Store connection (if we're configured that way)
    protected IMAPConnection storeConnection = null;
    
    // our dedicated Store connection attribute
    protected boolean dedicatedStoreConnection;
    // the size of our connection pool (by default, we only keep a single connection in the pool)
    protected int poolSize = 1;
    // the connection timeout property
    protected long poolTimeout;
    // our debug flag
    protected boolean debug;

    // the target host
    protected String host;
    // the target server port.
    protected int port;
    // the username we connect with
    protected String username;
    // the authentication password.
    protected String password;
    // the SASL realm name 
    protected String realm; 
    // the authorization id.  With IMAP, it's possible to 
    // log on with another's authorization. 
    protected String authid; 
    // Turned on when the store is closed for business. 
    protected boolean closed = false; 
    // the connection capabilities map
    protected Map capabilities; 

    /**
     * Create a connection pool associated with a give IMAPStore instance.  The
     * connection pool manages handing out connections for both the Store and
     * Folder and Message usage.
     * 
     * Depending on the session properties, the Store may be given a dedicated
     * connection, or will share connections with the Folders.  Connections may
     * be requested from either the Store or Folders.  Messages must request
     * their connections from their hosting Folder, and only one connection is
     * allowed per folder.
     * 
     * @param store  The Store we're creating the pool for.
     * @param props  The property bundle that defines protocol properties
     *               that alter the connection behavior.
     */
    public IMAPConnectionPool(IMAPStore store, ProtocolProperties props) {
        this.store = store;
        this.props = props; 

        // get the pool size.  By default, we just use a single connection that's 
        // shared among Store and all of the Folders.  Since most apps that use 
        // mail tend to be single-threaded, this generally poses no great hardship.
        poolSize = props.getIntProperty(MAIL_POOL_SIZE, 1);
        // get the timeout property.  Default is 45 seconds.
        poolTimeout = props.getIntProperty(MAIL_POOL_TIMEOUT, DEFAULT_POOL_TIMEOUT);
        // we can create a dedicated connection over and above the pool set that's 
        // reserved for the Store instance to use. 
        dedicatedStoreConnection = props.getBooleanProperty(MAIL_SEPARATE_STORE_CONNECTION, false);
        // if we have a dedicated pool connection, we allocated that from the pool.  Add this to 
        // the total pool size so we don't find ourselves stuck if the pool size is 1. 
        if (dedicatedStoreConnection) {
            poolSize++; 
        }
    }


    /**
     * Manage the initial connection to the IMAP server.  This is the first 
     * point where we obtain the information needed to make an actual server 
     * connection.  Like the Store protocolConnect method, we return false 
     * if there's any sort of authentication difficulties. 
     * 
     * @param host     The host of the IMAP server.
     * @param port     The IMAP server connection port.
     * @param user     The connection user name.
     * @param password The connection password.
     * 
     * @return True if we were able to connect and authenticate correctly. 
     * @exception MessagingException
     */
    public synchronized boolean protocolConnect(String host, int port, String username, String password) throws MessagingException {
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
        
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        
        // make sure we have the realm information 
        realm = props.getProperty(MAIL_SASL_REALM); 
        // get an authzid value, if we have one.  The default is to use the username.
        authid = props.getProperty(MAIL_AUTHORIZATIONID, username);

        // go create a connection and just add it to the pool.  If there is an authenticaton error, 
        // return the connect failure, and we may end up trying again. 
        IMAPConnection connection = createPoolConnection(); 
        if (connection == null) {
            return false; 
        }
        // save the capabilities map from the first connection. 
        capabilities = connection.getCapabilities(); 
        // if we're using a dedicated store connection, remove this from the pool and
        // reserve it for the store.
        if (dedicatedStoreConnection)  
        {  
            storeConnection = connection;
            // make sure this is hooked up to the store. 
            connection.addResponseHandler(store); 
        }
        else {
            // just put this back in the pool.  It's ready for anybody to use now. 
            synchronized(this) {
                availableConnections.add(connection); 
            }
        }
        // we're connection, authenticated, and ready to go. 
        return true; 
    }

    /**
     * Creates an authenticated pool connection and adds it to
     * the connection pool.  If there is an existing connection
     * already in the pool, this returns without creating a new
     * connection.
     *
     * @exception MessagingException
     */
    protected IMAPConnection createPoolConnection() throws MessagingException {
        IMAPConnection connection = new IMAPConnection(props, this);
        if (!connection.protocolConnect(host, port, authid, realm, username, password)) {
            // we only add live connections to the pool.  Sever the connections and 
            // allow it to go free. 
            connection.closeServerConnection(); 
            return null; 
        }
        
        // add this to the master list.  We do NOT add this to the 
        // available queue because we're handing this out. 
        synchronized(this) {
            // uh oh, we closed up shop while we were doing this...clean it up a 
            // get out of here 
            if (closed) {
                connection.close(); 
                throw new StoreClosedException(store, "No Store connections available"); 
            }
            
            poolConnections.add(connection);
        }
        // return that connection 
        return connection; 
    }


    /**
     * Get a connection from the pool.  We try to retrieve a live
     * connection, but we test the connection's liveness before
     * returning one.  If we don't have a viable connection in
     * the pool, we'll create a new one.  The returned connection
     * will be in the authenticated state already.
     *
     * @return An IMAPConnection object that is connected to the server.
     */
    protected IMAPConnection getConnection() throws MessagingException {
        int retryCount = 0; 
        
        // To keep us from falling into a futile failure loop, we'll only allow 
        // a set number of connection failures. 
        while (retryCount < MAX_CONNECTION_RETRIES) {
            // first try for an already created one.  If this returns 
            // null, then we'll probably have to make a new one. 
            IMAPConnection connection = getPoolConnection(); 
            // cool, we got one, the hard part is done.  
            if (connection != null) {
                return connection; 
            }
            // ok, create a new one.  This *should* work, but the server might 
            // have gone down, or other problem may occur. If we have a problem, 
            // retry the entire process...but only for a bit.  No sense 
            // being stubborn about it. 
            connection = createPoolConnection(); 
            if (connection != null) {
                return connection; 
            }
            // step the retry count 
            retryCount++; 
        }
        
        throw new MessagingException("Unable to get connection to IMAP server"); 
    }
    
    /**
     * Obtain a connection from the existing connection pool.  If none are 
     * available, and we've reached the connection pool limit, we'll wait for 
     * some other thread to return one.  It generally doesn't take too long, as 
     * they're usually only held for the time required to execute a single 
     * command.   If we're not at the pool limit, return null, which will signal 
     * the caller to go ahead and create a new connection outside of the 
     * lock. 
     * 
     * @return Either an active connection instance, or null if the caller should go 
     *         ahead and try to create a new connection.
     * @exception MessagingException
     */
    protected synchronized IMAPConnection getPoolConnection() throws MessagingException {
        // if the pool is closed, we can't process this 
        if (closed) {
            throw new StoreClosedException(store, "No Store connections available"); 
        }
        
        // we'll retry this a few times if the connection pool is full, but 
        // after that, we'll just create a new connection. 
        for (int i = 0; i < MAX_CONNECTION_RETRIES; i++) {
            Iterator it = availableConnections.iterator(); 
            while (it.hasNext()) {
                IMAPConnection connection = (IMAPConnection)it.next(); 
                // live or dead, we're going to remove this from the 
                // available list. 
                it.remove(); 
                if (connection.isAlive(poolTimeout)) {
                    // return the connection to the requestor 
                    return connection; 
                }
                else {
                    // remove this from the pool...it's toast. 
                    poolConnections.remove(connection); 
                    // make sure this cleans up after itself. 
                    connection.closeServerConnection(); 
                }
            }

            // we've not found something usable in the pool.  Now see if 
            // we're allowed to add another connection, or must just wait for 
            // someone else to return one. 

            if (poolConnections.size() >= poolSize) {
                // check to see if we've been told to shutdown before waiting
                if (closed) {
                    throw new StoreClosedException(store, "No Store connections available"); 
                }
                // we need to wait for somebody to return a connection 
                // once woken up, we'll spin around and try to snag one from 
                // the pool again.
                try {
                    wait(MAX_POOL_WAIT);
                } catch (InterruptedException e) {
                }
                
                // check to see if we've been told to shutdown while we waited
                if (closed) {
                    throw new StoreClosedException(store, "No Store connections available"); 
                }
            }
            else {
                // exit out and create a new connection.  Since 
                // we're going to be outside the synchronized block, it's possible 
                // we'll go over our pool limit.  We'll take care of that when connections start 
                // getting returned. 
                return null; 
            }
        }
        // we've hit the maximum number of retries...just create a new connection. 
        return null; 
    }
    
    /**
     * Return a connection to the connection pool.
     * 
     * @param connection The connection getting returned.
     * 
     * @exception MessagingException
     */
    protected void returnPoolConnection(IMAPConnection connection) throws MessagingException
    {
        synchronized(this) {
            // If we're still within the bounds of our connection pool, 
            // just add this to the active list and send out a notification 
            // in case somebody else is waiting for the connection. 
            if (availableConnections.size() < poolSize) {
                availableConnections.add(connection); 
                notify(); 
                return; 
            }
            // remove this from the connection pool...we have too many. 
            poolConnections.remove(connection); 
        }
        // the additional cleanup occurs outside the synchronized block 
        connection.close(); 
    }
    
    /**
     * Release a closed connection.
     * 
     * @param connection The connection getting released.
     * 
     * @exception MessagingException
     */
    protected void releasePoolConnection(IMAPConnection connection) throws MessagingException
    {
        synchronized(this) {
            // remove this from the connection pool...it's no longer usable. 
            poolConnections.remove(connection); 
        }
        // the additional cleanup occurs outside the synchronized block 
        connection.close(); 
    }


    /**
     * Get a connection for the Store.  This will be either a
     * dedicated connection object, or one from the pool, depending
     * on the mail.imap.separatestoreconnection property.
     *
     * @return An authenticated connection object.
     */
    public synchronized IMAPConnection getStoreConnection() throws MessagingException {  
        if (closed) {
            throw new StoreClosedException(store, "No Store connections available"); 
        }
        // if we have a dedicated connection created, return it.
        if (storeConnection != null) {
            return storeConnection;
        }
        else {
            IMAPConnection connection = getConnection();
            // add the store as a response handler while it has it. 
            connection.addResponseHandler(store); 
            return connection; 
        }
    }


    /**
     * Return the Store connection to the connection pool.  If we have a dedicated
     * store connection, this is simple.  Otherwise, the connection goes back 
     * into the general connection pool.
     * 
     * @param connection The connection getting returned.
     */
    public synchronized void releaseStoreConnection(IMAPConnection connection) throws MessagingException {
        // have a server disconnect situation?
        if (connection.isClosed()) {
            // we no longer have a dedicated store connection.  
            // we need to return to the pool from now on. 
            storeConnection = null; 
            // throw this away. 
            releasePoolConnection(connection); 
        }
        else {
            // if we have a dedicated connection, nothing to do really.  Otherwise, 
            // return this connection to the pool. 
            if (storeConnection == null) {
                // unhook the store from the connection. 
                connection.removeResponseHandler(store); 
                returnPoolConnection(connection); 
            }
        }
    }


    /**
     * Get a connection for Folder.  
     *
     * @return An authenticated connection object.
     */
    public IMAPConnection getFolderConnection() throws MessagingException {  
        // just get a connection from the pool 
        return getConnection(); 
    }


    /**
     * Return a Folder connection to the connection pool.  
     * 
     * @param connection The connection getting returned.
     */
    public void releaseFolderConnection(IMAPConnection connection) throws MessagingException {
        // potentially, the server may have decided to shut us down.  
        // In that case, the connection is no longer usable, so we need 
        // to remove it from the list of available ones. 
        if (!connection.isClosed()) {
            // back into the pool with yee, matey....arrggghhh
            returnPoolConnection(connection); 
        }
        else {
            // can't return this one to the pool.  It's been stomped on 
            releasePoolConnection(connection); 
        }
    }
    
    
    /**
     * Close the entire connection pool. 
     * 
     * @exception MessagingException
     */
    public synchronized void close() throws MessagingException {
        // first close each of the connections.  This also closes the 
        // store connection. 
        for (int i = 0; i < poolConnections.size(); i++) {
            IMAPConnection connection = (IMAPConnection)poolConnections.get(i);
            connection.close(); 
        }
        // clear the pool 
        poolConnections.clear(); 
        availableConnections.clear(); 
        storeConnection = null; 
        // turn out the lights, hang the closed sign on the wall. 
        closed = true; 
    }


    /**
     * Flush any connections from the pool that have not been used
     * for at least the connection pool timeout interval.
     */
    protected synchronized void closeStaleConnections() {
        Iterator i = poolConnections.iterator();

        while (i.hasNext()) {
            IMAPConnection connection = (IMAPConnection)i.next();
            // if this connection is a stale one, remove it from the pool
            // and close it out.
            if (connection.isStale(poolTimeout)) {
                i.remove();
                try {
                    connection.close();
                } catch (MessagingException e) {
                    // ignored.  we're just closing connections that are probably timed out anyway, so errors
                    // on those shouldn't have an effect on the real operation we're dealing with.
                }
            }
        }
    }


    /**
     * Return a connection back to the connection pool.  If we're not
     * over our limit, the connection is kept around.  Otherwise, it's
     * given a nice burial.
     *
     * @param connection The returned connection.
     */
    protected synchronized void releaseConnection(IMAPConnection connection) {
        // before adding this to the pool, close any stale connections we may
        // have.  The connection we're adding is quite likely to be a fresh one,
        // so we should cache that one if we can.
        closeStaleConnections();
        // still over the limit?
        if (poolConnections.size() + 1 > poolSize) {
            try {
                // close this out and forget we ever saw it.
                connection.close();
            } catch (MessagingException e) {
                // ignore....this is a non-critical problem if this fails now.
            }
        }
        else {
            // listen to alerts on this connection, and put it back in the pool.
            poolConnections.add(connection);
        }
    }

    /**
     * Cleanup time.  Sever and cleanup all of the pool connection
     * objects, including the special Store connection, if we have one.
     */
    protected synchronized void freeAllConnections() {
        for (int i = 0; i < poolConnections.size(); i++) {
            IMAPConnection connection = (IMAPConnection)poolConnections.get(i);
            try {
                // close this out and forget we ever saw it.
                connection.close();
            } catch (MessagingException e) {
                // ignore....this is a non-critical problem if this fails now.
            }
        }
        // everybody, out of the pool!
        poolConnections.clear();

        // don't forget the special store connection, if we have one.
        if (storeConnection != null) {
            try {
                // close this out and forget we ever saw it.
                storeConnection.close();
            } catch (MessagingException e) {
                // ignore....this is a non-critical problem if this fails now.
            }
            storeConnection = null;
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
        if (capabilities == null) {
            return false; 
        }
        return capabilities.containsKey(capability); 
    }
}


