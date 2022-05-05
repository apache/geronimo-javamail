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
import java.util.List;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentDisposition;
import jakarta.mail.internet.ContentType;

import org.apache.geronimo.mail.util.ResponseFormatException;


public class IMAPBodyStructure extends IMAPFetchDataItem {

    // the MIME type information
    public ContentType mimeType = new ContentType();
    // the content disposition info
    public ContentDisposition disposition = null;
    // the message ID
    public String contentID;
    public String contentDescription;
    public String transferEncoding;
    // size of the message 
    public int bodySize;
    // number of lines, which only applies to text types.
    public int lines = -1;

    // "parts is parts".  If this is a multipart message, we have a body structure item for each subpart.
    public IMAPBodyStructure[] parts;
    // optional dispostiion parameters
    public Map dispositionParameters;
    // language parameters
    public List languages;
    // the MD5 hash
    public String md5Hash;

    // references to nested message information.
    public IMAPEnvelope nestedEnvelope;
    public IMAPBodyStructure nestedBody;


    public IMAPBodyStructure(IMAPResponseTokenizer source) throws MessagingException {
        super(BODYSTRUCTURE);
        parseBodyStructure(source);
    }


    protected void parseBodyStructure(IMAPResponseTokenizer source) throws MessagingException {
        // the body structure needs to start with a left paren
        source.checkLeftParen();

        // if we start with a parentized item, we have a multipart content type.  We need to
        // recurse on each of those as appropriate
        if (source.peek().getType() == '(') {
            parseMultipartBodyStructure(source);
        }
        else {
            parseSinglepartBodyStructure(source);
        }
    }


    protected void parseMultipartBodyStructure(IMAPResponseTokenizer source) throws MessagingException {
        mimeType.setPrimaryType("multipart");
        ArrayList partList = new ArrayList();

        do {
            // parse the subpiece (which might also be a multipart).
            IMAPBodyStructure part = new IMAPBodyStructure(source);
            partList.add(part);
            // we keep doing this as long as we seen parenthized items.
        } while (source.peek().getType() == '(');
        
        parts = (IMAPBodyStructure[])partList.toArray(new IMAPBodyStructure[partList.size()]); 

        // get the subtype (required)
        mimeType.setSubType(source.readString());

        // if the next token is the list terminator, we're done.  Otherwise, we need to read extension
        // data.
        if (source.checkListEnd()) {
            return;
        }
        // read the content parameter information and copy into the ContentType.
        mimeType.setParameterList(source.readParameterList());

        // more optional stuff
        if (source.checkListEnd()) {
            return;
        }

        // go parse the extensions that are common to both single- and multi-part messages.
        parseMessageExtensions(source);
    }


    protected void parseSinglepartBodyStructure(IMAPResponseTokenizer source) throws MessagingException {
        // get the primary and secondary types.
        mimeType.setPrimaryType(source.readString());
        mimeType.setSubType(source.readString());

        // read the parameters associated with the content type.
        mimeType.setParameterList(source.readParameterList());

        // now a bunch of string value parameters
        contentID = source.readStringOrNil();
        contentDescription = source.readStringOrNil();
        transferEncoding = source.readStringOrNil();
        bodySize = source.readInteger();

        // is this an embedded message type?  Embedded messages include envelope and body structure
        // information for the embedded message next.
        if (mimeType.match("message/rfc822")) {
            // parse the nested information
            nestedEnvelope = new IMAPEnvelope(source);
            nestedBody = new IMAPBodyStructure(source);
            lines = source.readInteger();
        }
        // text types include a line count
        else if (mimeType.match("text/*")) {
            lines = source.readInteger();
        }

        // now the optional extension data.  All of these are optional, but must be in the specified order.
        if (source.checkListEnd()) {
            return;
        }

        md5Hash = source.readString();

        // go parse the extensions that are common to both single- and multi-part messages.
        parseMessageExtensions(source);
    }

    /**
     * Parse common message extension information shared between
     * single part and multi part messages.
     *
     * @param source The source tokenizer..
     */
    protected void parseMessageExtensions(IMAPResponseTokenizer source) throws MessagingException {

        // now the optional extension data.  All of these are optional, but must be in the specified order.
        if (source.checkListEnd()) {
            return;
        }

        disposition = new ContentDisposition();
        // now the dispostion.  This is a string, followed by a parameter list.
        if (source.peek(true).getType() == '(') {
            source.checkLeftParen();
            disposition.setDisposition(source.readString());
            disposition.setParameterList(source.readParameterList());
            source.checkRightParen();
        } else if (source.peek(true) == IMAPResponseTokenizer.NIL) {
            source.next();
        } else {
            throw new ResponseFormatException("Expecting NIL or '(' in response");
        }

        // once more
        if (source.checkListEnd()) {
            return;
        }
        // read the language info.
        languages = source.readStringList();
        // next is the body location information.  The mail APIs don't really expose that, so
        // we'll just skip over that.

        // once more
        if (source.checkListEnd()) {
            return;
        }
        // read the location info.
        source.readStringList();

        // we don't recognize any other forms of extension, so just skip over these.
        while (source.notListEnd()) {
            source.skipExtensionItem();
        }

        // step over the closing paren
        source.next();
    }


    /**
     * Tests if a body structure is for a multipart body.
     *
     * @return true if this is a multipart body part, false for a single part.
     */
    public boolean isMultipart() {
        return parts != null;
    }
    
    
    /**
     * Test if this body structure represents an attached message.  If it's a
     * message, this will be a single part of MIME type message/rfc822. 
     * 
     * @return True if this is a nested message type, false for either a multipart or 
     *         a single part of another type.
     */
    public boolean isAttachedMessage() {
        return !isMultipart() && mimeType.match("message/rfc822"); 
    }
}

