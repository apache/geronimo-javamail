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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.IllegalWriteException;
import javax.mail.MessagingException;
import javax.mail.event.MessageChangedEvent;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeMessage;

import org.apache.geronimo.javamail.store.pop3.connection.POP3Connection;

/**
 * POP3 implementation of javax.mail.internet.MimeMessage
 * 
 * Only the most basic information is given and Message objects created here is
 * a light-weight reference to the actual Message As per the JavaMail spec items
 * from the actual message will get filled up on demand
 * 
 * If some other items are obtained from the server as a result of one call,
 * then the other details are also processed and filled in. For ex if RETR is
 * called then header information will also be processed in addition to the
 * content
 * 
 * @version $Rev$ $Date$
 */
public class POP3Message extends MimeMessage {
    // the size of the message, in bytes
    protected int msgSize = -1;
    // the size of the headers.  We keep this around, as it's needed to 
    // properly calculate the size of the message 
    protected int headerSize = -1;
    // the UID value retrieved from the server 
    protected String uid; 
    // the raw message data from loading the message
    protected byte[] messageData; 

    /**
     * Create a new POP3 message associated with a folder.
     * 
     * @param folder The owning folder.
     * @param msgnum The message sequence number in the folder.
     */
    protected POP3Message(Folder folder, int msgnum) {
        super(folder, msgnum);
        this.session = session;
        // force the headers to empty so we'll load them the first time they're referenced. 
        this.headers = null; 
    }

    /**
     * Get an InputStream for reading the message content. 
     * 
     * @return An InputStream instance initialized to read the message 
     *         content.
     * @exception MessagingException
     */
    protected InputStream getContentStream() throws MessagingException {
        // make sure the content is loaded first 
        loadContent(); 
        // allow the super class to handle creating it from the loaded content.
        return super.getContentStream();
    }


    /**
     * Write out the byte data to the provided output stream.
     *
     * @param out    The target stream.
     *
     * @exception IOException
     * @exception MessagingException
     */
    public void writeTo(OutputStream out) throws IOException, MessagingException {
        // make sure we have everything loaded 
        loadContent(); 
        // just write out the raw message data 
        out.write(messageData); 
    }
    

    /**
     * Set a flag value for this Message.  The flags are 
     * only set locally, not the server.  When the folder 
     * is closed, any messages with the Deleted flag set 
     * will be removed from the server. 
     * 
     * @param newFlags The new flag values.
     * @param set      Indicates whether this is a set or an unset operation.
     * 
     * @exception MessagingException
     */
    public void setFlags(Flags newFlags, boolean set) throws MessagingException {
        Flags oldFlags = (Flags) flags.clone();
        super.setFlags(newFlags, set);

        if (!flags.equals(oldFlags)) {
            ((POP3Folder) folder).notifyMessageChangedListeners(MessageChangedEvent.FLAGS_CHANGED, this);
        }
    }

    /**
     * Unconditionally load the headers from an inputstream. 
     * When retrieving content, we get back the entire message, 
     * including the headers.  This allows us to skip over 
     * them to reach the content, even if we already have 
     * headers loaded. 
     * 
     * @param in     The InputStream with the header data.
     * 
     * @exception MessagingException
     */
    protected void loadHeaders(InputStream in) throws MessagingException {
        try {
            headerSize = in.available(); 
            // just load and replace the haders 
            headers = new InternetHeaders(in);
            headerSize -= in.available(); 
        } catch (IOException e) {
            // reading from a ByteArrayInputStream...this should never happen. 
        }
    }
    
    /**
     * Lazy loading of the message content. 
     * 
     * @exception MessagingException
     */
    protected void loadContent() throws MessagingException {
        if (content == null) {
            POP3Connection connection = getConnection(); 
            try {
                // retrieve (and save the raw message data 
                messageData = connection.retrieveMessageData(msgnum);
            } finally {
                // done with the connection
                releaseConnection(connection); 
            }
            // now create a input stream for splitting this into headers and 
            // content 
            ByteArrayInputStream in = new ByteArrayInputStream(messageData); 
            
            // the Sun implementation has an option that forces headers loaded using TOP 
            // should be forgotten when retrieving the message content.  This is because 
            // some POP3 servers return different results for TOP and RETR.  Since we need to 
            // retrieve the headers anyway, and this set should be the most complete, we'll 
            // just replace the headers unconditionally. 
            loadHeaders(in);
            // load headers stops loading at the header terminator.  Everything 
            // after that is content. 
            loadContent(in);
        }
    }

