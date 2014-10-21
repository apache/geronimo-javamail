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

package org.apache.geronimo.javamail.store.imap.connection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.event.FolderEvent;

import org.apache.geronimo.javamail.store.imap.connection.IMAPResponseTokenizer.Token;
import org.apache.geronimo.javamail.util.ConnectionException;

public class IMAPResponseStream {
    protected final int BUFFER_SIZE = 1024;

    // our source input stream
    protected InputStream in;
    // The response buffer
    IMAPResponseBuffer out;
    // the buffer array
    protected byte[] buffer = new byte[BUFFER_SIZE];
    // the current buffer position
    int position;
    // the current buffer read length
    int length;

    public IMAPResponseStream(InputStream in) {
        this.in = in;
        out = new IMAPResponseBuffer();
    }

    public int read() throws IOException {
        // if we can't read any more, that's an EOF condition.
        if (!fillBufferIfNeeded()) {
            return -1;
        }
        // just grab the next character
        return buffer[position++];
    }

    protected boolean fillBufferIfNeeded() throws IOException {
        // used up all of the data in the buffer?
        if (position >= length) {
            int readLength = 0;
            // a read from a network connection can return 0 bytes,
            // so we need to be prepared to handle a spin loop.
            while (readLength == 0) {
                readLength = in.read(buffer, 0, buffer.length);
            }
            // we may have hit the EOF.  Indicate the read failure
            if (readLength == -1) {
                return false;
            }
            // set our new buffer positions.
            position = 0;
            length = readLength;
        }
        return true;
    }


