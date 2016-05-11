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


package org.apache.geronimo.javamail.store.pop3;

import javax.mail.Folder; 
import javax.mail.Message; 
import javax.mail.MessagingException; 
import javax.mail.MethodNotSupportedException;
import javax.mail.Store; 

import org.apache.geronimo.javamail.store.pop3.connection.POP3Connection; 

/**
 * An POP3 folder instance for the root of POP3 folder tree.  This has 
 * some of the folder operations disabled. 
 */
public class POP3RootFolder extends POP3Folder {
    // the inbox folder is the only one that exists 
    protected Folder inbox; 
    
    /**
     * Create a default POP3RootFolder attached to a specific Store instance.
     * 
     * @param store  The Store instance this is the root for.
     */
    public POP3RootFolder(POP3Store store) {
        // create a folder with a null string name and the default separator. 
        super(store, ""); 
        // this only holds folders 
        folderType = HOLDS_FOLDERS; 
        // this folder does exist
        exists = true; 
        // no messages in this folder 
        msgCount = 0; 
    }

    
    /**
     * Get the parent.  This is the root folder, which 
     * never has a parent. 
     * 
     * @return Always returns null. 
     */
    public Folder getParent() {
        // we never have a parent folder 
        return null; 
    }

    /**
     * We have a separator because the root folder is "special". 
     */
    public char getSeparator() throws MessagingException {
        return '/';
    }
    
    /**
     * Retrieve a list of folders that match a pattern.
     * 
     * @param pattern The match pattern.
     * 
     * @return An array of matching folders.
     * @exception MessagingException
     */
    public Folder[] list(String pattern) throws MessagingException {
        // I'm not sure this is correct, but the Sun implementation appears to 
        // return a array containing the inbox regardless of what pattern was specified. 
        return new Folder[] { getInbox() };
    }
    
    /**
     * Get a folder of a given name from the root folder.
     * The Sun implementation seems somewhat inconsistent 
     * here.  The docs for Store claim that only INBOX is 
     * supported, but it will return a Folder instance for any 
     * name.  On the other hand, the root folder raises 
     * an exception for anything but the INBOX.
     * 
     * @param name   The folder name (which must be "INBOX".
     * 
     * @return The inbox folder instance. 
     * @exception MessagingException
     */
    public Folder getFolder(String name) throws MessagingException {
        if (!name.equalsIgnoreCase("INBOX")) {
            throw new MessagingException("Only the INBOX folder is supported"); 
        }
        // return the inbox folder 
        return getInbox(); 
    }
    
    /**
     * Override for the isOpen method.  The root folder can 
     * never be opened. 
     * 
     * @return always returns false. 
     */
    public boolean isOpen() {
        return false; 
    }
    
    public void open(int mode) throws MessagingException {
        throw new MessagingException("POP3 root folder cannot be opened"); 
    }
    
    public void open(boolean expunge) throws MessagingException {
        throw new MessagingException("POP3 root folder cannot be close"); 
    }
    
    
    /**
     * Retrieve the INBOX folder from the root. 
     * 
     * @return The Folder instance for the inbox. 
     * @exception MessagingException
     */
    protected Folder getInbox() throws MessagingException {
        // we're the only place that creates folders, and 
        // we only create the single instance. 
        if (inbox == null) {
            inbox = new POP3Folder((POP3Store)store, "INBOX"); 
        }
        return inbox; 
    }
}


