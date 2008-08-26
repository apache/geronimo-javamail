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

import java.util.List;     

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.MethodNotSupportedException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.event.ConnectionEvent;

import org.apache.geronimo.javamail.store.pop3.connection.POP3Connection; 
import org.apache.geronimo.javamail.store.pop3.connection.POP3StatusResponse; 

/**
 * The POP3 implementation of the javax.mail.Folder Note that only INBOX is
 * supported in POP3
 * <p>
 * <url>http://www.faqs.org/rfcs/rfc1939.html</url>
 * </p>
 * 
 * @see javax.mail.Folder
 * 
 * @version $Rev$ $Date$
 */
public class POP3Folder extends Folder {

    protected boolean isFolderOpen = false;

    protected int mode;

    protected int msgCount;

    private POP3Message[] messageCache; 
    // The fully qualified name of the folder.  For a POP3 folder, this is either "" for the root or 
    // "INPUT" for the in-basket.  It is possible to create other folders, but they will report that 
    // they don't exist. 
    protected String fullName;  
    // indicates whether this folder exists or not 
    protected boolean exists = false; 
    // indicates the type of folder this is. 
    protected int folderType; 
    
    /**
     * Create a new folder associate with a POP3 store instance.
     * 
     * @param store  The owning Store.
     * @param name   The name of the folder.  Note that POP3 stores only
     *               have 2 real folders, the root ("") and the in-basket
     *               ("INBOX").  It is possible to create other instances
     *               of Folder associated with the Store, but they will
     *               be non-functional.
     */
     public POP3Folder(POP3Store store, String name) {
        super(store);
        this.fullName = name; 
        // if this is the input folder, this exists 
        if (name.equalsIgnoreCase("INPUT")) {
            exists = true; 
        }
        // by default, we're holding messages. 
        folderType = Folder.HOLDS_MESSAGES; 
    }
    
    
    /**
     * Retrieve the folder name.  This is the simple folder
     * name at the its hiearchy level.  This can be invoked when the folder is closed.
     * 
     * @return The folder's name.
     */
	public String getName() {
        // the name and the full name are always the same
        return fullName; 
	}

    /**
     * Retrieve the folder's full name (including hierarchy information).
     * This can be invoked when the folder is closed.
     *
     * @return The full name value.
     */
	public String getFullName() {
        return fullName;
	}

    
    /**
     * Never return "this" as the parent folder. Somebody not familliar with
     * POP3 may do something like while(getParent() != null) or something
     * simmilar which will result in an infinte loop
     */
    public Folder getParent() throws MessagingException {
        // the default folder returns null.  We return the default 
        // folder 
        return store.getDefaultFolder(); 
    }

    /**
     * Indicate whether a folder exists.  Only the root 
     * folder and "INBOX" will ever return true. 
     * 
     * @return true for real POP3 folders, false for any other 
     *         instances that have been created.
     * @exception MessagingException
     */
    public boolean exists() throws MessagingException {
        // only one folder truely exists...this might be it.
        return exists; 
    }

    public Folder[] list(String pattern) throws MessagingException {
        throw new MethodNotSupportedException("Only INBOX is supported in POP3, no sub folders");
    }

    /**
     * No sub folders, hence there is no notion of a seperator.  This is always a null character. 
     */
    public char getSeparator() throws MessagingException {
        return '\0';
    }

    /**
     * There's no hierarchy in POP3, so the only type 
     * is HOLDS_MESSAGES (and only one of those exists).
     * 
     * @return Always returns HOLDS_MESSAGES. 
     * @exception MessagingException
     */
    public int getType() throws MessagingException {
        return folderType;      
    }

    /**
     * Always returns false as any creation operation must 
     * fail. 
     * 
     * @param type   The type of folder to create.  This is ignored.
     * 
     * @return Always returns false. 
     * @exception MessagingException
     */
    public boolean create(int type) throws MessagingException {
        return false; 
    }

    /**
     * No way to detect new messages, so always return false. 
     * 
     * @return Always returns false. 
     * @exception MessagingException
     */
    public boolean hasNewMessages() throws MessagingException {
        return false; 
    }

    public Folder getFolder(String name) throws MessagingException {
        throw new MethodNotSupportedException("Only INBOX is supported in POP3, no sub folders");
    }

    public boolean delete(boolean recurse) throws MessagingException {
        throw new MethodNotSupportedException("Only INBOX is supported in POP3 and INBOX cannot be deleted");
    }

    public boolean renameTo(Folder f) throws MessagingException {
        throw new MethodNotSupportedException("Only INBOX is supported in POP3 and INBOX cannot be renamed");
    }

