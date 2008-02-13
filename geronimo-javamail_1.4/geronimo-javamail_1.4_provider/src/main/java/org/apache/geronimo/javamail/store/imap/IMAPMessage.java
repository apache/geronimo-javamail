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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

import javax.activation.DataHandler;

import javax.mail.Address; 
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.IllegalWriteException;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.MessageRemovedException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.event.MessageChangedEvent;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MailDateFormat;     
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.apache.geronimo.javamail.store.imap.connection.IMAPBody; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPBodyStructure; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPConnection; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPEnvelope;
import org.apache.geronimo.javamail.store.imap.connection.IMAPFetchDataItem;
import org.apache.geronimo.javamail.store.imap.connection.IMAPFetchResponse;
import org.apache.geronimo.javamail.store.imap.connection.IMAPInternalDate;
import org.apache.geronimo.javamail.store.imap.connection.IMAPInternetHeader;
import org.apache.geronimo.javamail.store.imap.connection.IMAPMessageSize;
import org.apache.geronimo.javamail.store.imap.connection.IMAPUid;

/**
 * IMAP implementation of javax.mail.internet.MimeMessage
 *
 * Only the most basic information is given and
 * Message objects created here is a light-weight reference to the actual Message
 * As per the JavaMail spec items from the actual message will get filled up on demand
 *
 * If some other items are obtained from the server as a result of one call, then the other
 * details are also processed and filled in. For ex if RETR is called then header information
 * will also be processed in addition to the content
 *
 * @version $Rev$ $Date$
 */
public class IMAPMessage extends MimeMessage {
    // the Store we're stored in (which manages the connection and other stuff).
    protected IMAPStore store;

    // the IMAP server sequence number (potentially updated during the life of this message object).
    protected int sequenceNumber;
    // the IMAP uid value;
    protected long uid = -1;
    // the section identifier.  This is only really used for nested messages.  The toplevel version  
    // will be null, and each nested message will set the appropriate part identifier 
    protected String section; 
    // the loaded message envelope (delayed until needed)
    protected IMAPEnvelope envelope;
    // the body structure information (also lazy loaded).
    protected IMAPBodyStructure bodyStructure;
    // the IMAP INTERNALDATE value.
    protected Date receivedDate;
    // the size item, which is maintained separately from the body structure 
    // as it can be retrieved without getting the body structure
    protected int size; 
    // turned on once we've requested the entire header set. 
    protected boolean allHeadersRetrieved = false; 
    // singleton date formatter for this class.
    static protected MailDateFormat dateFormat = new MailDateFormat();


    /**
     * Contruct an IMAPMessage instance.
     * 
     * @param folder   The hosting folder for the message.
     * @param store    The Store owning the article (and folder).
     * @param msgnum   The article message number.  This is assigned by the Folder, and is unique
     *                 for each message in the folder.  The message numbers are only valid
     *                 as long as the Folder is open.
     * @param sequenceNumber The IMAP server manages messages by sequence number, which is subject to
     *                 change whenever messages are expunged.  This is the server retrieval number
     *                 of the message, which needs to be synchronized with status updates
     *                 sent from the server.
     * 
     * @exception MessagingException
     */
	IMAPMessage(IMAPFolder folder, IMAPStore store, int msgnum, int sequenceNumber) {
		super(folder, msgnum);
        this.sequenceNumber = sequenceNumber;
		this.store = store;
        // The default constructor creates an empty Flags item.  We need to clear this out so we 
        // know if the flags need to be fetched from the server when requested.  
        flags = null;
        // make sure this is a totally fresh set of headers.  We'll fill things in as we retrieve them.
        headers = new InternetHeaders();
	}


    /**
     * Override for the Message class setExpunged() method to allow
     * us to do additional cleanup for expunged messages.
     *
     * @param value  The new expunge setting.
     */
    public void setExpunged(boolean value) {
        // super class handles most of the details
        super.setExpunged(value);
        // if we're now expunged, this removes us from the server message sequencing scheme, so
        // we need to invalidate the sequence number.
        if (isExpunged()) {
            sequenceNumber = -1;
        }
    }
    

    /**
     * Return a copy the flags associated with this message.
     *
     * @return a copy of the flags for this message
     * @throws MessagingException if there was a problem accessing the Store
     */
    public synchronized Flags getFlags() throws MessagingException {
        // load the flags, if needed 
        loadFlags(); 
        return super.getFlags(); 
    }


