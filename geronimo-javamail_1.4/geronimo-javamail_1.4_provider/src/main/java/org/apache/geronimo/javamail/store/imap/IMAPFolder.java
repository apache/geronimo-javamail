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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Vector;

import javax.mail.*;
import javax.mail.event.ConnectionEvent;
import javax.mail.event.FolderEvent;
import javax.mail.event.MessageChangedEvent;
import javax.mail.search.FlagTerm;
import javax.mail.search.SearchTerm;

import org.apache.geronimo.javamail.store.imap.connection.IMAPConnection;
import org.apache.geronimo.javamail.store.imap.connection.IMAPFetchDataItem;
import org.apache.geronimo.javamail.store.imap.connection.IMAPFetchResponse;
import org.apache.geronimo.javamail.store.imap.connection.IMAPFlags;
import org.apache.geronimo.javamail.store.imap.connection.IMAPListResponse;
import org.apache.geronimo.javamail.store.imap.connection.IMAPMailboxStatus;
import org.apache.geronimo.javamail.store.imap.connection.IMAPSizeResponse;
import org.apache.geronimo.javamail.store.imap.connection.IMAPUid;
import org.apache.geronimo.javamail.store.imap.connection.IMAPUntaggedResponse;
import org.apache.geronimo.javamail.store.imap.connection.IMAPUntaggedResponseHandler;

/**
 * The base IMAP implementation of the javax.mail.Folder
 * This is a base class for both the Root IMAP server and each IMAP group folder.
 * @see javax.mail.Folder
 *
 * @version $Rev$
 */
public class IMAPFolder extends Folder implements UIDFolder, IMAPUntaggedResponseHandler {

    /**
     * Special profile item used for fetching SIZE and HEADER information.
     * These items are extensions that Sun has added to their IMAPFolder immplementation.
     * We're supporting the same set.
     */
    public static class FetchProfileItem extends FetchProfile.Item {
        public static final FetchProfileItem HEADERS = new FetchProfileItem("HEADERS");
        public static final FetchProfileItem SIZE = new FetchProfileItem("SIZE");

        protected FetchProfileItem(String name) {
            super(name);
        }
    }

    // marker that we don't know the separator yet for this folder.
    // This occurs when we obtain a folder reference from the
    // default folder.  At that point, we've not queried the
    // server for specifics yet.
    static final protected char UNDETERMINED = 0;

    // our attached session
    protected Session session;
    // retrieved messages, mapped by sequence number.
    protected Map messageCache;
    // mappings of UIDs to retrieved messages.
    protected Map uidCache;

    // the separator the server indicates is used as the hierarchy separator
    protected char separator;
    // the "full" name of the folder.  This is the fully qualified path name for the folder returned by
    // the IMAP server.  Elements of the hierarchy are delimited by "separator" characters.
    protected String fullname;
    // the name of this folder.  The is the last element of the fully qualified name.
    protected String name;
    // the folder open state
	protected boolean folderOpen = false;
    // the type information on what the folder can hold
    protected int folderType;
    // the subscription status
    protected boolean subscribed = false;

    // the message identifier ticker, used to assign message numbers.
    protected int nextMessageID = 1;
    // the current count of messages in our cache.
    protected int maxSequenceNumber = 0;
    // the reported count of new messages (updated as a result of untagged message resposes)
    protected int recentMessages = -1;
    // the reported count of unseen messages
    protected int unseenMessages = 0;
    // the uidValidity value reported back from the server
    protected long uidValidity = 0;
    // the uidNext value reported back from the server
    protected long uidNext = 0;
    // the persistent flags we save in the store
    protected Flags permanentFlags;
    // the settable flags the server reports back to us
    protected Flags availableFlags;
    // Our cached status information.  We will only hold this for the timeout interval.
    protected IMAPMailboxStatus cachedStatus;
    // Folder information retrieved from the server.  Good info here indicates the
    // folder exists.
    protected IMAPListResponse listInfo;
    // the configured status cache timeout value.
    protected long statusCacheTimeout;
    // the last time we took a status snap shot.
    protected long lastStatusTimeStamp;
    // Our current connection.  We get one of these when opened, and release it when closed.
    // We do this because for any folder (and message) operations, the folder must be selected on
    // the connection.
    // Note, however, that there are operations which will require us to borrow a connection
    // temporarily because we need to touch the server when the folder is not open.  In those
    // cases, we grab a connection, then immediately return it to the pool.
    protected IMAPConnection currentConnection;



    /**
     * Super class constructor the base IMAPFolder class.
     *
     * @param store     The javamail store this folder is attached to.
     * @param fullname  The fully qualified name of this folder.
     * @param separator The separtor character used to delimit the different
     *                  levels of the folder hierarchy.  This is used to
     *                  decompose the full name into smaller parts and
     *                  create the names of subfolders.
     */
	protected IMAPFolder(IMAPStore store, String fullname, char separator) {
		super(store);
		this.session = store.getSession();
        this.fullname = fullname;
        this.separator = separator;
        // get the status timeout value from the folder.
        statusCacheTimeout = store.statusCacheTimeout;
	}

    /**
     * Retrieve the folder name.  This is the simple folder
     * name at the its hiearchy level.  This can be invoked when the folder is closed.
     *
     * @return The folder's name.
     */
	public String getName() {
        // At the time we create the folder, we might not know the separator character yet.
        // Because of this we need to delay creating the name element until
        // it's required.
        if (name == null) {
            // extract the name from the full name
            int lastLevel = -1;
            try {
                lastLevel = fullname.lastIndexOf(getSeparator());
            } catch (MessagingException e) {
                // not likely to occur, but the link could go down before we
                // get this.  Just assume a failure to locate the character
                // occurred.
            }
            if (lastLevel == -1) {
                name = fullname;
            }
            else {
                name = fullname.substring(lastLevel + 1);
            }
        }
        return name;
	}

    /**
     * Retrieve the folder's full name (including hierarchy information).
     * This can be invoked when the folder is closed.
     *
     * @return The full name value.
     */
	public String getFullName() {
        return fullname;
	}



    /**
     * Return the parent for this folder; if the folder is at the root of a heirarchy
     * this returns null.
     * This can be invoked when the folder is closed.
     *
     * @return this folder's parent
     * @throws MessagingException
     */
	public Folder getParent() throws MessagingException {
        // NB:  We need to use the method form because the separator
        // might not have been retrieved from the server yet.
        char separator = getSeparator();
        // we don't hold a reference to the parent folder, as that would pin the instance in memory
        // as long as any any leaf item in the hierarchy is still open.
        int lastLevel = fullname.lastIndexOf(separator);
        // no parent folder?  Get the root one from the Store.
        if (lastLevel == -1) {
            return ((IMAPStore)store).getDefaultFolder();
        }
        else {
            // create a folder for the parent.
            return new IMAPFolder((IMAPStore)store, fullname.substring(0, lastLevel), separator);
        }
	}


