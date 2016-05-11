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

import javax.mail.Folder; 
import javax.mail.Message; 
import javax.mail.MessagingException; 
import javax.mail.MethodNotSupportedException;
import javax.mail.Store; 

import org.apache.geronimo.javamail.store.imap.connection.IMAPConnection; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPEnvelope; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPBodyStructure; 

/**
 * An IMAP folder instance for the root of IMAP folder tree.  This has 
 * some of the folder operations disabled. 
 */
public class IMAPRootFolder extends IMAPFolder {
    
    /**
     * Create a default IMAPRootFolder attached to a specific Store instance.
     * 
     * @param store  The Store instance this is the root for.
     */
    public IMAPRootFolder(IMAPStore store) {
        // create a folder with a null string name and the default separator. 
        super(store, "", '/'); 
        // this only holds folders 
        folderType = HOLDS_FOLDERS; 
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
        // The root folder is a dummy one.  Any getFolder() request starting 
        // at the root will use the request name for the full name.  The separator 
        // used in that folder's namespace will be determined when the folder is 
        // first opened. 
        return new IMAPFolder((IMAPStore)store, name, UNDETERMINED);
    }
    
    
    public Folder getParent() {
        // we never have a parent folder 
        return null; 
    }
    
    
    public boolean exists() throws MessagingException {
        // this always exists 
        return true; 
    }
    
    public boolean hasNewMessages() {
        // we don't really exist, so the answer is always false. 
        return false; 
    }
    
    
    public int getMessagesCount() {
        // we don't really exist, so the answer is always 0; 
        return 0; 
    }
    
    
    public int getNewMessagesCount() {
        // we don't really exist, so the answer is always 0; 
        return 0; 
    }
    
    
    public int getUnreadMessagesCount() {
        // we don't really exist, so the answer is always 0; 
        return 0; 
    }
    
    
    public int getDeletedMessagesCount() {
        // we don't really exist, so the answer is always 0; 
        return 0; 
    }
    
    
	public boolean create(int newType) throws MessagingException {
        throw new MethodNotSupportedException("Default IMAP folder cannot be created"); 
    }
    
    public boolean delete(boolean recurse) throws MessagingException {
        throw new MethodNotSupportedException("Default IMAP folder cannot be deleted"); 
    }
    
    
    public boolean rename(boolean recurse) throws MessagingException {
        throw new MethodNotSupportedException("Default IMAP folder cannot be renamed"); 
    }
    
    
    public void appendMessages(Message[] msgs) throws MessagingException {
        throw new MethodNotSupportedException("Messages cannot be appended to Default IMAP folder"); 
    }
    
    
    public Message[] expunge() throws MessagingException {
        throw new MethodNotSupportedException("Messages cannot be expunged from Default IMAP folder"); 
    }
}