    /**
     * Read a single response line from the input stream, returning
     * a parsed and processed response line.
     *
     * @return A parsed IMAPResponse item using the response data.
     * @exception MessagingException
     */
    public IMAPResponse readResponse() throws MessagingException
      {
        // reset our accumulator
        out.reset();
        // now read a buffer of data
        byte[] data = readData();

        // and create a tokenizer for parsing this down.
        IMAPResponseTokenizer tokenizer = new IMAPResponseTokenizer(data);
        // get the first token.
        Token token = tokenizer.next();

        int type = token.getType();

        // a continuation response.  This will terminate a response set.
        if (type == Token.CONTINUATION) {
            return new IMAPContinuationResponse(data);
        }
        // unsolicited response.  There are multiple forms of these, which might actually be
        // part of the response for the last issued command.
        else if (type == Token.UNTAGGED) {
            // step to the next token, which will give us the type
            token = tokenizer.next();
            // if the token is numeric, then this is a size response in the
            // form "* nn type"
            if (token.isType(Token.NUMERIC)) {
                int size = token.getInteger();

                token = tokenizer.next();

                String keyword = token.getValue();

                // FETCH responses require fairly complicated parsing.  Other
                // size/message updates are fairly generic.
                if (keyword.equals("FETCH")) {
                    return new IMAPFetchResponse(size, data, tokenizer);
                }
                return new IMAPSizeResponse(keyword, size, data);
            }

            // this needs to be an ATOM type, which will tell us what format this untagged
            // response is in.  There are many different untagged formats, some general, some
            // specific to particular command types.
            if (token.getType() != Token.ATOM) {
                try {
                    throw new MessagingException("Unknown server response: " + new String(data, "ISO8859-1"));
                } catch (UnsupportedEncodingException e) {
                    throw new MessagingException("Unknown server response: " + new String(data));
                }
            }

            String keyword = token.getValue();
            // many response are in the form "* OK [keyword value] message".
            if (keyword.equals("OK")) {
                return parseUntaggedOkResponse(data, tokenizer);
            }
            // preauth status response
            else if (keyword.equals("PREAUTH")) {
                return new IMAPServerStatusResponse("PREAUTH", tokenizer.getRemainder(), data);
            }
            // preauth status response
            else if (keyword.equals("BYE")) {
                return new IMAPServerStatusResponse("BYE", tokenizer.getRemainder(), data);
            }
            else if (keyword.equals("BAD")) {
                // these are generally ignored.
                return new IMAPServerStatusResponse("BAD", tokenizer.getRemainder(), data);
            }
            else if (keyword.equals("NO")) {
                // these are generally ignored.
                return new IMAPServerStatusResponse("NO", tokenizer.getRemainder(), data);
            }
            // a complex CAPABILITY response
            else if (keyword.equals("CAPABILITY")) {
                return new IMAPCapabilityResponse(tokenizer, data);
            }
            // a complex LIST response
            else if (keyword.equals("LIST")) {
                return new IMAPListResponse("LIST", data, tokenizer);
            }
            // a complex FLAGS response
            else if (keyword.equals("FLAGS")) {
                // parse this into a flags set.
                return new IMAPFlagsResponse(data, tokenizer);
            }
            // a complex LSUB response (identical in format to LIST)
            else if (keyword.equals("LSUB")) {
                return new IMAPListResponse("LSUB", data, tokenizer);
            }
            // a STATUS response, which will contain a list of elements
            else if (keyword.equals("STATUS")) {
                return new IMAPStatusResponse(data, tokenizer);
            }
            // SEARCH requests return an variable length list of message matches.
            else if (keyword.equals("SEARCH")) {
                return new IMAPSearchResponse(data, tokenizer);
            }
            // ACL requests return an variable length list of ACL values .
            else if (keyword.equals("ACL")) {
                return new IMAPACLResponse(data, tokenizer);
            }
            // LISTRIGHTS requests return a variable length list of RIGHTS values .
            else if (keyword.equals("LISTRIGHTS")) {
                return new IMAPListRightsResponse(data, tokenizer);
            }
            // MYRIGHTS requests return a list of user rights for a mailbox name.
            else if (keyword.equals("MYRIGHTS")) {
                return new IMAPMyRightsResponse(data, tokenizer);
            }
            // QUOTAROOT requests return a list of mailbox quota root names
            else if (keyword.equals("QUOTAROOT")) {
                return new IMAPQuotaRootResponse(data, tokenizer);
            }
            // QUOTA requests return a list of quota values for a root name
            else if (keyword.equals("QUOTA")) {
                return new IMAPQuotaResponse(data, tokenizer);
            }
            else if (keyword.equals("NAMESPACE")) {
                return new IMAPNamespaceResponse(data, tokenizer);
            }
        }
        // begins with a word, this should be the tagged response from the last command.
        else if (type == Token.ATOM) {
            String tag = token.getValue();
            token = tokenizer.next();
            String status = token.getValue();
            //handle plain authentication gracefully, see GERONIMO-6526
            if("+".equals(tag) && status == null) {
            	return new IMAPContinuationResponse(data);
            }                      
            // primary information in one of these is the status field, which hopefully
            // is 'OK'
            return new IMAPTaggedResponse(tag, status, tokenizer.getRemainder(), data);
        }
        try {
            throw new MessagingException("Unknown server response: " + new String(data, "ISO8859-1"));
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Unknown server response: " + new String(data));
        }
    }

    /**
     * Parse an unsolicited OK status response.  These
     * responses are of the form:
     *
     * * OK [keyword arguments ...] message
     *
     * The part in the brackets are optional, but
     * most OK messages will have some sort of update.
     *
     * @param data      The raw message data
     * @param tokenizer The tokenizer being used for this message.
     *
     * @return An IMAPResponse instance for this message.
     */
    private IMAPResponse parseUntaggedOkResponse(byte [] data, IMAPResponseTokenizer tokenizer) throws MessagingException {
        Token token = tokenizer.peek();
        // we might have an optional value here
        if (token.getType() != '[') {
            // this has no tagging item, so there's nothing to be processed
            // later.
            return new IMAPOkResponse("OK", null, tokenizer.getRemainder(), data);
        }
        // skip over the "[" token
        tokenizer.next();
        token = tokenizer.next();
        String keyword = token.getValue();

        // Permanent flags gets special handling
        if (keyword.equals("PERMANENTFLAGS")) {
            return new IMAPPermanentFlagsResponse(data, tokenizer);
        }

        ArrayList arguments = new ArrayList();

        // strip off all of the argument tokens until the "]" list terminator.
        token = tokenizer.next();
        while (token.getType() != ']') {
            arguments.add(token);
            token = tokenizer.next();
        }
        // this has a tagged keyword and arguments that will be processed later.
        return new IMAPOkResponse(keyword, arguments, tokenizer.getRemainder(), data);
    }


