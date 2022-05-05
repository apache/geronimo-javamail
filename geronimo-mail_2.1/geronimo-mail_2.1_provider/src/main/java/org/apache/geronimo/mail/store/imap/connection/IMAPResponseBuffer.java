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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Simple extension to the ByteArrayOutputStream to allow inspection
 * of the data while it is being accumulated.
 */
public class IMAPResponseBuffer extends ByteArrayOutputStream {

    public IMAPResponseBuffer() {
        super();
    }


    /**
     * Read a character from the byte array output stream buffer
     * at the give position.
     *
     * @param index  The requested index.
     *
     * @return The byte at the target index, or -1 if the index is out of
     *         bounds.
     */
    public int read(int index) {
        if (index >= size()) {
            return -1;
        }
        return buf[index];
    }

    /**
     * Read a buffer of data from the output stream's accumulator
     * buffer.  This will copy the data into a target byte arrain.
     *
     * @param buffer The target byte array for returning the data.
     * @param offset The offset of the source data within the output stream buffer.
     * @param length The desired length.
     *
     * @return The count of bytes transferred into the buffer.
     */
    public int read(byte[] buffer, int offset, int length) {

        int available = size() - offset;
        length = Math.min(length, available);
        // nothing to return?   quit now.
        if (length <= 0) {
            return 0;
        }
        System.arraycopy(buf, offset, buffer, 0, length);
        return length;
    }

    /**
     * Search backwards through the buffer for a given byte.
     *
     * @param target The search character.
     *
     * @return The index relative to the buffer start of the given byte.
     *         Returns -1 if not found.
     */
    public int lastIndex(byte target) {
        for (int i = size() - 1; i > 0; i--) {
            if (buf[i] == target) {
                return i;
            }
        }
        return -1;
    }


    /**
     * Return the last byte written to the output stream.  Returns
     * -1 if the stream is empty.
     *
     * @return The last byte written (or -1 if the stream is empty).
     */
    public int lastByte() {
        if (size() > 0) {
            return buf[size() - 1];
        }
        return -1;
    }


    /**
     * Retrieve an IMAP literal length value from the buffer.  We
     * have a literal length value IFF the last characters written
     * to the buffer have the form "{nnnn}".  This returns the
     * integer value of the info inside the curly braces.  Returns -1
     * if a valid literal length is not found.
     *
     * @return A literal length value, or -1 if we don't have a literal
     *         signature at the end.
     */
    public int getLiteralLength() {
        // was the last byte before the line break the close of the literal length?
        if (lastByte() == '}') {
            // locate the length start
            int literalStart = lastIndex((byte)'{');
            // no matching start, this can't be a literal.
            if (literalStart == -1) {
                return -1;
            }

            try {
                String lenString = new String(buf, literalStart + 1, size() - (literalStart + 2), "US-ASCII");
                try {
                    return Integer.parseInt(lenString);
                } catch (NumberFormatException e) {
                }
            } catch (UnsupportedEncodingException ex) {
                // should never happen
            }
        }
        // not a literal
        return -1;
    }
}