    /**
     * Check whether the supplied flag is set.
     * The default implementation checks the flags returned by {@link #getFlags()}.
     *
     * @param flag the flags to check for
     * @return true if the flags is set
     * @throws MessagingException if there was a problem accessing the Store
     */
    public synchronized boolean isSet(Flags.Flag flag) throws MessagingException {
        // load the flags, if needed 
        loadFlags(); 
        return super.isSet(flag); 
    }

    /**
     * Set or clear a flag value.
     *
     * @param flags  The set of flags to effect.
     * @param set    The value to set the flag to (true or false).
     *
     * @exception MessagingException
     */
    public synchronized void setFlags(Flags flag, boolean set) throws MessagingException {
        // make sure this is in a valid state.
        checkValidity();
        
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        synchronized (folder) {
            IMAPConnection connection = getConnection();

            try {
                // set the flags for this item and update the 
                // internal state with the new values returned from the 
                // server. 
                flags = connection.setFlags(sequenceNumber, flag, set); 
            } finally {
                releaseConnection(connection); 
            }
        }
    }


    /**
     * Return an InputStream instance for accessing the 
     * message content.
     * 
     * @return An InputStream instance for accessing the content
     *         (body) of the message.
     * @exception MessagingException
     * @see javax.mail.internet.MimeMessage#getContentStream()
     */
	protected InputStream getContentStream() throws MessagingException {

        // no content loaded yet?
        if (content == null) {
            // make sure we're still valid
            checkValidity();
            // make sure the content is fully loaded
            loadContent();
        }

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
        // TODO:  The connection is shared on a folder basis, so some of these operations
        // will need to use a common locking object.
        // make sure we're still good
        checkValidity();
        
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        synchronized (folder) {
            IMAPConnection connection = getConnection();
            try {
                IMAPBody body = connection.fetchBody(sequenceNumber, section);
                byte[] outData = body.getContent();

                // write out the data.
                out.write(outData);
            } finally {
                releaseConnection(connection); 
            }
        }
    }
	/******************************************************************
	 * Following is a set of methods that deal with information in the 
	 * envelope.  These methods ensure the enveloper is loaded and   
     * retrieve the information.                         
	 ********************************************************************/
     

    /**
     * Get the message "From" addresses.  This looks first at the
     * "From" headers, and no "From" header is found, the "Sender"
     * header is checked.  Returns null if not found.
     *
     * @return An array of addresses identifying the message from target.  Returns
     *         null if this is not resolveable from the headers.
     * @exception MessagingException
     */
    public Address[] getFrom() throws MessagingException {
        // make sure we've retrieved the envelope information.
        loadEnvelope();
        // make sure we return a copy of the array so this can't be changed. 
        Address[] addresses = envelope.from; 
        if (addresses == null) {
            return null;
        }
        return (Address[])addresses.clone(); 
    }
    

    /**
     * Return the "Sender" header as an address.
     *
     * @return the "Sender" header as an address, or null if not present
     * @throws MessagingException if there was a problem parsing the header
     */
    public Address getSender() throws MessagingException {
        // make sure we've retrieved the envelope information.
        loadEnvelope();
        // make sure we return a copy of the array so this can't be changed. 
        Address[] addresses = envelope.sender; 
        if (addresses == null) {
            return null;
        }
        // There's only a single sender, despite IMAP potentially returning a list
        return addresses[0]; 
    }

    /**
     * Gets the recipients by type.  Returns null if there are no
     * headers of the specified type.  Acceptable RecipientTypes are:
     *
     *   javax.mail.Message.RecipientType.TO
     *   javax.mail.Message.RecipientType.CC
     *   javax.mail.Message.RecipientType.BCC
     *   javax.mail.internet.MimeMessage.RecipientType.NEWSGROUPS
     *
     * @param type   The message RecipientType identifier.
     *
     * @return The array of addresses for the specified recipient types.
     * @exception MessagingException
     */
    public Address[] getRecipients(Message.RecipientType type) throws MessagingException {
        // make sure we've retrieved the envelope information.
        loadEnvelope();
        Address[] addresses = null; 
        
        if (type == Message.RecipientType.TO) {
            addresses = envelope.to; 
        }
        else if (type == Message.RecipientType.CC) {
            addresses = envelope.cc; 
        }
        else if (type == Message.RecipientType.BCC) {
            addresses = envelope.bcc; 
        }
        else {
            // this could be a newsgroup type, which will tickle the message headers. 
            return super.getRecipients(type);
        }
        // make sure we return a copy of the array so this can't be changed. 
        if (addresses == null) {
            return null;
        }
        return (Address[])addresses.clone(); 
    }

