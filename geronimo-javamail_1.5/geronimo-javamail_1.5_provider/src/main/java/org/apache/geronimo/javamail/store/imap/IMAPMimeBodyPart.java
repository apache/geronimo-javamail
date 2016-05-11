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

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;

import javax.activation.DataHandler;
import javax.mail.IllegalWriteException;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeUtility;

import org.apache.geronimo.javamail.store.imap.connection.IMAPBodyStructure;
import org.apache.geronimo.javamail.store.imap.connection.IMAPConnection;
import org.apache.geronimo.mail.util.SessionUtil;


public class IMAPMimeBodyPart extends MimeBodyPart {
    // the message we're part of
    protected IMAPMessage message;
    // the retrieved BODYSTRUCTURE information for this part.
    protected IMAPBodyStructure bodyStructure;
    // the section identifier.  This will be in a format such as 1.2.3, which 
    // would refer to the "third part contained in the second part of the first part"...
    // got all that?  There will be a quiz at the end of class :-)
    protected String section;
    // flag to indicate whether the body part headers have been loaded.
    boolean headersLoaded = false;

    /**
     * Create an instance of a MimeBodyPart within an 
     * IMAP message.
     * 
     * @param message The parent Message instance containing this part.
     * @param bodyStructure
     *                The IMAPBodyStructure information describing the part.
     * @param section The numeric section identifier string for this part.
     *                This is a hierarchical set of numbers describing
     *                how to navigate to the message part on the IMAP
     *                server.  For example, "2.1.3" would be the third
     *                subpart of the first subpart of the second main
     *                message part.
     */
    public IMAPMimeBodyPart(IMAPMessage message, IMAPBodyStructure bodyStructure, String section) {
        super();
        this.message = message;
        this.bodyStructure = bodyStructure;
        this.section = section;
    }


    /**
     * Get the size of the message part.
     * 
     * @return The size information returned in the IMAP body structure.
     * @exception MessagingException
     */
    public int getSize() throws MessagingException {
        return bodyStructure.bodySize;
    }

    /**
     * Get the estimated line count for the body part.
     * 
     * @return The line count information returned by the IMAP 
     *         server.
     * @exception MessagingException
     */
    public int getLineCount() throws MessagingException {
        return bodyStructure.lines;
    }

    /**
     * Get the content type for the body part.
     * 
     * @return The mimetype for the body part, in string format.
     * @exception MessagingException
     */
    public String getContentType() throws MessagingException {
        return bodyStructure.mimeType.toString();
    }

    /**
     * Test if the body part is of a particular MIME type.
     * 
     * @param type   The string MIME-type name.  A wild card * can be
     *               specified for the subpart type.
     * 
     * @return true if the body part matches the give MIME-type.
     * @exception MessagingException
     */
    public boolean isMimeType(String type) throws MessagingException {
        return bodyStructure.mimeType.match(type);
    }

    /**
     * Retrieve the disposition information about this 
     * body part.
     * 
     * @return The disposition information, as a string value.
     * @exception MessagingException
     */
    public String getDisposition() throws MessagingException {
        return bodyStructure.disposition.getDisposition();
    }

    /**
     * Set the disposition information.  The IMAP message 
     * is read-only, so this is an error.
     * 
     * @param disposition
     *               The disposition string.
     * 
     * @exception MessagingException
     */
    public void setDisposition(String disposition) throws MessagingException {
        throw new IllegalWriteException("IMAP message parts are read-only");
    }

    public String getEncoding() throws MessagingException {
        return bodyStructure.transferEncoding;
    }

    public String getContentID() throws MessagingException {
        return bodyStructure.contentID;
    }

    public void setContentID(String id) throws MessagingException {
        throw new IllegalWriteException("IMAP message parts are read-only");
    }

    public String getContentMD5() throws MessagingException {
        return bodyStructure.md5Hash;
    }

    public void setContentMD5(String id) throws MessagingException {
        throw new IllegalWriteException("IMAP message parts are read-only");
    }

    public String getDescription() throws MessagingException {
        String description = bodyStructure.contentDescription;
        if (description != null) {
            try {
                // this could be both folded and encoded.  Return this to usable form.
                return MimeUtility.decodeText(MimeUtility.unfold(description));
            } catch (UnsupportedEncodingException e) {
                // ignore
            }
        }
        // return the raw version for any errors (this might be null also)
        return description;
    }

    public void setDescription(String d, String charset) throws MessagingException {
        throw new IllegalWriteException("IMAP message parts are read-only");
    }

    public String getFileName() throws MessagingException {
        String filename = bodyStructure.disposition.getParameter("filename");
        if (filename == null) {
            filename = bodyStructure.mimeType.getParameter("name");
        }
        
        // if we have a name, we might need to decode this if an additional property is set.
        if (filename != null && SessionUtil.getBooleanProperty(MIME_DECODEFILENAME, false)) {
            try {
                filename = MimeUtility.decodeText(filename);
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException("Unable to decode filename", e);
            }
        }
        
        return filename;
    }

    public void setFileName(String name) throws MessagingException {
        throw new IllegalWriteException("IMAP message parts are read-only");
    }

    protected InputStream getContentStream() throws MessagingException {

        // no content loaded yet?
        if (content == null) {
            // make sure we're still valid
            message.checkValidity();
            // make sure the content is fully loaded
            loadContent();
        }

        // allow the super class to handle creating it from the loaded content.
        return super.getContentStream();
    }


    /**
     * Create the DataHandler object for this message.
     *
     * @return The DataHandler object that processes the content set for this
     *         message.
     * @exception MessagingException
     */
    public synchronized DataHandler getDataHandler() throws MessagingException {
        if (dh == null) {                                                
            // are we working with a multipart message here?
            if (bodyStructure.isMultipart()) {
                dh = new DataHandler(new IMAPMultipartDataSource(message, this, section, bodyStructure));
                return dh;
            }
            else if (bodyStructure.isAttachedMessage()) {
                dh = new DataHandler(new IMAPAttachedMessage(message, section, bodyStructure.nestedEnvelope, 
                    bodyStructure.nestedBody), bodyStructure.mimeType.toString());
                return dh;
            }
        }

        // single part messages get handled the normal way.
        return super.getDataHandler();
    }

    public void setDataHandler(DataHandler content) throws MessagingException {
        throw new IllegalWriteException("IMAP body parts are read-only");
    }

    public void setContent(Object o, String type) throws MessagingException {
        throw new IllegalWriteException("IMAP body parts are read-only");
    }

    public void setContent(Multipart mp) throws MessagingException {
        throw new IllegalWriteException("IMAP body parts are read-only");
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
     * Load the mime part headers into this body part.
     *
     * @exception MessagingException
     */
    protected synchronized void loadHeaders() throws MessagingException {
        // have them already?  Super..
        if (headers != null) {
            return;
        }
                           
        IMAPConnection connection = message.getConnection();
        try {
            // this asks for the MIME subsection of the given section piece.
            headers = connection.fetchHeaders(message.getSequenceNumber(), section);
        } finally {    
            message.releaseConnection(connection);
        }

    }


    /**
     * Load the message content into the BodyPart object.
     *
     * @exception MessagingException
     */
    protected void loadContent() throws MessagingException {
        // if we've loaded this already, just return
        if (content != null) {
            return;
        }
        
        IMAPConnection connection = message.getConnection();
        try {
            // load the content from the server. 
            content = connection.fetchContent(message.getSequenceNumber(), section); 
        } finally {
            message.releaseConnection(connection); 
        }
    }
}

