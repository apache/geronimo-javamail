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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.mail.MessagingException; 
import javax.mail.Session;
import javax.mail.Store;

import javax.mail.StoreClosedException;

import org.apache.geronimo.javamail.store.pop3.POP3Store; 
import org.apache.geronimo.javamail.util.ProtocolProperties; 

public class POP3ConnectionPool {

    protected static final String MAIL_PORT = "port";
    
    protected static final String MAIL_SASL_REALM = "sasl.realm"; 
    protected static final String MAIL_AUTHORIZATIONID = "sasl.authorizationid"; 

    protected static final String DEFAULT_MAIL_HOST = "localhost";

    // Our hosting Store instance
    protected POP3Store store;
    // our Protocol abstraction 
    protected ProtocolProperties props; 
    // POP3 is not nearly as multi-threaded as IMAP.  We really just have a single folder, 
    // plus the Store, but the Store doesn't really talk to the server very much.  We only
    // hold one connection available, and on the off chance there is a situation where 
    // we need to create a new one, we'll authenticate on demand.  The one case where 
    // I know this might be an issue is a folder checking back with the Store to see it if
    // it is still connected.  
    protected POP3Connection availableConnection;      
    
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
    // the authorization id.  
    protected String authid; 
    // Turned on when the store is closed for business. 
    protected boolean closed = false; 

    /**
     * Create a connection pool associated with a give POP3Store instance.  The
     * connection pool manages handing out connections for both the Store and
     * Folder and Message usage.
     * 
     * @param store  The Store we're creating the pool for.
     * @param props  The protocol properties abstraction we use.
     */
    public POP3ConnectionPool(POP3Store store, ProtocolProperties props) {
        this.store = store;
        this.props = props; 
    }


    /**
     * Manage the initial connection to the POP3 server.  This is the first 
     * point where we obtain the information needed to make an actual server 
     * connection.  Like the Store protocolConnect method, we return false 
     * if there's any sort of authentication difficulties. 
     * 
     * @param host     The host of the mail server.
     * @param port     The mail server connection port.
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
        availableConnection = createPoolConnection(); 
        if (availableConnection == null) {
            return false; 
        }
        // we're connected, authenticated, and ready to go. 
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
    protected POP3Connection createPoolConnection() throws MessagingException {
        POP3Connection connection = new POP3Connection(props);
        if (!connection.protocolConnect(host, port, authid, realm, username, password)) {
            // we only add live connections to the pool.  Sever the connections and 
            // allow it to go free. 
            connection.closeServerConnection(); 
            return null; 
        }
        // just return this connection 
        return connection; 
    }


    /**
     * Get a connection from the pool.  We try to retrieve a live
     * connection, but we test the connection's liveness before
     * returning one.  If we don't have a viable connection in
     * the pool, we'll create a new one.  The returned connection
     * will be in the authenticated state already.
     *
     * @return A POP3Connection object that is connected to the server.
     */
    public synchronized POP3Connection getConnection() throws MessagingException {
        // if we have an available one (common when opening the INBOX), just return it 
        POP3Connection connection = availableConnection; 
        
        if (connection != null) {
            availableConnection = null; 
            return connection; 
        }
        // we need an additional connection...rare, but it can happen if we've closed the INBOX folder. 
        return createPoolConnection(); 
    }
    
    
    /**
     * Return a connection to the connection pool.
     * 
     * @param connection The connection getting returned.
     * 
     * @exception MessagingException
     */
    public synchronized void releaseConnection(POP3Connection connection) throws MessagingException
    {
        // we're generally only called if the store needed to talk to the server and 
        // then returned the connection to the pool.  So it's pretty likely that we'll just cache this
        if (availableConnection == null) {
            availableConnection = connection; 
        }
        else {
            // got too many connections created...not sure how, but get rid of this one. 
            connection.close(); 
        }
    }
    
    
    /**
     * Close the entire connection pool. 
     * 
     * @exception MessagingException
     */
    public synchronized void close() throws MessagingException {
        // we'll on have the single connection in reserver 
        if (availableConnection != null) {
            availableConnection.close(); 
            availableConnection = null; 
        }
        // turn out the lights, hang the closed sign on the wall. 
        closed = true; 
    }
}