    /**
     * Get the ReplyTo address information.  The headers are parsed
     * using the "mail.mime.address.strict" setting.  If the "Reply-To" header does
     * not have any addresses, then the value of the "From" field is used.
     *
     * @return An array of addresses obtained from parsing the header.
     * @exception MessagingException
     */
    public Address[] getReplyTo() throws MessagingException {
        // make sure we've retrieved the envelope information.
        loadEnvelope();
        // make sure we return a copy of the array so this can't be changed. 
        Address[] addresses = envelope.replyTo; 
        if (addresses == null) {
            return null;
        }
        return (Address[])addresses.clone(); 
    }

    /**
     * Returns the value of the "Subject" header.  If the subject
     * is encoded as an RFC 2047 value, the value is decoded before
     * return.  If decoding fails, the raw string value is
     * returned.
     *
     * @return The String value of the subject field.
     * @exception MessagingException
     */
    public String getSubject() throws MessagingException {
        // make sure we've retrieved the envelope information.
        loadEnvelope();
        
        if (envelope.subject == null) {
            return null; 
        }
        // the subject could be encoded.  If there is a decoding error, 
        // return the raw subject string. 
        try {
            return MimeUtility.decodeText(envelope.subject); 
        } catch (UnsupportedEncodingException e) {
            return envelope.subject; 
        }
    }

    /**
     * Get the value of the "Date" header field.  Returns null if
     * if the field is absent or the date is not in a parseable format.
     *
     * @return A Date object parsed according to RFC 822.
     * @exception MessagingException
     */
    public Date getSentDate() throws MessagingException {
        // make sure we've retrieved the envelope information.
        loadEnvelope();
        // just return that directly 
        return envelope.date; 
    }


    /**
     * Get the message received date.
     *
     * @return Always returns the formatted INTERNALDATE, if available.
     * @exception MessagingException
     */
    public Date getReceivedDate() throws MessagingException {
        loadEnvelope();
        return receivedDate; 
    }


    /**
     * Retrieve the size of the message content.  The content will
     * be retrieved from the server, if necessary.
     *
     * @return The size of the content.
     * @exception MessagingException
     */
	public int getSize() throws MessagingException {
        // make sure we've retrieved the envelope information.  We load the 
        // size when we retrieve that. 
        loadEnvelope();
        return size;                          
	}
    

    /**
     * Get a line count for the IMAP message.  This is potentially
     * stored in the Lines article header.  If not there, we return
     * a default of -1.
     *
     * @return The header line count estimate, or -1 if not retrieveable.
     * @exception MessagingException
     */
    public int getLineCount() throws MessagingException {
        loadBodyStructure();
        return bodyStructure.lines; 
    }

    /**
     * Return the IMAP in reply to information (retrieved with the
     * ENVELOPE).
     *
     * @return The in reply to String value, if available.
     * @exception MessagingException
     */
    public String getInReplyTo() throws MessagingException {
        loadEnvelope();
        return envelope.inReplyTo;
    }

    /**
     * Returns the current content type (defined in the "Content-Type"
     * header.  If not available, "text/plain" is the default.
     *
     * @return The String name of the message content type.
     * @exception MessagingException
     */
    public String getContentType() throws MessagingException {
        loadBodyStructure();
        return bodyStructure.mimeType.toString(); 
    }


    /**
     * Tests to see if this message has a mime-type match with the
     * given type name.
     *
     * @param type   The tested type name.
     *
     * @return If this is a type match on the primary and secondare portion of the types.
     * @exception MessagingException
     */
    public boolean isMimeType(String type) throws MessagingException {
        loadBodyStructure();
        return bodyStructure.mimeType.match(type); 
    }

    /**
     * Retrieve the message "Content-Disposition" header field.
     * This value represents how the part should be represented to
     * the user.
     *
     * @return The string value of the Content-Disposition field.
     * @exception MessagingException
     */
    public String getDisposition() throws MessagingException {
        loadBodyStructure();
        if (bodyStructure.disposition != null) {
            return bodyStructure.disposition.getDisposition(); 
        }
        return null; 
    }