    /**
     * @see javax.mail.Folder#open(int)
     */
    public void open(int mode) throws MessagingException {
        // Can only be performed on a closed folder
        checkClosed();

        // get a connection object 
        POP3Connection connection = getConnection(); 
        
        try {
            POP3StatusResponse res = connection.retrieveMailboxStatus();
            this.mode = mode;
            this.isFolderOpen = true;
            this.msgCount = res.getNumMessages();
            // JavaMail API has no method in Folder to expose the total
            // size (no of bytes) of the mail drop;

            // NB:  We use the actual message number to access the messages from 
            // the cache, which is origin 1.  Vectors are origin 0, so we have to subtract each time 
            // we access a messagge.  
            messageCache = new POP3Message[msgCount]; 
        } catch (Exception e) {
            throw new MessagingException("Unable to execute STAT command", e);
        }
        finally {
            // return the connection when finished 
            releaseConnection(connection); 
        }

        notifyConnectionListeners(ConnectionEvent.OPENED);
    }

    /**
     * Close a POP3 folder.
     * 
     * @param expunge The expunge flag (ignored for POP3).
     * 
     * @exception MessagingException
     */
    public void close(boolean expunge) throws MessagingException {
        // Can only be performed on an open folder
        checkOpen();

        // get a connection object 
        POP3Connection connection = getConnection(); 
        try {
            // we might need to reset the connection before we 
            // process deleted messages and send the QUIT.  The 
            // connection knows if we need to do this. 
            connection.reset(); 
            // clean up any messages marked for deletion 
            expungeDeletedMessages(connection); 
        } finally {
            // return the connection when finished 
            releaseConnection(connection); 
            // cleanup the the state even if exceptions occur when deleting the 
            // messages. 
            cleanupFolder(false); 
        }
    }
    
    /**
     * Mark any messages we've flagged as deleted from the 
     * POP3 server before closing. 
     * 
     * @exception MessagingException
     */
    protected void expungeDeletedMessages(POP3Connection connection) throws MessagingException {
        if (mode == READ_WRITE) {
            for (int i = 0; i < messageCache.length; i++) {
                POP3Message msg = messageCache[i]; 
                if (msg != null) {
                    // if the deleted flag is set, go delete this 
                    // message. NB:  We adjust the index back to an 
                    // origin 1 value 
                    if (msg.isSet(Flags.Flag.DELETED)) {
                        try {
                            connection.deleteMessage(i + 1); 
                        } catch (MessagingException e) {
                            throw new MessagingException("Exception deleting message number " + (i + 1), e); 
                        }
                    }
                }
            }
        }
    }
    
    
    /**
     * Do folder cleanup.  This is used both for normal
     * close operations, and adnormal closes where the
     * server has sent us a BYE message.
     * 
     * @param expunge Indicates whether open messages should be expunged.
     * @param disconnected
     *                The disconnected flag.  If true, the server has cut
     *                us off, which means our connection can not be returned
     *                to the connection pool.
     * 
     * @exception MessagingException
     */
    protected void cleanupFolder(boolean disconnected) throws MessagingException {
        messageCache = null;
        isFolderOpen = false;
		notifyConnectionListeners(ConnectionEvent.CLOSED);
    }
    
    
    /**
     * Obtain a connection object for a Message attached to this Folder.  This 
     * will be the Folder's connection, which is only available if the Folder 
     * is currently open.
     * 
     * @return The connection object for the Message instance to use. 
     * @exception MessagingException
     */
    synchronized POP3Connection getMessageConnection() throws MessagingException {
        // we always get one from the store.  If we're fully single threaded, then 
        // we can get away with just a single one. 
        return getConnection(); 
    }
    
    
    /**
     * Release the connection object back to the Folder instance.  
     * 
     * @param connection The connection being released.
     * 
     * @exception MessagingException
     */
    void releaseMessageConnection(POP3Connection connection) throws MessagingException {
        // give this back to the store 
        releaseConnection(connection); 
    }

    public boolean isOpen() {
        // if we're not open, we're not open 
        if (!isFolderOpen) {
            return false; 
        }
        
        try {
            // we might be open, but the Store has been closed.  In which case, we're not any more
            // closing also changes the isFolderOpen flag. 
            if (!((POP3Store)store).isConnected()) {
                close(false); 
            }
        } catch (MessagingException e) {
        }
        return isFolderOpen;
    }

    public Flags getPermanentFlags() {
        // unfortunately doesn't have a throws clause for this method
        // throw new MethodNotSupportedException("POP3 doesn't support permanent
        // flags");

        // Better than returning null, save the extra condition from a user to
        // check for null
        // and avoids a NullPointerException for the careless.
        return new Flags();
    }