    /**
     * Read a "line" of server response data.  An individual line
     * may span multiple line breaks, depending on syntax implications.
     *
     * @return
     * @exception MessagingException
     */
    public byte[] readData() throws MessagingException {
        // reset out buffer accumulator
        out.reset();
        // read until the end of the response into our buffer.
        readBuffer();
        // get the accumulated data.
        return out.toByteArray();
    }

    /**
     * Read a buffer of data.  This accumulates the data into a
     * ByteArrayOutputStream, terminating the processing at a line
     * break.  This also handles line breaks that are the result
     * of literal continuations in the stream.
     *
     * @exception MessagingException
     * @exception IOException
     */
    public void readBuffer() throws MessagingException {
        while (true) {
            int ch = nextByte();
            // potential end of line?  Check the next character, and if it is an end of line,
            // we need to do literal processing.
            if (ch == '\r') {
                int next = nextByte();
                if (next == '\n') {
                    // had a line break, which might be part of a literal marker.  Check for the signature,
                    // and if we found it, continue with the next line.  In any case, we're done with processing here.
                    checkLiteral();
                    return;
                }
            }
            // write this to the buffer.
            out.write(ch);
        }
    }


    /**
     * Check the line just read to see if we're processing a line
     * with a literal value.  Literals are encoded as "{length}\r\n",
     * so if we've read up to the line break, we can check to see
     * if we need to continue reading.
     *
     * If a literal marker is found, we read that many characters
     * from the reader without looking for line breaks.  Once we've
     * read the literal data, we just read the rest of the line
     * as normal (which might also end with a literal marker).
     *
     * @exception MessagingException
     */
    public void checkLiteral() throws MessagingException {
        try {
            // see if we have a literal length signature at the end.
            int length = out.getLiteralLength();

            // -1 means no literal length, so we're done reading this particular response.
            if (length == -1) {
                return;
            }

            // we need to write out the literal line break marker.
            out.write('\r');
            out.write('\n');

            // have something we're supposed to read for the literal?
            if (length > 0) {
                byte[] bytes = new byte[length];

                int offset = 0;

                // The InputStream can return less than the requested length if it needs to block.
                // This may take a couple iterations to get everything, particularly if it's long.
                while (length > 0) {
                    int read = -1;
                    try {
                        read = in.read(bytes, offset, length);
                    } catch (IOException e) {
                        throw new MessagingException("Unexpected read error on server connection", e);
                    }
                    // premature EOF we can't ignore.
                    if (read == -1) {
                        throw new MessagingException("Unexpected end of stream");
                    }
                    length -= read;
                    offset += read;
                }

                // write this out to the output stream.
                out.write(bytes);
            }
            // Now that we have the literal data, we need to read the rest of the response line (which might contain
            // additional literals).  Just recurse on the line reading logic.
            readBuffer();
        } catch (IOException e) {
            e.printStackTrace();
            // this is a byte array output stream...should never happen
        }
    }


    /**
     * Get the next byte from the input stream, handling read errors
     * and EOF conditions as MessagingExceptions.
     *
     * @return The next byte read from the stream.
     * @exception MessagingException
     */
    protected int nextByte() throws MessagingException {
        try {
            int next = in.read();
            if (next == -1) {
                throw new MessagingException("Read error on IMAP server connection");
            }
            return next;
        } catch (IOException e) {
            throw new MessagingException("Unexpected error on server stream", e);
        }
    }


}