    /**
     * Load the message content from the server.
     * 
     * @param stream A ByteArrayInputStream containing the message content.
     *               We explicitly use ByteArrayInputStream because
     *               there are some optimizations that can take advantage
     *               of the fact it is such a stream.
     * 
     * @exception MessagingException
     */
    protected void loadContent(ByteArrayInputStream stream) throws MessagingException {
        // since this is a byte array input stream, available() returns reliable value. 
        content = new byte[stream.available()];
        try {
            // just read everything in to the array 
            stream.read(content); 
        } catch (IOException e) {
            // should never happen 
            throw new MessagingException("Error loading content info", e);
        }
    }

    /**
     * Get the size of the message.
     * 
     * @return The calculated message size, in bytes. 
     * @exception MessagingException
     */
    public int getSize() throws MessagingException {
        if (msgSize < 0) {
            // we need to get the headers loaded, since we need that information to calculate the total 
            // content size without retrieving the content. 
            loadHeaders();  
            
            POP3Connection connection = getConnection(); 
            try {

                // get the total message size, and adjust by size of the headers to get the content size. 
                msgSize = connection.retrieveMessageSize(msgnum) - headerSize; 
            } finally {
                // done with the connection
                releaseConnection(connection); 
            }
        }
        return msgSize;
    }

    /**
     * notice that we pass zero as the no of lines from the message,as it
     * doesn't serv any purpose to get only a certain number of lines.
     * 
     * However this maybe important if a mail client only shows 3 or 4 lines of
     * the message in the list and then when the user clicks they would load the
     * message on demand.
     * 
     */
    protected void loadHeaders() throws MessagingException {
        if (headers == null) {
            POP3Connection connection = getConnection(); 
            try {
                loadHeaders(connection.retrieveMessageHeaders(msgnum)); 
            } finally {
                // done with the connection
                releaseConnection(connection); 
            }
        }
    }
    
    /**
     * Retrieve the message UID from the server.
     * 
     * @return The string UID value. 
     * @exception MessagingException
     */
    protected String getUID() throws MessagingException {
        if (uid == null) {
            POP3Connection connection = getConnection(); 
            try {
                uid = connection.retrieveMessageUid(msgnum); 
            } finally {
                // done with the connection
                releaseConnection(connection); 
            }
        }
        return uid; 
    }
    
    // The following are methods that deal with all header accesses.  Most of the 
    // methods that retrieve information from the headers funnel through these, so we 
    // can lazy-retrieve the header information. 

    public String[] getHeader(String name) throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getHeader(name); 
    }

    public String getHeader(String name, String delimiter) throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getHeader(name, delimiter); 
    }

    public Enumeration getAllHeaders() throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getAllHeaders(); 
    }

    public Enumeration getMatchingHeaders(String[] names) throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getMatchingHeaders(names); 
    }

    public Enumeration getNonMatchingHeaders(String[] names) throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getNonMatchingHeaders(names); 
    }

    public Enumeration getAllHeaderLines() throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getAllHeaderLines();       
    }

    public Enumeration getMatchingHeaderLines(String[] names) throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getMatchingHeaderLines(names); 
    }

    public Enumeration getNonMatchingHeaderLines(String[] names) throws MessagingException {
        // make sure the headers are loaded 
        loadHeaders(); 
        // allow the super class to handle everything from here 
        return super.getNonMatchingHeaderLines(names); 
    }

    // the following are overrides for header modification methods. These
    // messages are read only,
    // so the headers cannot be modified.
    public void addHeader(String name, String value) throws MessagingException {
        throw new IllegalWriteException("POP3 messages are read-only");
    }

    public void setHeader(String name, String value) throws MessagingException {
        throw new IllegalWriteException("POP3 messages are read-only");
    }

    public void removeHeader(String name) throws MessagingException {
        throw new IllegalWriteException("POP3 messages are read-only");
    }

    public void addHeaderLine(String line) throws MessagingException {
        throw new IllegalWriteException("POP3 messages are read-only");
    }

    /**
     * We cannot modify these messages
     */
    public void saveChanges() throws MessagingException {
        throw new IllegalWriteException("POP3 messages are read-only");
    }

    
    /**
     * get the current connection pool attached to the folder.  We need
     * to do this dynamically, to A) ensure we're only accessing an
     * currently open folder, and B) to make sure we're using the
     * correct connection attached to the folder.
     *
     * @return A connection attached to the hosting folder.
     */
    protected POP3Connection getConnection() throws MessagingException {
        // the folder owns everything.
        return ((POP3Folder)folder).getMessageConnection();
    }
    
    /**
     * Release the connection back to the Folder after performing an operation 
     * that requires a connection.
     * 
     * @param connection The previously acquired connection.
     */
    protected void releaseConnection(POP3Connection connection) throws MessagingException {
        // the folder owns everything.
        ((POP3Folder)folder).releaseMessageConnection(connection);
    }
}