    /**
     * Decode the Content-Transfer-Encoding header to determine
     * the transfer encoding type.
     *
     * @return The string name of the required encoding.
     * @exception MessagingException
     */
    public String getEncoding() throws MessagingException {
        loadBodyStructure();
        return bodyStructure.transferEncoding; 
    }

    /**
     * Retrieve the value of the "Content-ID" header.  Returns null
     * if the header does not exist.
     *
     * @return The current header value or null.
     * @exception MessagingException
     */
    public String getContentID() throws MessagingException {
        loadBodyStructure();
        return bodyStructure.contentID;         
    }

    public String getContentMD5() throws MessagingException {
        loadBodyStructure();
        return bodyStructure.md5Hash;         
    }
    
    
    public String getDescription() throws MessagingException {
        loadBodyStructure();
        
        if (bodyStructure.contentDescription == null) {
            return null; 
        }
        // the subject could be encoded.  If there is a decoding error, 
        // return the raw subject string. 
        try {
            return MimeUtility.decodeText(bodyStructure.contentDescription); 
        } catch (UnsupportedEncodingException e) {
            return bodyStructure.contentDescription;
        }
    }

    /**
     * Return the content languages associated with this 
     * message.
     * 
     * @return 
     * @exception MessagingException
     */
    public String[] getContentLanguage() throws MessagingException {
        loadBodyStructure();
        
        if (!bodyStructure.languages.isEmpty()) {
            return (String[])bodyStructure.languages.toArray(new String[bodyStructure.languages.size()]); 
        }
        return null; 
    }

    public String getMessageID() throws MessagingException {
        loadEnvelope();
        return envelope.messageID; 
    }
    