    /**
     * Check to see if this folder physically exists in the store.
     * This can be invoked when the folder is closed.
     *
     * @return true if the folder really exists
     * @throws MessagingException if there was a problem accessing the store
     */
    public synchronized boolean exists() throws MessagingException {
        IMAPConnection connection = getConnection();
        try {
            return checkExistance(connection);
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Internal routine for checking existance using an
     * already obtained connection.  Used for situations
     * where the list information needs updating but
     * we'd end up acquiring a new connection because
     * the folder isn't open yet.
     *
     * @param connection The connection to use.
     *
     * @return true if the folder exists, false for non-existence.
     * @exception MessagingException
     */
    private boolean checkExistance(IMAPConnection connection) throws MessagingException {
        // get the list response for this folder.
        List responses = connection.list("", fullname);
        // NB, this grabs the latest information and updates
        // the type information also.  Note also that we need to
        // use the mailbox name, not the full name.  This is so
        // the namespace folders will return the correct response.
        listInfo = findListResponse(responses, getMailBoxName());

        if (listInfo == null) {
            return false;
        }

        // update the type information from the status.
        folderType = 0;
        if (!listInfo.noinferiors) {
            folderType |= HOLDS_FOLDERS;
        }
        if (!listInfo.noselect) {
            folderType |= HOLDS_MESSAGES;
        }

        // also update the separator information.  This will allow
        // use to skip a call later
        separator = listInfo.separator;
        // this can be omitted in the response, so assume a default
        if (separator == '\0') {
            separator = '/';
        }

        // updated ok, so it must be there.
        return true;
    }



    /**
     * Return a list of folders from this Folder's namespace that match the supplied pattern.
     * Patterns may contain the following wildcards:
     * <ul><li>'%' which matches any characater except hierarchy delimiters</li>
     * <li>'*' which matches any character including hierarchy delimiters</li>
     * </ul>
     * This can be invoked when the folder is closed.
     *
     * @param pattern the pattern to search for
     *
     * @return a possibly empty array containing Folders that matched the pattern
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
    public synchronized Folder[] list(String pattern) throws MessagingException {
        // go filter the folders based on the pattern.  The server does most of the
        // heavy lifting on the pattern matching.
        return filterFolders(pattern, false);
    }


    /**
     * Return a list of folders to which the user is subscribed and which match the supplied pattern.
     * If the store does not support the concept of subscription then this should match against
     * all folders; the default implementation of this method achieves this by defaulting to the
     * {@link #list(String)} method.
     *
     * @param pattern the pattern to search for
     *
     * @return a possibly empty array containing subscribed Folders that matched the pattern
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
    public synchronized Folder[] listSubscribed(String pattern) throws MessagingException {
        // go filter the folders based on the pattern.  The server does most of the
        // heavy lifting on the pattern matching.
        return filterFolders(pattern, true);
    }


    /**
     * Return the character used by this folder's Store to separate path components.
     *
     * @return the name separater character
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized char getSeparator() throws MessagingException {
        // not determined yet, we need to ask the server for the information
        if (separator == UNDETERMINED) {
            IMAPConnection connection = getConnection();
            try {
                List responses = connection.list("", fullname);
                IMAPListResponse info = findListResponse(responses, fullname);

                // if we didn't get any hits, then we just assume a reasonable default.
                if (info == null) {
                    separator = '/';
                }
                else {
                    separator = info.separator;
                    // this can be omitted in the response, so assume a default
                    if (separator == '\0') {
                        separator = '/';
                    }
                }
            } finally {
                releaseConnection(connection);
            }
        }
        return separator;
	}


    /**
     * Return whether this folder can hold just messages or also
     * subfolders.
     *
     * @return The combination of Folder.HOLDS_MESSAGES and Folder.HOLDS_FOLDERS, depending
     * on the folder capabilities.
     * @exception MessagingException
     */
	public int getType() throws MessagingException {
        // checking the validity will update the type information
        // if it succeeds.
        checkFolderValidity();
		return folderType;
	}


    /**
     * Create a new folder capable of containing subfolder and/or messages as
     * determined by the type parameter. Any hierarchy defined by the folder
     * name will be recursively created.
     * If the folder was sucessfully created, a {@link FolderEvent#CREATED CREATED FolderEvent}
     * is sent to all FolderListeners registered with this Folder or with the Store.
     *
     * @param newType the type, indicating if this folder should contain subfolders, messages or both
     *
     * @return true if the folder was sucessfully created
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
	public synchronized boolean create(int newType) throws MessagingException {
        IMAPConnection connection = getConnection();
        try {

            // by default, just create using the fullname.
            String newPath = fullname;

            // if this folder is expected to only hold additional folders, we need to
            // add a separator on to the end when we create this.
            if ((newType & HOLDS_MESSAGES) == 0) {
                newPath = fullname + separator;
            }
            try {
                // go create this
                connection.createMailbox(newPath);
                // verify this exists...also updates some of the status
                boolean reallyCreated = checkExistance(connection);
                // broadcast a creation event.
                notifyFolderListeners(FolderEvent.CREATED);
                return reallyCreated;
            } catch (MessagingException e) {
                //TODO add folder level debug logging.
            }
            // we have a failure
            return false;
        } finally {
            releaseConnection(connection);
        }
	}


    /**
     * Return the subscription status of this folder.
     *
     * @return true if the folder is marked as subscribed, false for
     *         unsubscribed.
     */
    public synchronized boolean isSubscribed() {
        try {
            IMAPConnection connection = getConnection();
            try {
                // get the lsub response for this folder.
                List responses = connection.listSubscribed("", fullname);

                IMAPListResponse response = findListResponse(responses, fullname);
                if (response == null) {
                    return false;
                }
                else {
                    // a NOSELECT flag response indicates the mailbox is no longer
                    // selectable, so it's also no longer subscribed to.
                    return !response.noselect;
                }
            } finally {
                releaseConnection(connection);
            }
        } catch (MessagingException e) {
            // Can't override to throw a MessagingException on this method, so
            // just swallow any exceptions and assume false is the answer.
        }
        return false;
    }


    /**
     * Set or clear the subscription status of a file.
     *
     * @param flag
     *            The new subscription state.
     */
    public synchronized void setSubscribed(boolean flag) throws MessagingException {
        IMAPConnection connection = getConnection();
        try {
            if (flag) {
                connection.subscribe(fullname);
            }
            else {
                connection.unsubscribe(fullname);
            }
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Check to see if this Folder conatins messages with the {@link Flag.RECENT} flag set.
     * This can be used when the folder is closed to perform a light-weight check for new mail;
     * to perform an incremental check for new mail the folder must be opened.
     *
     * @return true if the Store has recent messages
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized boolean hasNewMessages() throws MessagingException {
        // the folder must exist for this to work.
        checkFolderValidity();

        // get the freshest status information.
        refreshStatus(true);
        // return the indicator from the message state.
        return recentMessages > 0;
	}

    /**
     * Get the Folder determined by the supplied name; if the name is relative
     * then it is interpreted relative to this folder. This does not check that
     * the named folder actually exists.
     *
     * @param name the name of the folder to return
     * @return the named folder
     * @throws MessagingException if there was a problem accessing the store
     */
    public Folder getFolder(String name) throws MessagingException {
        // this must be a real, valid folder to hold a subfolder
        checkFolderValidity();
        if (!holdsFolders()) {
            throw new MessagingException("Folder " + fullname + " cannot hold subfolders");
        }
        // our separator does not get determined until we ping the server for it.  We
        // might need to do that now, so we need to use the getSeparator() method to retrieve this.
        char separator = getSeparator();

        return new IMAPFolder((IMAPStore)store, fullname + separator + name, separator);
    }


    /**
     * Delete this folder and possibly any subfolders. This operation can only be
     * performed on a closed folder.
     * If recurse is true, then all subfolders are deleted first, then any messages in
     * this folder are removed and it is finally deleted; {@link FolderEvent#DELETED}
     * events are sent as appropriate.
     * If recurse is false, then the behaviour depends on the folder type and store
     * implementation as followd:
     * <ul>
     * <li>If the folder can only conrain messages, then all messages are removed and
     * then the folder is deleted; a {@link FolderEvent#DELETED} event is sent.</li>
     * <li>If the folder can onlu contain subfolders, then if it is empty it will be
     * deleted and a {@link FolderEvent#DELETED} event is sent; if the folder is not
     * empty then the delete fails and this method returns false.</li>
     * <li>If the folder can contain both subfolders and messages, then if the folder
     * does not contain any subfolders, any messages are deleted, the folder itself
     * is deleted and a {@link FolderEvent#DELETED} event is sent; if the folder does
     * contain subfolders then the implementation may choose from the following three
     * behaviors:
     * <ol>
     * <li>it may return false indicting the operation failed</li>
     * <li>it may remove all messages within the folder, send a {@link FolderEvent#DELETED}
     * event, and then return true to indicate the delete was performed. Note this does
     * not delete the folder itself and the {@link #exists()} operation for this folder
     * will return true</li>
     * <li>it may remove all messages within the folder as per the previous option; in
     * addition it may change the type of the Folder to only HOLDS_FOLDERS indictaing
     * that messages may no longer be added</li>
     * </li>
     * </ul>
     * FolderEvents are sent to all listeners registered with this folder or
     * with the Store.
     *
     * @param recurse whether subfolders should be recursively deleted as well
     * @return true if the delete operation succeeds
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized boolean delete(boolean recurse) throws MessagingException {
        // we must be in the closed state.
        checkClosed();

        // if recursive, get the list of subfolders and delete them first.
        if (recurse) {

            Folder[] subfolders = list();
            for (int i = 0; i < subfolders.length; i++) {
                // this is a recursive delete also
                subfolders[i].delete(true);
            }
        }

        IMAPConnection connection = getConnection();
        try {
            // delete this one now.
            connection.deleteMailbox(fullname);
            // this folder no longer exists on the server.
            listInfo = null;

            // notify interested parties about the deletion.
            notifyFolderListeners(FolderEvent.DELETED);
            return true;

        } catch (MessagingException e) {
            // ignored
        } finally {
            releaseConnection(connection);
        }
        return false;
	}


    /**
     * Rename this folder; the folder must be closed.
     * If the rename is successfull, a {@link FolderEvent#RENAMED} event is sent to
     * all listeners registered with this folder or with the store.
     *
     * @param newName the new name for this folder
     * @return true if the rename succeeded
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized boolean renameTo(Folder f) throws MessagingException {
        // we must be in the closed state.
        checkClosed();
        // but we must also exist
        checkFolderValidity();

        IMAPConnection connection = getConnection();
        try {
            // delete this one now.
            connection.renameMailbox(fullname, f.getFullName());
            // we renamed, so get a fresh set of status
            refreshStatus(false);

            // notify interested parties about the deletion.
            notifyFolderRenamedListeners(f);
            return true;
        } catch (MessagingException e) {
            // ignored
        } finally {
            releaseConnection(connection);
        }
        return false;
	}


    /**
     * Open this folder; the folder must be able to contain messages and
     * must currently be closed. If the folder is opened successfully then
     * a {@link ConnectionEvent#OPENED} event is sent to listeners registered
     * with this Folder.
     * <p/>
     * Whether the Store allows multiple connections or if it allows multiple
     * writers is implementation defined.
     *
     * @param mode READ_ONLY or READ_WRITE
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized void open(int mode) throws MessagingException {

        // we use a synchronized block rather than use a synchronized method so that we
        // can notify the event listeners while not holding the lock.
        synchronized(this) {
            // can only be performed on a closed folder
            checkClosed();
            // ask the store to kindly hook us up with a connection.
            // We're going to hang on to this until we're closed, so store it in
            // the Folder field.  We need to make sure our mailbox is selected while
            // we're working things.
            currentConnection = ((IMAPStore)store).getFolderConnection(this);
            // we need to make ourselves a handler of unsolicited responses
            currentConnection.addResponseHandler(this);
            // record our open mode
            this.mode = mode;


            try {
                // try to open, which gives us a lot of initial mailbox state.
                IMAPMailboxStatus status = currentConnection.openMailbox(fullname, mode == Folder.READ_ONLY);

                // not available in the requested mode?
                if (status.mode != mode) {
                    // trying to open READ_WRITE and this isn't available?
                    if (mode == READ_WRITE) {
                        throw new ReadOnlyFolderException(this, "Cannot open READ_ONLY folder in READ_WRITE mode");
                    }
                }

                // save this status and when we got it for later updating.
                cachedStatus = status;
                // mark when we got this
                lastStatusTimeStamp = System.currentTimeMillis();

                // now copy the status information over and flip over the open sign.
                this.mode = status.mode;
                maxSequenceNumber = status.messages;
                recentMessages = status.recentMessages;
                uidValidity = status.uidValidity;
                uidNext = status.uidNext;

                availableFlags = status.availableFlags;
                permanentFlags = status.permanentFlags;

                // create a our caches.  These are empty initially
                messageCache = new HashMap();
                uidCache = new HashMap();

                // we're open for business folks!
                folderOpen = true;
                notifyConnectionListeners(ConnectionEvent.OPENED);
            } finally {
                // NB:  this doesn't really release this, but it does drive
                // the processing of any unsolicited responses.
                releaseConnection(currentConnection);
            }
        }
	}


    /**
     * Close this folder; it must already be open.
     * A  @link ConnectionEvent#CLOSED} event is sent to all listeners registered
     {*
     * with this folder.
     *
     * @param expunge whether to expunge all deleted messages
     * @throws MessagingException if there was a problem accessing the store; the folder is still closed
     */
	public synchronized void close(boolean expunge) throws MessagingException {
		// Can only be performed on an open folder
		checkOpen();
        cleanupFolder(expunge, false);
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
    protected void cleanupFolder(boolean expunge, boolean disconnected) throws MessagingException {
		folderOpen = false;
        uidCache = null;
        messageCache = null;
        // if we have a connection active at the moment
        if (currentConnection != null) {
            // was this a forced disconnect by the server?
            if (disconnected) {
                currentConnection.setClosed();
            }
            else {
                // The CLOSE operation depends on what mode was used to select the mailbox.
                // If we're open in READ-WRITE mode, we used a SELECT operation.  When CLOSE
                // is issued, any deleted messages will be expunged.  If we've been asked not
                // to expunge the messages, we have a problem.  The solution is to reselect the
                // mailbox using EXAMINE, which will not expunge messages when closed.
                if (mode == READ_WRITE && !expunge) {
                    // we can ignore the result...we're just switching modes.
                    currentConnection.openMailbox(fullname, true);
                }

                // have this close the selected mailbox
                currentConnection.closeMailbox();
            }
            currentConnection.removeResponseHandler(this);
            // we need to release the connection to the Store once we're closed
            ((IMAPStore)store).releaseFolderConnection(this, currentConnection);
            currentConnection = null;
        }
		notifyConnectionListeners(ConnectionEvent.CLOSED);
    }


    /**
     * Tests the open status of the folder.
     *
     * @return true if the folder is open, false otherwise.
     */
	public boolean isOpen() {
		return folderOpen;
	}

    /**
     * Get the permanentFlags
     *
     * @return The set of permanent flags we support (only SEEN).
     */
	public synchronized Flags getPermanentFlags() {
        if (permanentFlags != null) {
            // we need a copy of our master set.
            return new Flags(permanentFlags);
        }
        else {
            // a null return is expected if not there.
            return null;
        }
	}


    /**
     * Return the number of messages this folder contains.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     *
     * @return the number of messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized int getMessageCount() throws MessagingException {
        checkFolderValidity();

        // if we haven't opened the folder yet, we might not have good status information.
        // go request some, which updates the folder fields also.
        refreshStatus(false);
		return maxSequenceNumber;
	}

    /**
     * Return the numbew of messages in this folder that have the {@link Flag.RECENT} flag set.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     * The default implmentation of this method iterates over all messages
     * in the folder; subclasses should override if possible to provide a more
     * efficient implementation.
     *
     * NB:  This is an override of the default Folder implementation, which
     * examines each of the messages in the folder.  IMAP has more efficient
     * mechanisms for grabbing the information.
     *
     * @return the number of new messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
    public synchronized int getNewMessageCount() throws MessagingException {
        // the folder must be a real one for this to work.
        checkFolderValidity();
        // now get current status from the folder
        refreshStatus(false);
        // this should be current now.
        return recentMessages;
    }



    /**
     * Return the number of messages in this folder that do not have the {@link Flag.SEEN} flag set.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     * The default implmentation of this method iterates over all messages
     * in the folder; subclasses should override if possible to provide a more
     * efficient implementation.
     *
     * NB:  This is an override of the default Folder implementation, which
     * examines each of the messages in the folder.  IMAP has more efficient
     * mechanisms for grabbing the information.
     *
     * @return the number of new messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized int getUnreadMessageCount() throws MessagingException {
        checkFolderValidity();
        // if we haven't opened the folder yet, we might not have good status information.
        // go request some, which updates the folder fields also.
        if (!folderOpen) {
            refreshStatus(false);
        }
        else {
            // if we have an open connection, then search the folder for any messages
            // marked UNSEEN.

            // UNSEEN is a false test on SEEN using the search criteria.
            SearchTerm criteria = new FlagTerm(new Flags(Flags.Flag.SEEN), false);

            // ask the store to kindly hook us up with a connection.
            IMAPConnection connection = getConnection();
            try {
                // search using the connection directly rather than calling our search() method so we don't
                // need to instantiate each of the matched messages.  We're really only interested in the count
                // right now.
                int[] matches = connection.searchMailbox(criteria);
                // update the unseen count.
                unseenMessages = matches == null ? 0 : matches.length;
            } finally {
                releaseConnection(connection);
            }
        }
        // return our current message count.
		return unseenMessages;
	}



    /**
     * Return the number of messages in this folder that have the {@link Flag.DELETED} flag set.
     * If this operation is invoked on a closed folder, the implementation
     * may choose to return -1 to avoid the expense of opening the folder.
     * The default implmentation of this method iterates over all messages
     * in the folder; subclasses should override if possible to provide a more
     * efficient implementation.
     *
     * @return the number of new messages, or -1 if unknown
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized int getDeletedMessageCount() throws MessagingException {
        checkFolderValidity();

        // if we haven't opened the folder yet, we might not have good status information.
        // go request some, which updates the folder fields also.
        if (!folderOpen) {
            // the status update doesn't return deleted messages.  These can only be obtained by
            // searching an open folder.  Just return a bail-out response
            return -1;
        }
        else {
            // if we have an open connection, then search the folder for any messages
            // marked DELETED.

            // UNSEEN is a false test on SEEN using the search criteria.
            SearchTerm criteria = new FlagTerm(new Flags(Flags.Flag.DELETED), true);

            // ask the store to kindly hook us up with a connection.
            IMAPConnection connection = getConnection();
            try {
                // search using the connection directly rather than calling our search() method so we don't
                // need to instantiate each of the matched messages.  We're really only interested in the count
                // right now.
                int[] matches = connection.searchMailbox(criteria);
                return matches == null ? 0 : matches.length;
            } finally {
                releaseConnection(connection);
            }
        }
	}


    /**
     * Retrieve the message with the specified index in this Folder;
     * messages indices start at 1 not zero.
     * Clients should note that the index for a specific message may change
     * if the folder is expunged; {@link Message} objects should be used as
     * references instead.
     *
     * @param msgNum The message sequence number of the target message.
     *
     * @return the message
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
    public synchronized Message getMessage(int msgNum) throws MessagingException {
        // Can only be performed on an Open folder
        checkOpen();
        // Check the validity of the message number.  This may require pinging the server to
        // see if there are new messages in the folder.
        checkMessageValidity(msgNum);
        // create the mapping key for this
        Integer messageKey = new Integer(msgNum);
        // ok, if the message number is within range, we should have this in the
        // messages list.  Just return the element.
        Message message = (Message)messageCache.get(messageKey);
        // if not in the cache, create a dummy add it in.  The message body will be
        // retrieved on demand
        if (message == null) {
            message = new IMAPMessage(this, ((IMAPStore)store), nextMessageID++, msgNum);
            messageCache.put(messageKey, message);
        }
        return message;
    }


    /**
     * Retrieve a range of messages for this folder.
     * messages indices start at 1 not zero.
     *
     * @param start  Index of the first message to fetch, inclusive.
     * @param end    Index of the last message to fetch, inclusive.
     *
     * @return An array of the fetched messages.
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
    public synchronized Message[] getMessages(int start, int end) throws MessagingException {
        // Can only be performed on an Open folder
        checkOpen();
        Message[] messageRange = new Message[end - start + 1];

        for (int i = 0; i < messageRange.length; i++) {
            // NB:  getMessage() requires values that are origin 1, so there's
            // no need to adjust the value by other than the start position.
            messageRange[i] = getMessage(start + i);
        }
        return messageRange;
    }


    /**
     * Append the supplied messages to this folder. A {@link MessageCountEvent} is sent
     * to all listeners registered with this folder when all messages have been appended.
     * If the array contains a previously expunged message, it must be re-appended to the Store
     * and implementations must not abort this operation.
     *
     * @param msgs   The array of messages to append to the folder.
     *
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
	public synchronized void appendMessages(Message[] msgs) throws MessagingException {
        checkFolderValidity();
        for (int i = 0; i < msgs.length; i++) {
            Message msg = msgs[i];

            appendMessage(msg);
        }
	}

    /**
     * Hint to the store to prefetch information on the supplied messages.
     * Subclasses should override this method to provide an efficient implementation;
     * the default implementation in this class simply returns.
     *
     * @param messages messages for which information should be fetched
     * @param profile  the information to fetch
     * @throws MessagingException if there was a problem accessing the store
     * @see FetchProfile
     */
    public void fetch(Message[] messages, FetchProfile profile) throws MessagingException {

        // we might already have the information being requested, so ask each of the
        // messages in the list to evaluate itself against the profile.  We'll only ask
        // the server to send information that's required.
        List fetchSet = new ArrayList();

        for (int i = 0; i < messages.length; i++) {
            Message msg = messages[i];
            // the message is missing some of the information still.  Keep this in the list.
            // even if the message is only missing one piece of information, we still fetch everything.
            if (((IMAPMessage)msg).evaluateFetch(profile)) {
                fetchSet.add(msg);
            }
        }

        // we've got everything already, no sense bothering the server
        if (fetchSet.isEmpty()) {
            return;
        }
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();
        try {
            // ok, from this point onward, we don't want any threads messing with the
            // message cache.  A single processed EXPUNGE could make for a very bad day
            synchronized(this) {
                // get the message set for this
                String messageSet = generateMessageSet(fetchSet);
                // fetch all of the responses
                List responses = connection.fetch(messageSet, profile);

                // IMPORTANT:  We must do our updates while synchronized to keep the
                // cache from getting updated underneath us.   This includes
                // not releasing the connection until we're done to delay processing any
                // pending expunge responses.
                for (int i = 0; i < responses.size(); i++) {
                    IMAPFetchResponse response = (IMAPFetchResponse)responses.get(i);
                    Message msg = getMessage(response.getSequenceNumber());
                    // Belt and Braces.  This should never be false.
                    if (msg != null) {
                        // have the message apply this to itself.
                        ((IMAPMessage)msg).updateMessageInformation(response);
                    }
                }
            }
        } finally {
            releaseConnection(connection);
        }
        return;
    }

    /**
     * Set flags on the messages to the supplied value; all messages must belong to this folder.
     * This method may be overridden by subclasses that can optimize the setting
     * of flags on multiple messages at once; the default implementation simply calls
     * {@link Message#setFlags(Flags, boolean)} for each supplied messages.
     *
     * @param messages whose flags should be set
     * @param flags    the set of flags to modify
     * @param set      Indicates whether the flags should be set or cleared.
     *
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
    public void setFlags(Message[] messages, Flags flags, boolean set) throws MessagingException {
        // this is a list of messages for the change broadcast after the update
        List updatedMessages = new ArrayList();

        synchronized(this) {
            // the folder must be open and writeable.
            checkOpenReadWrite();

            // now make sure these are settable flags.
            if (!availableFlags.contains(flags))
            {
                throw new MessagingException("The IMAP server does not support changing of this flag set");
            }

            // turn this into a set of message numbers
            String messageSet = generateMessageSet(messages);
            // if all of the messages have been expunged, nothing to do.
            if (messageSet == null) {
                return;
            }
            // ask the store to kindly hook us up with a connection.
            IMAPConnection connection = getConnection();

            try {
                // and have the connection set this
                List responses = connection.setFlags(messageSet, flags, set);
                // retrieve each of the messages from our cache, and do the flag update.
                // we need to keep the list so we can broadcast a change update event
                // when we're finished.
                for (int i = 0; i < responses.size(); i++) {
                    IMAPFetchResponse response = (IMAPFetchResponse)responses.get(i);

                    // get the updated message and update the internal state.
                    Message message = getMessage(response.sequenceNumber);
                    // this shouldn't happen, but it might have been expunged too.
                    if (message != null) {
                        ((IMAPMessage)message).updateMessageInformation(response);
                        updatedMessages.add(message);
                    }
                }
            } finally {
                releaseConnection(connection);
            }
        }

        // ok, we're no longer holding the lock.  Now go broadcast the update for each
        // of the affected messages.
        for (int i = 0; i < updatedMessages.size(); i++) {
            Message message = (Message)updatedMessages.get(i);
            notifyMessageChangedListeners(MessageChangedEvent.FLAGS_CHANGED, message);
        }
    }


    /**
     * Set flags on a range of messages to the supplied value.
     * This method may be overridden by subclasses that can optimize the setting
     * of flags on multiple messages at once; the default implementation simply
     * gets each message and then calls {@link Message#setFlags(Flags, boolean)}.
     *
     * @param start  first message end set
     * @param end    last message end set
     * @param flags  the set of flags end modify
     * @param value  Indicates whether the flags should be set or cleared.
     *
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
    public synchronized void setFlags(int start, int end, Flags flags, boolean value) throws MessagingException {
        Message[] msgs = new Message[end - start + 1];

        for (int i = start; i <= end; i++) {
            msgs[i] = getMessage(i);
        }
        // go do a bulk set operation on these messages
        setFlags(msgs, flags, value);
    }

    /**
     * Set flags on a set of messages to the supplied value.
     * This method may be overridden by subclasses that can optimize the setting
     * of flags on multiple messages at once; the default implementation simply
     * gets each message and then calls {@link Message#setFlags(Flags, boolean)}.
     *
     * @param ids    the indexes of the messages to set
     * @param flags  the set of flags end modify
     * @param value  Indicates whether the flags should be set or cleared.
     *
     * @throws MessagingException
     *                if there was a problem accessing the store
     */
    public synchronized void setFlags(int ids[], Flags flags, boolean value) throws MessagingException {
        Message[] msgs = new Message[ids.length];

        for (int i = 0; i <ids.length; i++) {
            msgs[i] = getMessage(ids[i]);
        }
        // go do a bulk set operation on these messages
        setFlags(msgs, flags, value);
    }


    /**
     * Copy the specified messages to another folder.
     * The default implementation simply appends the supplied messages to the
     * target folder using {@link #appendMessages(Message[])}.
     * @param messages the messages to copy
     * @param folder the folder to copy to
     * @throws MessagingException if there was a problem accessing the store
     */
    public synchronized void copyMessages(Message[] messages, Folder folder) throws MessagingException {
        // the default implementation just appends the messages to the target.  If
        // we're copying between two folders of the same store, we can get the server to
        // do most of the work for us without needing to fetch all of the message data.
        // If we're dealing with two different Store instances, we need to do this the
        // hardway.
        if (getStore() != folder.getStore()) {
            super.copyMessages(messages, folder);
            return;
        }

        // turn this into a set of message numbers
        String messageSet = generateMessageSet(messages);
        // if all of the messages have been expunged, nothing to do.
        if (messageSet == null) {
            return;
        }
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // ask the server to copy this information over to the other mailbox.
            connection.copyMessages(messageSet, folder.getFullName());
        } finally {
            releaseConnection(connection);
        }
    }



    /**
     * Permanently delete all supplied messages that have the DELETED flag set from the Store.
     * The original message indices of all messages actually deleted are returned and a
     * {@link MessageCountEvent} event is sent to all listeners with this folder. The expunge
     * may cause the indices of all messaged that remain in the folder to change.
     *
     * @return the original indices of messages that were actually deleted
     * @throws MessagingException if there was a problem accessing the store
     */
	public synchronized Message[] expunge() throws MessagingException {
        // must be open to do this.
        checkOpen();
        // and changes need to be allowed
        checkReadWrite();

        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();
        List expunges = null;

        try {
            // send the expunge notification.  This operation results in "nn EXPUNGE" responses getting returned
            // for each expunged messages.  These will be dispatched to our response handler, which will process
            // the expunge operation.  We could process this directly, but we may have received asynchronous
            // expunge messages that also marked messages as expunged.
            expunges = connection.expungeMailbox();
        } finally {
            releaseConnection(connection);
        }

        // we get one EXPUNGE message for each message that's expunged.  They MUST be processed in
        // order, as the message sequence numbers represent a relative position that takes into account
        // previous expunge operations.  For example, if message sequence numbers 5, 6, and 7 are
        // expunged, we receive 3 expunge messages, all indicating that message 5 has been expunged.
        Message[] messages = new Message[expunges.size()];

        // now we need to protect the internal structures
        synchronized (this) {
            // expunge all of the messages from the message cache.  This keeps the sequence
            // numbers up to-date.
            for (int i = 0; i < expunges.size(); i++) {
                IMAPSizeResponse response = (IMAPSizeResponse)expunges.get(i);
                messages[i] = expungeMessage(response.getSize());
            }
        }
        // if we have messages that have been removed, broadcast the notification.
        if (messages.length > 0) {
            notifyMessageRemovedListeners(true, messages);
        }

        // note, we're expected to return an array in all cases, even if the expunged count was zero.
        return messages;
	}



    /**
     * Search the supplied messages for those that match the supplied criteria;
     * messages must belong to this folder.
     * The default implementation iterates through the messages, returning those
     * whose {@link Message#match(javax.mail.search.SearchTerm)} method returns true;
     * subclasses may provide a more efficient implementation.
     *
     * @param term the search criteria
     * @param messages the messages to search
     * @return an array containing messages that match the criteria
     * @throws MessagingException if there was a problem accessing the store
     */
    public synchronized Message[] search(SearchTerm term) throws MessagingException {
        // only allowed on open folders
        checkOpen();

        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // just search everything
            int[] messageNumbers = connection.searchMailbox(term);
            return resolveMessages(messageNumbers);
        } finally {
            releaseConnection(connection);
        }
    }



    /**
     * Search the supplied messages for those that match the supplied criteria;
     * messages must belong to this folder.
     * The default implementation iterates through the messages, returning those
     * whose {@link Message#match(javax.mail.search.SearchTerm)} method returns true;
     * subclasses may provide a more efficient implementation.
     *
     * @param term the search criteria
     * @param messages the messages to search
     * @return an array containing messages that match the criteria
     * @throws MessagingException if there was a problem accessing the store
     */
    public synchronized Message[] search(SearchTerm term, Message[] messages) throws MessagingException {
        // only allowed on open folders
        checkOpen();

        // turn this into a string specifier for these messages.  We'll weed out the expunged messages first.
        String messageSet = generateMessageSet(messages);

        // If we have no "live" messages to search, just return now.  We're required to return a non-null
        // value, so give an empy array back.
        if (messageSet == null) {
            return new Message[0];
        }

        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {

            // now go do the search.
            int[] messageNumbers = connection.searchMailbox(messageSet, term);
            return resolveMessages(messageNumbers);
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Get the UID validity value for this Folder.
     *
     * @return The current UID validity value, as a long.
     * @exception MessagingException
     */
    public synchronized long getUIDValidity() throws MessagingException
    {
        // get the latest status to make sure we have the
        // most current.
        refreshStatus(true);
        return uidValidity;
    }

    /**
     * Retrieve a message using the UID rather than the
     * message sequence number.  Returns null if the message
     * doesn't exist.
     *
     * @param uid    The target UID.
     *
     * @return the Message object.  Returns null if the message does
     *         not exist.
     * @exception MessagingException
     */
    public synchronized Message getMessageByUID(long uid) throws MessagingException
    {
        // only allowed on open folders
        checkOpen();

        Long key = new Long(uid);
        // first check to see if we have a cached value for this
        synchronized(messageCache) {
            Message msg = (Message)uidCache.get(key);
            if (msg != null) {
                return msg;
            }
        }

        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // locate the message identifier
            IMAPUid imapuid = connection.getSequenceNumberForUid(uid);
            // if nothing is returned, the message doesn't exist
            if (imapuid == null) {
                return null;
            }


            // retrieve the actual message object and place this in the UID cache
            return retrieveMessageByUid(key, imapuid.messageNumber);
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Get a series of messages using a UID range.  The
     * special value LASTUID can be used to mark the
     * last available message.
     *
     * @param start  The start of the UID range.
     * @param end    The end of the UID range.  The special value
     *               LASTUID can be used to request all messages up
     *               to the last UID.
     *
     * @return An array containing all of the messages in the
     *         range.
     * @exception MessagingException
     */
    public synchronized Message[] getMessagesByUID(long start, long end) throws MessagingException
    {
        // only allowed on open folders
        checkOpen();
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // locate the message identifier
            List uids = connection.getSequenceNumbersForUids(start, end);
            Message[] msgs = new Message[uids.size()];

            // fill in each of the messages based on the returned value
            for (int i = 0; i < msgs.length; i++) {
                IMAPUid uid = (IMAPUid)uids.get(i);
                msgs[i] = retrieveMessageByUid(new Long(uid.uid), uid.messageNumber);
            }

            return msgs;
        } finally {
            releaseConnection(connection);
        }


    }

    /**
     * Retrieve a set of messages by explicit UIDs.  If
     * any message in the list does not exist, null
     * will be returned for the corresponding item.
     *
     * @param ids    An array of UID values to be retrieved.
     *
     * @return An array of Message items the same size as the ids
     *         argument array.  This array will contain null
     *         entries for any UIDs that do not exist.
     * @exception MessagingException
     */
    public synchronized Message[] getMessagesByUID(long[] ids) throws MessagingException
    {
        // only allowed on open folders
        checkOpen();

        Message[] msgs = new Message[ids.length];

        for (int i = 0; i < msgs.length; i++) {
            msgs[i] = getMessageByUID(ids[i]);
        }

        return msgs;
    }

    /**
     * Retrieve the UID for a message from this Folder.
     * The argument Message MUST belong to this Folder
     * instance, otherwise a NoSuchElementException will
     * be thrown.
     *
     * @param message The target message.
     *
     * @return The UID associated with this message.
     * @exception MessagingException
     */
    public synchronized long getUID(Message message) throws MessagingException
    {
        // verify this actually is in this folder.
        checkMessageFolder(message);
        IMAPMessage msg = (IMAPMessage)message;

        // we might already know this bit of information
        if (msg.getUID() != -1) {
            return msg.getUID();
        }

        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // locate the message identifier
            IMAPUid imapuid = connection.getUidForSequenceNumber(msg.getMessageNumber());
            // if nothing is returned, the message doesn't exist
            if (imapuid == null) {
                return -1;
            }
            // cache this information now that we've gotten it.
            addToUidCache(new Long(imapuid.uid), getMessage(imapuid.messageNumber));
            // return the UID information.
            return imapuid.uid;
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Retrieve a message from a UID/message mapping.
     *
     * @param key       The UID key used for the mapping.
     * @param msgNumber The message sequence number.
     *
     * @return The Message object corresponding to the message.
     * @exception MessagingException
     */
    protected synchronized Message retrieveMessageByUid(Long key, int msgNumber) throws MessagingException
    {
        synchronized (messageCache) {
            // first check the cache...this might have already been added.
            Message msg = (Message)uidCache.get(key);
            if (msg != null) {
                return msg;
            }

            // retrieve the message by sequence number
            msg = getMessage(msgNumber);
            // add this to our UID mapping cache.
            addToUidCache(key, msg);
            return msg;
        }
    }


    /**
     * Add a message to the UID mapping cache, ensuring that
     * the UID value is updated.
     *
     * @param key    The UID key.
     * @param msg    The message to add.
     */
    protected void addToUidCache(Long key, Message msg) {
        synchronized (messageCache) {
            ((IMAPMessage)msg).setUID(key.longValue());
            uidCache.put(key, msg);
        }
    }


    /**
     * Append a single message to the IMAP Folder.
     *
     * @param msg    The message to append.
     *
     * @exception MessagingException
     */
    protected synchronized void appendMessage(Message msg) throws MessagingException
    {
        // sort out the dates.  If no received date, use the sent date.
        Date date = msg.getReceivedDate();
        if (date == null) {
            date = msg.getSentDate();
        }

        Flags flags = msg.getFlags();

        // convert the message into an array of bytes we can attach as a literal.
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            msg.writeTo(out);
        } catch (IOException e) {
        }

        // now issue the append command
        IMAPConnection connection = getConnection();
        try {
            connection.appendMessage(getFullName(), date, flags, out.toByteArray());
        } finally {
            releaseConnection(connection);
        }
    }


    /**
     * Retrieve the list of matching groups from the IMAP server using the LIST
     * or LSUB command. The server does the wildcard matching for us.
     *
     * @param pattern
     *            The pattern string (in wildmat format) used to match.
     *
     * @return An array of folders for the matching groups.
     */
    protected synchronized Folder[] filterFolders(String pattern, boolean subscribed) throws MessagingException {
        IMAPConnection connection = getConnection();
        // this is used to filter out our own folder from the search
        String root = fullname;
        if (!root.isEmpty()) {
            root = root + getSeparator();
        }

        List responses = null;
        try {


            if (subscribed) {
                // get the lsub response for this folder.
                responses = connection.listSubscribed(root, pattern);
            }
            else {
                // grab using the LIST command.
                responses = connection.list(root, pattern);
            }
        } finally {
            releaseConnection(connection);
        }

        List folders = new ArrayList();

        for (int i = 0; i < responses.size(); i++) {
            IMAPListResponse response = (IMAPListResponse)responses.get(i);
            // if a full wildcard is specified, the root folder can be returned too.  Make sure we
            // filter that one out.
            if (!response.mailboxName.equals(root)) {
                IMAPFolder folder = new IMAPFolder((IMAPStore)store, response.mailboxName, response.separator);
                folders.add(folder);
            }
        }

        // convert into an array and return
        return (Folder[])folders.toArray(new Folder[folders.size()]);
    }


    /**
     * Test if a folder can hold sub folders.
     *
     * @return True if the folder is allowed to have subfolders.
     */
    protected synchronized boolean holdsFolders() throws MessagingException {
        checkFolderValidity();
        return (folderType & HOLDS_FOLDERS) != 0;
    }


    /**
     * Validate that a target message number is considered valid
     * by the IMAP server.  If outside of the range we currently
     * are a ware of, we'll ping the IMAP server to see if there
     * have been any updates.
     *
     * @param messageNumber
     *               The message number we're checking.
     *
     * @exception MessagingException
     */
    protected void checkMessageValidity(int messageNumber) throws MessagingException {
        // lower range for a message is 1.
        if (messageNumber < 1) {
            throw new MessagingException("Invalid message number for IMAP folder: " + messageNumber);
        }
        // if within our current known range, we'll accept this
        if (messageNumber <= maxSequenceNumber) {
            return;
        }

        IMAPConnection connection = getConnection();

        synchronized (this) {
            try {
                // ping the server to see if there's any updates to process.  The updates are handled
                // by the response handlers.
                connection.updateMailboxStatus();
            } finally {
                releaseConnection(connection);
            }
        }

        // still out of range?
        if (messageNumber > maxSequenceNumber) {
            throw new MessagingException("Message " + messageNumber + " does not exist on server");
        }
    }


	/**
	 * Below is a list of convenience methods that avoid repeated checking for a
	 * value and throwing an exception
	 */

    /**
     * Ensure the folder is open.  Throws a MessagingException
     * if not in the correct state for the operation.
     *
     * @exception IllegalStateException
     */
    protected void checkOpen() throws IllegalStateException {
		if (!folderOpen){
		    throw new IllegalStateException("Folder is not Open");
		}
    }

    /**
     * Ensure the folder is not open for operations
     * that require the folder to be closed.
     *
     * @exception IllegalStateException
     */
    protected void checkClosed() throws IllegalStateException {
		if (folderOpen){
		    throw new IllegalStateException("Folder is Open");
		}
    }

    /**
     * Ensure that the folder is open for read/write mode before doing
     * an operation that would make a change.
     *
     * @exception IllegalStateException
     */
    protected void checkReadWrite() throws IllegalStateException {
        if (mode != READ_WRITE) {
		    throw new IllegalStateException("Folder is opened READY_ONLY");
        }
    }


    /**
     * Check that the folder is open and in read/write mode.
     *
     * @exception IllegalStateException
     */
    protected void checkOpenReadWrite() throws IllegalStateException {
        checkOpen();
        checkReadWrite();
    }



    /**
     * Notify the message changed listeners that a
     * message contained in the folder has been updated.
     *
     * @param type   The type of update made to the message.
     * @param m      The message that was updated.
     *
     * @see javax.mail.Folder#notifyMessageChangedListeners(int, javax.mail.Message)
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
    protected synchronized IMAPConnection getConnection() throws MessagingException {
        // don't have an open connection yet?  Just request a pool connection.
        if (currentConnection == null) {
            // request a connection from the central store.
            IMAPConnection connection = ((IMAPStore)store).getFolderConnection(this);
            // we need to make ourselves a handler of unsolicited responses
            connection.addResponseHandler(this);
            return connection;
        }
        // we have a connection for our use.  Just return it.
        return currentConnection;
    }


    /**
     * Release our connection back to the Store.
     *
     * @param connection The connection to release.
     *
     * @exception MessagingException
     */
    protected void releaseConnection(IMAPConnection connection) throws MessagingException {
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
        // if no cached connection or this is somehow different from the cached one, just
        // return it.
        if (currentConnection == null || connection != currentConnection) {
            connection.removeResponseHandler(this);
            ((IMAPStore)store).releaseFolderConnection(this, connection);
        }
        // if we're open, then we don't have to worry about returning this connection
        // to the Store.  This is set up perfectly for our use right now.
    }


    /**
     * Obtain a connection object for a Message attached to this Folder.  This
     * will be the Folder's connection, which is only available if the Folder
     * is currently open.
     *
     * @return The connection object for the Message instance to use.
     * @exception MessagingException
     */
    synchronized IMAPConnection getMessageConnection() throws MessagingException {
        // if we're not open, the messages can't communicate either
        if (currentConnection == null) {
            throw new FolderClosedException(this, "No Folder connections available");
        }
        // return the current Folder connection.  At this point, we'll be sharing the
        // connection between the Folder and the Message (and potentially, other messages).  The
        // command operations on the connection are synchronized so only a single command can be
        // issued at one time.
        return currentConnection;
    }


    /**
     * Release the connection object back to the Folder instance.
     *
     * @param connection The connection being released.
     *
     * @exception MessagingException
     */
    void releaseMessageConnection(IMAPConnection connection) throws MessagingException {
        // release it back to ourselves...this will drive unsolicited message processing.
        releaseConnection(connection);
    }


    /**
     * Refresh the status information on this folder.
     *
     * @param force  Force a status refresh always.
     *
     * @exception MessagingException
     */
    protected void refreshStatus(boolean force) throws MessagingException {
        // first check that any cached status we've received has gotten a little moldy.
        if (cachedStatus != null) {
            // if not forcing, check the time out.
            if (!force) {
                if (statusCacheTimeout > 0) {
                    long age = System.currentTimeMillis() - lastStatusTimeStamp;
                    if (age < statusCacheTimeout) {
                        return;
                    }
                }
            }
            // make sure the stale information is cleared out.
            cachedStatus = null;
        }

        IMAPConnection connection = getConnection();
        try {
            // ping the server for the list information for this folder
            cachedStatus = connection.getMailboxStatus(fullname);
            // mark when we got this
            lastStatusTimeStamp = System.currentTimeMillis();
        } finally {
            releaseConnection(connection);
        }

        // refresh the internal state from the message information
        maxSequenceNumber = cachedStatus.messages;
        recentMessages = cachedStatus.recentMessages;
        unseenMessages = cachedStatus.unseenMessages;
        uidValidity = cachedStatus.uidValidity;
    }


    /**
     * Process an EXPUNGE response for a message, removing the
     * message from the message cache.
     *
     * @param sequenceNumber
     *               The sequence number for the expunged message.
     *
     * @return The Message object corresponding to this expunged
     *         message.
     * @exception MessagingException
     */
    protected synchronized Message expungeMessage(int sequenceNumber) throws MessagingException {

        // first process the expunged message.  We need to return a Message instance, so
        // force this to be added to the cache
        IMAPMessage expungedMessage = (IMAPMessage)getMessage(sequenceNumber);
        // mark the message as expunged.
        expungedMessage.setExpunged(true);
        // have we retrieved a UID for this message?  If we have, then it's in the UID cache and
        // needs removal from there also
        long uid = ((IMAPMessage)expungedMessage).getUID();
        if (uid >= 0) {
            uidCache.remove(new Long(uid));
        }
        // because we need to jigger the keys of some of these, we had better have a working
        // copy.
        Map newCache = new HashMap();

        // now process each message in the cache, making adjustments as necessary
        Iterator i = messageCache.keySet().iterator();

        while (i.hasNext()) {
            Integer key = (Integer)i.next();
            int index = key.intValue();
            // if before the expunged message, just copy over to the
            // new cache
            if (index < sequenceNumber) {
                newCache.put(key, messageCache.get(key));
            }
            // after the expunged message...we need to adjust this
            else if (index > sequenceNumber) {
                // retrieve the message using the current position,
                // adjust the message sequence number, and add to the new
                // message cache under the new key value
                IMAPMessage message = (IMAPMessage)messageCache.get(key);
                message.setSequenceNumber(index - 1);
                newCache.put(new Integer(index - 1), message);
            }
            else {
                // the expunged message.  We don't move this over to the new
                // cache, and we've already done all processing of that message that's
                // required
            }
        }

        // replace the old cache now that everything has been adjusted
        messageCache = newCache;

        // adjust the message count downward
        maxSequenceNumber--;
        return expungedMessage;
    }


    /**
     * Resolve an array of message numbers into an array of the
     * referenced messages.
     *
     * @param messageNumbers
     *               The array of message numbers (can be null).
     *
     * @return An array of Message[] containing the resolved messages from
     *         the list.  Returns a zero-length array if there are no
     *         messages to resolve.
     * @exception MessagingException
     */
    protected Message[] resolveMessages(int[] messageNumbers) throws MessagingException {
        // the connection search returns a null pointer if nothing was found, just convert this into a
        // null array.
        if (messageNumbers == null) {
            return new Message[0];
        }

        Message[] messages = new Message[messageNumbers.length];

        // retrieve each of the message numbers in turn.
        for (int i = 0; i < messageNumbers.length; i++) {
            messages[i] = getMessage(messageNumbers[i]);
        }

        return messages;
    }

    /**
     * Generate a message set string from a List of messages rather than an
     * array.
     *
     * @param messages The List of messages.
     *
     * @return The evaluated message set string.
     * @exception MessagingException
     */
    protected String generateMessageSet(List messages) throws MessagingException {
        Message[] msgs = (Message[])messages.toArray(new Message[messages.size()]);
        return generateMessageSet(msgs);
    }


    /**
     * Take an array of messages and generate a String <message set>
     * argument as specified by RFC 2060.  The message set argument
     * is a comma-separated list of message number ranges.  A
     * single element range is just one number.  A longer range is
     * a pair of numbers separated by a ":".  The generated string
     * should not have any blanks.  This will attempt to locate
     * consequetive ranges of message numbers, but will only do this
     * for messages that are already ordered in the array (i.e., we
     * don't try to sort).  Expunged messages are excluded from the
     * search, since they don't exist anymore.  A valid search string
     * will look something like this:
     *
     *    "3,6:10,15,21:35"
     *
     * @param messages The array of messages we generate from.
     *
     * @return A string formatted version of these message identifiers that
     *         can be used on an IMAP command.
     */
    protected String generateMessageSet(Message[] messages) throws MessagingException {
        StringBuffer set = new StringBuffer();

        for (int i = 0; i < messages.length; i++) {
            // first scan the list looking for a "live" message.
            IMAPMessage start = (IMAPMessage)messages[i];
            if (!start.isExpunged()) {

                // we can go ahead and add this to the list now.  If we find this is the start of a
                // range, we'll tack on the ":end" bit once we find the last message in the range.
                if (set.length() != 0) {
                    // only append the comma if not the first element of the list
                    set.append(',');
                }

                // append the first number.  NOTE:  We append this directly rather than
                // use appendInteger(), which appends it using atom rules.
                set.append(Integer.toString(start.getSequenceNumber()));

                // ok, we have a live one.  Now scan the list from here looking for the end of
                // a range of consequetive messages.
                int endIndex = -1; ;
                // get the number we're checking against.
                int previousSequence = start.getSequenceNumber();
                for (int j = i + 1; j < messages.length; j++) {
                    IMAPMessage message = (IMAPMessage)messages[j];
                    if (!message.isExpunged()) {
                        // still consequetive?
                        if (message.getSequenceNumber() == previousSequence + 1) {
                            // step this for the next check.
                            previousSequence++;
                            // record this as the current end of the range.
                            endIndex = j;
                        }
                        else {
                            // found a non-consequetive one, stop here
                            break;
                        }
                    }
                }

                // have a range end point?  Add the range specifier and step the loop index point
                // to skip over this
                if (endIndex != -1) {
                    // pick up the scan at the next location
                    i = endIndex;

                    set.append(':');
                    set.append(Integer.toString(((IMAPMessage)messages[endIndex]).getSequenceNumber()));
                }
            }
        }

        // return null for an empty list. This is possible because either an empty array has been handed to
        // us or all of the messages in the array have been expunged.
        if (set.length() == 0) {
            return null;
        }
        return set.toString();
    }

    /**
     * Verify that this folder exists on the server before
     * performning an operation that requires a valid
     * Folder instance.
     *
     * @exception MessagingException
     */
    protected void checkFolderValidity() throws MessagingException {
        // if we are holding a current listinfo response, then
        // we have chached existance information.  In that case,
        // all of our status is presumed up-to-date and we can go
        // with that.  If we don't have the information, then we
        // ping the server for it.
        if (listInfo == null && !exists()) {
            throw new FolderNotFoundException(this, "Folder " + fullname + " not found on server");
        }
    }


    /**
     * Check if a Message is properly within the target
     * folder.
     *
     * @param msg    The message we're checking.
     *
     * @exception MessagingException
     */
    protected void checkMessageFolder(Message msg) throws MessagingException {
        if (msg.getFolder() != this) {
            throw new NoSuchElementException("Message is not within the target Folder");
        }
    }


    /**
     * Search a list of LIST responses for one containing information
     * for a particular mailbox name.
     *
     * @param responses The list of responses.
     * @param name      The desired mailbox name.
     *
     * @return The IMAPListResponse information for the requested name.
     */
    protected IMAPListResponse findListResponse(List responses, String name) {
        for (int i = 0; i < responses.size(); i++) {
            IMAPListResponse response = (IMAPListResponse)responses.get(i);
            if (response.mailboxName.equals(name)) {
                return response;
            }
        }
        return null;
    }


    /**
     * Protected class intended for subclass overrides.  For normal folders,
     * the mailbox name is fullname.  For Namespace root folders, the mailbox
     * name is the prefix + separator.
     *
     * @return The string name to use as the mailbox name for exists() and issubscribed()
     *         calls.
     */
    protected String getMailBoxName() {
        return fullname;
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
        // "you've got mail".  The message count has been updated.  There
        // are two posibilities.  Either there really are new messages, or
        // this is an update following an expunge.  If there are new messages,
        // we need to update the message cache and broadcast the change to
        // any listeners.
        if (response.isKeyword("EXISTS")) {
            // we need to update our cache, and also retrieve the new messages and
            // send them out in a broadcast update.
            int oldCount = maxSequenceNumber;
            maxSequenceNumber = ((IMAPSizeResponse)response).getSize();
            // has the size grown?  We have to send the "you've got mail" announcement.
            if (oldCount < maxSequenceNumber) {
                try {
                    Message[] messages = getMessages(oldCount + 1, maxSequenceNumber);
                    notifyMessageAddedListeners(messages);
                } catch (MessagingException e) {
                    // should never happen in this context
                }
            }
            return true;
        }
        // "you had mail".  A message was expunged from the server.  This MUST
        // be processed immediately, as any subsequent expunge messages will
        // shift the message numbers as a result of previous messages getting
        // removed.  We need to keep our internal cache in sync with the server.
        else if (response.isKeyword("EXPUNGE")) {
            int messageNumber = ((IMAPSizeResponse)response).getSize();
            try {
                Message message = expungeMessage(messageNumber);

                // broadcast the message update.
                notifyMessageRemovedListeners(false, new Message[] {message});
            } catch (MessagingException e) {
            }
            // we handled this one.
            return true;
        }
        // just an update of recently arrived stuff?  Just update the field.
        else if (response.isKeyword("RECENT")) {
            recentMessages = ((IMAPSizeResponse)response).getSize();
            return true;
        }
        // The spec is not particularly clear what types of unsolicited
        // FETCH response can be sent.  The only one that is specifically
        // spelled out is flag updates.  If this is one of those, then
        // handle it.
        else if (response.isKeyword("FETCH")) {
            IMAPFetchResponse fetch = (IMAPFetchResponse)response;
            IMAPFlags flags = (IMAPFlags)fetch.getDataItem(IMAPFetchDataItem.FLAGS);
            // if this is a flags response, get the message and update
            if (flags != null) {
                try {
                    // get the updated message and update the internal state.
                    IMAPMessage message = (IMAPMessage)getMessage(fetch.sequenceNumber);
                    // this shouldn't happen, but it might have been expunged too.
                    if (message != null) {
                        message.updateMessageInformation(fetch);
                    }
                    notifyMessageChangedListeners(MessageChangedEvent.FLAGS_CHANGED, message);
                } catch (MessagingException e) {
                }
                return true;
            }
        }
        // this is a BYE response on our connection.  This forces us to close, but
        // when we return the connection, the pool needs to get rid of it.
        else if (response.isKeyword("BYE")) {
            // this is essentially a close event.  We need to clean everything up
            // and make sure our connection is not returned to the general pool.
            try {
                cleanupFolder(false, true);
            } catch (MessagingException e) {
            }
            return true;
        }

        // not a response the folder knows how to deal with.
        return false;
    }

// The following set of methods are extensions that exist in the Sun implementation.  They
// match the Sun version in intent, but are not 100% compatible because the Sun implementation
// uses com.sun.* class instances as opposed to the org.apache.geronimo.* classes.



    /**
     *   Remove an entry from the access control list for this folder.
     *
     * @param acl    The ACL element to remove.
     *
     * @exception MessagingException
     */
    public synchronized void removeACL(ACL acl) throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            connection.removeACLRights(fullname, acl);
        } finally {
            releaseConnection(connection);
        }
    }


    /**
     *   Add an entry to the access control list for this folder.
     *
     * @param acl    The new ACL to add.
     */
    public synchronized void addACL(ACL acl) throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            connection.setACLRights(fullname, acl);
        } finally {
            releaseConnection(connection);
        }
    }


    /**
     * Add Rights to a given ACL entry.
     *
     * @param acl    The target ACL to update.
     *
     * @exception MessagingException
     */
    public synchronized void addRights(ACL acl) throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            connection.addACLRights(fullname, acl);
        } finally {
            releaseConnection(connection);
        }
    }


    /**
     * Remove ACL Rights from a folder.
     *
     * @param acl    The ACL describing the Rights to remove.
     *
     * @exception MessagingException
     */
    public synchronized void removeRights(ACL acl) throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            connection.removeACLRights(fullname, acl);
        } finally {
            releaseConnection(connection);
        }
    }


    /**
     *   List the rights associated with a given name.
     *
     * @param name   The user name for the Rights.
     *
     * @return The set of Rights associated with the user name.
     * @exception MessagingException
     */
    public synchronized Rights[] listRights(String name) throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            return connection.listACLRights(fullname, name);
        } finally {
            releaseConnection(connection);
        }
    }


    /**
     *   List the rights for the currently authenticated user.
     *
     * @return The set of Rights for the current user.
     * @exception MessagingException
     */
    public synchronized Rights myRights() throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            return connection.getMyRights(fullname);
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Get the quota values assigned to the current folder.
     *
     * @return The Quota information for the folder.
     * @exception MessagingException
     */
    public synchronized Quota[] getQuota() throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            return connection.fetchQuotaRoot(fullname);
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Set the quota value for a quota root
     *
     * @param quota  The new quota information to set.
     *
     * @exception MessagingException
     */
    public synchronized void setQuota(Quota quota) throws MessagingException {
        // ask the store to kindly hook us up with a connection.
        IMAPConnection connection = getConnection();

        try {
            // the connection does the heavy lifting
            connection.setQuota(quota);
        } finally {
            releaseConnection(connection);
        }
    }

    /**
     * Get the set of attributes defined for the folder
     * as the set of capabilities returned when the folder
     * was opened.
     *
     * @return The set of attributes associated with the folder.
     * @exception MessagingException
     */
    public synchronized String[] getAttributes() throws MessagingException {
        // if we don't have the LIST command information for this folder yet,
        // call exists() to force this to be updated so we can return.
        if (listInfo == null) {
            // return a null reference if this is not valid.
            if (!exists()) {
                return null;
            }
        }
        // return a copy of the attributes array.
        return (String[])listInfo.attributes.clone();
    }
}