    /**
     * Get the folder message count.
     * 
     * @return The number of messages in the folder.
     * @exception MessagingException
     */
    public int getMessageCount() throws MessagingException {
        // NB: returns -1 if the folder isn't open. 
        return msgCount;
    }

    /**
     * Checks wether the message is in cache, if not will create a new message
     * object and return it.
     * 
     * @see javax.mail.Folder#getMessage(int)
     */
    public Message getMessage(int msgNum) throws MessagingException {
        // Can only be performed on an Open folder
        checkOpen();
        if (msgNum < 1 || msgNum > getMessageCount()) {
            throw new MessagingException("Invalid Message number");
        }

        Message msg = messageCache[msgNum - 1];
        if (msg == null) {
            msg = new POP3Message(this, msgNum); 
            messageCache[msgNum - 1] = (POP3Message)msg; 
        }

        return msg;
    }

    public void appendMessages(Message[] msgs) throws MessagingException {
        throw new MethodNotSupportedException("Message appending is not supported in POP3");

    }

    public Message[] expunge() throws MessagingException {
        throw new MethodNotSupportedException("Expunge is not supported in POP3");
    }

    public int getMode() throws IllegalStateException {
        // Can only be performed on an Open folder
        checkOpen();
        return mode;
    }

    /**
     * @see javax.mail.Folder#fetch(javax.mail.Message[],
     *      javax.mail.FetchProfile)
     * 
     * The JavaMail API recommends that this method be overrident to provide a
     * meaningfull implementation.
     */
    public synchronized void fetch(Message[] msgs, FetchProfile fp) throws MessagingException {
        // Can only be performed on an Open folder
        checkOpen();
        for (int i = 0; i < msgs.length; i++) {
            Message msg = msgs[i];
            
            if (fp.contains(FetchProfile.Item.ENVELOPE)) {
                // fetching the size and the subject will force all of the 
                // envelope information to load 
                msg.getHeader("Subject"); 
                msg.getSize(); 
            }
            if (fp.contains(FetchProfile.Item.CONTENT_INFO)) {
                // force the content to load...this also fetches the header information. 
                // C'est la vie. 
                ((POP3Message)msg).loadContent(); 
                msg.getSize(); 
            }
            // force flag loading for this message 
            if (fp.contains(FetchProfile.Item.FLAGS)) {
                msg.getFlags(); 
            }
            
            if (fp.getHeaderNames().length > 0) {
                // loading any header loads all headers, so just grab the header set. 
                msg.getHeader("Subject"); 
            }
        }
    }
    
    /**
     * Retrieve the UID for a given message.
     * 
     * @param msg    The message of interest.
     * 
     * @return The String UID value for this message.
     * @exception MessagingException
     */
    public synchronized String getUID(Message msg) throws MessagingException {
        checkOpen(); 
        // the Message knows how to do this 
        return ((POP3Message)msg).getUID(); 
    }
    

    /**
     * Below is a list of covinience methods that avoid repeated checking for a
     * value and throwing an exception
     */

    /** Ensure the folder is open */
    private void checkOpen() throws IllegalStateException {
        if (!isFolderOpen) {
            throw new IllegalStateException("Folder is not Open");
        }
    }

    /** Ensure the folder is not open */
    private void checkClosed() throws IllegalStateException {
        if (isFolderOpen) {
            throw new IllegalStateException("Folder is Open");
        }
    }

    /**
     * @see javax.mail.Folder#notifyMessageChangedListeners(int,
     *      javax.mail.Message)
     * 
     * this method is protected and cannot be used outside of Folder, therefore
     * had to explicitly expose it via a method in POP3Folder, so that
     * POP3Message has access to it
     * 
     * Bad design on the part of the Java Mail API.
     */
    public void notifyMessageChangedListeners(int type, Message m) {
        super.notifyMessageChangedListeners(type, m);
    }

    
    /**
     * Retrieve the connection attached to this folder.  Throws an
     * exception if we don't have an active connection.
     *
     * @return The current connection object.
     * @exception MessagingException
     */
    protected synchronized POP3Connection getConnection() throws MessagingException {
        // request a connection from the central store. 
        return ((POP3Store)store).getFolderConnection(this); 
    }
    
    
    /**
     * Release our connection back to the Store.
     * 
     * @param connection The connection to release.
     * 
     * @exception MessagingException
     */
    protected void releaseConnection(POP3Connection connection) throws MessagingException {
        // we need to release the connection to the Store once we're finished with it 
        ((POP3Store)store).releaseFolderConnection(this, connection); 
    }
}