    public void setFrom(Address address) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void addFrom(Address[] address) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setSender(Address address) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setRecipients(Message.RecipientType type, String address) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void addRecipients(Message.RecipientType type, Address[] address) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setReplyTo(Address[] address) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setSubject(String subject) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setSubject(String subject, String charset) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setSentDate(Date sent) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setDisposition(String disposition) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setContentID(String cid) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }

    public void setContentMD5(String md5) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }

    public void setDescription(String description) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    
    public void setDescription(String description, String charset) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }

    public void setContentLanguage(String[] languages) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }
    

	/******************************************************************
	 * Following is a set of methods that deal with headers
	 * These methods are just overrides on the superclass methods to
     * allow lazy loading of the header information.
	 ********************************************************************/

	public String[] getHeader(String name) throws MessagingException {
        loadHeaders();
		return headers.getHeader(name);
	}

	public String getHeader(String name, String delimiter) throws MessagingException {
        loadHeaders();
		return headers.getHeader(name, delimiter);
	}

	public Enumeration getAllHeaders() throws MessagingException {
        loadHeaders();
		return headers.getAllHeaders();
	}

	public Enumeration getMatchingHeaders(String[] names)  throws MessagingException {
        loadHeaders();
		return headers.getMatchingHeaders(names);
	}

	public Enumeration getNonMatchingHeaders(String[] names) throws MessagingException {
        loadHeaders();
		return headers.getNonMatchingHeaders(names);
	}

	public Enumeration getAllHeaderLines() throws MessagingException {
        loadHeaders();
		return headers.getAllHeaderLines();
	}

	public Enumeration getMatchingHeaderLines(String[] names) throws MessagingException {
        loadHeaders();
		return headers.getMatchingHeaderLines(names);
	}

	public Enumeration getNonMatchingHeaderLines(String[] names) throws MessagingException {
        loadHeaders();
		return headers.getNonMatchingHeaderLines(names);
	}

    // the following are overrides for header modification methods.  These messages are read only,
    // so the headers cannot be modified.
    public void addHeader(String name, String value) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }

    public void setHeader(String name, String value) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }


    public void removeHeader(String name) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }

    public void addHeaderLine(String line) throws MessagingException {
        throw new IllegalWriteException("IMAP messages are read-only");
    }

	/**
	 * We cannot modify these messages
	 */
	public void saveChanges() throws MessagingException {
		throw new IllegalWriteException("IMAP messages are read-only");
	}


    /**
     * Utility method for synchronizing IMAP envelope information and
     * the message headers.
     *
     * @param header    The target header name.
     * @param addresses The update addresses.
     */
    protected void updateHeader(String header, InternetAddress[] addresses) throws MessagingException {
        if (addresses != null) {
            headers.addHeader(header, InternetAddress.toString(envelope.from));
        }
    }

    /**
     * Utility method for synchronizing IMAP envelope information and
     * the message headers.
     *
     * @param header    The target header name.
     * @param address   The update address.
     */
    protected void updateHeader(String header, Address address) throws MessagingException {
        if (address != null) {
            headers.setHeader(header, address.toString());
        }
    }

    /**
     * Utility method for synchronizing IMAP envelope information and
     * the message headers.
     *
     * @param header    The target header name.
     * @param value     The update value.
     */
    protected void updateHeader(String header, String value) throws MessagingException {
        if (value != null) {
            headers.setHeader(header, value);
        }
    }


    /**
     * Create the DataHandler object for this message.
     *
     * @return The DataHandler object that processes the content set for this
     *         message.
     * @exception MessagingException
     */
    public synchronized DataHandler getDataHandler() throws MessagingException {
        // check the validity and make sure we have the body structure information. 
        checkValidity();
        loadBodyStructure();
        if (dh == null) {
            // are we working with a multipart message here?
            if (bodyStructure.isMultipart()) {
                dh = new DataHandler(new IMAPMultipartDataSource(this, this, section, bodyStructure));
                return dh;
            }
            else if (bodyStructure.isAttachedMessage()) {
                dh = new DataHandler(new IMAPAttachedMessage(this, section, bodyStructure.nestedEnvelope, bodyStructure.nestedBody), 
                     bodyStructure.mimeType.toString()); 
                return dh;
            }
        }

        // single part messages get handled the normal way.
        return super.getDataHandler();
    }

    public void setDataHandler(DataHandler content) throws MessagingException {
        throw new IllegalWriteException("IMAP body parts are read-only");
    }

    /**
     * Update the message headers from an input stream.
     *
     * @param in     The InputStream source for the header information.
     *
     * @exception MessagingException
     */
    public void updateHeaders(InputStream in) throws MessagingException {
        // wrap a stream around the reply data and read as headers.
        headers = new InternetHeaders(in);
        allHeadersRetrieved = true;
    }
    
    /**
     * Load the flag set for this message from the server. 
     * 
     * @exception MessagingeException
     */
    public void loadFlags() throws MessagingException {
        // make sure this is in a valid state.
        checkValidity();
        // if the flags are already loaded, nothing to do 
        if (flags != null) {
            return; 
        }
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        synchronized (folder) {
            IMAPConnection connection = getConnection();

            try {
                // fetch the flags for this item. 
                flags = connection.fetchFlags(sequenceNumber); 
            } finally {
                releaseConnection(connection); 
            }
        }
    }


    /**
     * Retrieve the message raw message headers from the IMAP server, synchronizing with the existing header set.
     *
     * @exception MessagingException
     */
    protected synchronized void loadHeaders() throws MessagingException {
        // don't retrieve if already loaded.
        if (allHeadersRetrieved) {
            return;
        }

        // make sure this is in a valid state.
        checkValidity();
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        synchronized (folder) {
            IMAPConnection connection = getConnection();

            try {
                // get the headers and set 
                headers = connection.fetchHeaders(sequenceNumber, section);
                // we have the entire header set, not just a subset. 
                allHeadersRetrieved = true;                                               
            } finally {
                releaseConnection(connection); 
            }
        }
    }


    /**
     * Retrieve the message envelope from the IMAP server, synchronizing the headers with the
     * information.
     *
     * @exception MessagingException
     */                                
    protected synchronized void loadEnvelope() throws MessagingException {
        // don't retrieve if already loaded.
        if (envelope != null) {
            return;
        }

        // make sure this is in a valid state.
        checkValidity();
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        synchronized (folder) {
            IMAPConnection connection = getConnection();
            try {
                // fetch the envelope information for this
                List fetches = connection.fetchEnvelope(sequenceNumber);
                // now process all of the fetch responses before releasing the folder lock.  
                // it's possible that an unsolicited update on another thread might try to 
                // make an update, causing a potential deadlock. 
                for (int i = 0; i < fetches.size(); i++) {
                    // get the returned data items from each of the fetch responses
                    // and process. 
                    IMAPFetchResponse fetch = (IMAPFetchResponse)fetches.get(i);
                    // update the internal info 
                    updateMessageInformation(fetch); 
                }
            } finally {
                releaseConnection(connection); 
            }
        }
    }


    /**
     * Retrieve the message envelope from the IMAP server, synchronizing the headers with the
     * information.
     *
     * @exception MessagingException
     */
    protected synchronized void updateEnvelope(IMAPEnvelope envelope) throws MessagingException {
        // set the envelope item 
        this.envelope = envelope; 

        // copy header type information from the envelope into the headers.
        updateHeader("From", envelope.from);
        if (envelope.sender != null) {
            // we can only have a single sender, even though the envelope theoretically supports more.
            updateHeader("Sender", envelope.sender[0]);
        }
        updateHeader("To", envelope.to);
        updateHeader("Cc", envelope.cc);
        updateHeader("Bcc", envelope.bcc);
        updateHeader("Reply-To", envelope.replyTo);
        // NB:  This is already in encoded form, if needed.
        updateHeader("Subject", envelope.subject);
        updateHeader("Message-ID", envelope.messageID);
    }


    /**
     * Retrieve the BODYSTRUCTURE information from the IMAP server.
     *
     * @exception MessagingException
     */
    protected synchronized void loadBodyStructure() throws MessagingException {
        // don't retrieve if already loaded.
        if (bodyStructure != null) {
            return;
        }

        // make sure this is in a valid state.
        checkValidity();
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        synchronized (folder) {
            IMAPConnection connection = getConnection();
            try {
                // fetch the envelope information for this
                bodyStructure = connection.fetchBodyStructure(sequenceNumber);
                // go update all of the information 
            } finally {
                releaseConnection(connection); 
            }
            
            // update this before we release the folder lock so we can avoid 
            // deadlock. 
            updateBodyStructure(bodyStructure); 
        }
    }


    /**
     * Update the BODYSTRUCTURE information from the IMAP server.
     *
     * @exception MessagingException
     */
    protected synchronized void updateBodyStructure(IMAPBodyStructure structure) throws MessagingException {
        // save the reference. 
        bodyStructure = structure; 
        // now update various headers with the information from the body structure 

        // now update header information with the body structure data.
        if (bodyStructure.lines != -1) {
            updateHeader("Lines", Integer.toString(bodyStructure.lines));
        }

        // languages are a little more complicated
        if (bodyStructure.languages != null) {
            // this is a duplicate of what happens in the super class, but 
            // the superclass methods call setHeader(), which we override and 
            // throw an exception for.  We need to set the headers ourselves. 
            if (bodyStructure.languages.size() == 1) {
                updateHeader("Content-Language", (String)bodyStructure.languages.get(0));
            }
            else {
                StringBuffer buf = new StringBuffer(bodyStructure.languages.size() * 20);
                buf.append(bodyStructure.languages.get(0));
                for (int i = 1; i < bodyStructure.languages.size(); i++) {
                    buf.append(',').append(bodyStructure.languages.get(i));
                }
                updateHeader("Content-Language", buf.toString());
            }
        }

        updateHeader("Content-Type", bodyStructure.mimeType.toString());
        if (bodyStructure.disposition != null) {
            updateHeader("Content-Disposition", bodyStructure.disposition.toString());
        }

        updateHeader("Content-Transfer-Encoding", bodyStructure.transferEncoding);
        updateHeader("Content-ID", bodyStructure.contentID);
        // NB:  This is already in encoded form, if needed.
        updateHeader("Content-Description", bodyStructure.contentDescription);
    }


    /**
     * Load the message content into the Message object.
     *
     * @exception MessagingException
     */
    protected void loadContent() throws MessagingException {
        // if we've loaded this already, just return
        if (content != null) {
            return;
        }
        
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        synchronized (folder) {
            IMAPConnection connection = getConnection();
            try {
                // load the content from the server. 
                content = connection.fetchContent(getSequenceNumber(), section); 
            } finally {
                releaseConnection(connection); 
            }
        }
    }

    
    /**
     * Retrieve the sequence number assigned to this message.
     *
     * @return The messages assigned sequence number.  This maps back to the server's assigned number for
     * this message.
     */
    int getSequenceNumber() {
        return sequenceNumber;
    }
    
    /**
     * Set the sequence number for the message.  This 
     * is updated whenever messages get expunged from 
     * the folder. 
     * 
     * @param s      The new sequence number.
     */
    void setSequenceNumber(int s) {
        sequenceNumber = s; 
    }


    /**
     * Retrieve the message UID value.
     *
     * @return The assigned UID value, if we have the information.
     */
    long getUID() {
        return uid;
    }

    /**
     * Set the message UID value.
     *
     * @param uid    The new UID value.
     */
    void setUID(long uid) {
        this.uid = uid;
    }

    
    /**
     * get the current connection pool attached to the folder.  We need
     * to do this dynamically, to A) ensure we're only accessing an
     * currently open folder, and B) to make sure we're using the
     * correct connection attached to the folder.
     *
     * @return A connection attached to the hosting folder.
     */
    protected IMAPConnection getConnection() throws MessagingException {
        // the folder owns everything.
        return ((IMAPFolder)folder).getMessageConnection();
    }
    
    /**
     * Release the connection back to the Folder after performing an operation 
     * that requires a connection.
     * 
     * @param connection The previously acquired connection.
     */
    protected void releaseConnection(IMAPConnection connection) throws MessagingException {
        // the folder owns everything.
        ((IMAPFolder)folder).releaseMessageConnection(connection);
    }


    /**
     * Check the validity of the current message.  This ensures that
     * A) the folder is currently open, B) that the message has not
     * been expunged (after getting the latest status from the server).
     *
     * @exception MessagingException
     */
    protected void checkValidity() throws MessagingException {
        checkValidity(false); 
    }


    /**
     * Check the validity of the current message.  This ensures that
     * A) the folder is currently open, B) that the message has not
     * been expunged (after getting the latest status from the server).
     *
     * @exception MessagingException
     */
    protected void checkValidity(boolean update) throws MessagingException {
        // we need to ensure that we're the only ones with access to the folder's 
        // message cache any time we need to talk to the server.  This needs to be 
        // held until after we release the connection so that any pending EXPUNGE 
        // untagged responses are processed before the next time the folder connection is 
        // used. 
        if (update) {
            synchronized (folder) {
                // have the connection update the folder status.  This might result in this message
                // changing its state to expunged.  It might also result in an exception if the
                // folder has been closed.
                IMAPConnection connection = getConnection(); 

                try {
                    connection.updateMailboxStatus();
                } finally {
                    // this will force any expunged messages to be processed before we release 
                    // the lock. 
                    releaseConnection(connection); 
                }
            }
        }

        // now see if we've been expunged, this is a bad op on the message.
        if (isExpunged()) {
            throw new MessageRemovedException("Illegal opertion on a deleted message");
        }
    }
    
    
    /**
     * Evaluate whether this message requires any of the information 
     * in a FetchProfile to be fetched from the server.  If the messages 
     * already contains the information in the profile, it returns false.
     * This allows IMAPFolder to optimize fetch() requests to just the 
     * messages that are missing any of the requested information.
     * 
     * NOTE:  If any of the items in the profile are missing, then this 
     * message will be updated with ALL of the items.  
     * 
     * @param profile The FetchProfile indicating the information that should be prefetched.
     * 
     * @return true if any of the profile information requires fetching.  false if this 
     *         message already contains the given information.
     */
    protected boolean evaluateFetch(FetchProfile profile) {
        // the fetch profile can contain a number of different item types.  Validate
        // whether we need any of these and return true on the first mismatch. 
        
        // the UID is a common fetch request, put it first. 
        if (profile.contains(UIDFolder.FetchProfileItem.UID) && uid == -1) {
            return true; 
        }
        if (profile.contains(FetchProfile.Item.ENVELOPE) && envelope == null) {
            return true; 
        }
        if (profile.contains(FetchProfile.Item.FLAGS) && flags == null) {
            return true; 
        }
        if (profile.contains(FetchProfile.Item.CONTENT_INFO) && bodyStructure == null) {
            return true; 
        }
        // The following profile items are our implementation of items that the 
        // Sun IMAPFolder implementation supports.  
        if (profile.contains(IMAPFolder.FetchProfileItem.HEADERS) && !allHeadersRetrieved) {
            return true; 
        }
        if (profile.contains(IMAPFolder.FetchProfileItem.SIZE) && bodyStructure.bodySize < 0) {
            return true; 
        }
        // last bit after checking each of the information types is to see if 
        // particular headers have been requested and whether those are on the 
        // set we do have loaded. 
        String [] requestedHeaders = profile.getHeaderNames(); 
         
        // ok, any missing header in the list is enough to force us to request the 
        // information. 
        for (int i = 0; i < requestedHeaders.length; i++) {
            if (headers.getHeader(requestedHeaders[i]) == null) {
                return true; 
            }
        }
        // this message, at least, does not need anything fetched. 
        return false; 
    }
    
    /**
     * Update a message instance with information retrieved via an IMAP FETCH 
     * command.  The command response for this message may contain multiple pieces
     * that we need to process.
     * 
     * @param response The response line, which may contain multiple data items.
     * 
     * @exception MessagingException
     */
    void updateMessageInformation(IMAPFetchResponse response) throws MessagingException {
        // get the list of data items associated with this response.  We can have 
        // a large number of items returned in a single update. 
        List items = response.getDataItems(); 
        
        for (int i = 0; i < items.size(); i++) {
            IMAPFetchDataItem item = (IMAPFetchDataItem)items.get(i); 
            
            switch (item.getType()) {
                // if the envelope has been requested, we'll end up with all of these items. 
                case IMAPFetchDataItem.ENVELOPE:
                    // update the envelope and map the envelope items into the headers. 
                    updateEnvelope((IMAPEnvelope)item);
                    break;
                case IMAPFetchDataItem.INTERNALDATE:
                    receivedDate = ((IMAPInternalDate)item).getDate();;
                    break;
                case IMAPFetchDataItem.SIZE:
                    size = ((IMAPMessageSize)item).size;      
                    break;
                case IMAPFetchDataItem.UID:
                    uid = ((IMAPUid)item).uid;       
                    // make sure the folder knows about the UID update. 
                    ((IMAPFolder)folder).addToUidCache(new Long(uid), this); 
                    break; 
                case IMAPFetchDataItem.BODYSTRUCTURE: 
                    updateBodyStructure((IMAPBodyStructure)item); 
                    break; 
                    // a partial or full header update 
                case IMAPFetchDataItem.HEADER:
                {
                    // if we've fetched the complete set, then replace what we have 
                    IMAPInternetHeader h = (IMAPInternetHeader)item; 
                    if (h.isComplete()) {
                        // we've got a complete header set now. 
                        this.headers = h.headers;     
                        allHeadersRetrieved = true; 
                    }
                    else {
                        // need to merge the requested headers in with 
                        // our existing set.  We need to be careful, since we 
                        // don't want to add duplicates. 
                        mergeHeaders(h.headers); 
                    }
                }
                default:
            }
        }
    }
    
    
    /**
     * Merge a subset of the requested headers with our existing partial set. 
     * The new set will contain all headers requested from the server, plus 
     * any of our existing headers that were not included in the retrieved set.
     * 
     * @param newHeaders The retrieved set of headers.
     */
    protected synchronized void mergeHeaders(InternetHeaders newHeaders) {
        // This is sort of tricky to manage.  The input headers object is a fresh set  
        // retrieved from the server, but it's a subset of the headers.  Our existing set
        // might not be complete, but it may contain duplicates of information in the 
        // retrieved set, plus headers that are not in the retrieved set.  To keep from 
        // adding duplicates, we'll only add headers that are not in the retrieved set to 
        // that set.   
        
        // start by running through the list of headers 
        Enumeration e = headers.getAllHeaders(); 
        
        while (e.hasMoreElements()) {
            Header header = (Header)e.nextElement(); 
            // if there are no headers with this name in the new set, then 
            // we can add this.  Note that to add the header, we need to 
            // retrieve all instances by this name and add them as a unit.  
            // When we hit one of the duplicates again with the enumeration, 
            // we'll skip it then because the merge target will have everything. 
            if (newHeaders.getHeader(header.getName()) == null) {
                // get all occurrences of this name and stuff them into the 
                // new list 
                String name = header.getName(); 
                String[] a = headers.getHeader(name); 
                for (int i = 0; i < a.length; i++) {
                    newHeaders.addHeader(name, a[i]); 
                }
            }
        }
        // and replace the current header set
        headers = newHeaders; 
    }
}
