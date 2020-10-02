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

package org.apache.geronimo.javamail.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.geronimo.mail.james.mime4j.codec.QuotedPrintableOutputStream;

/**
 * @version $Rev$ $Date$
 */
public class TraceInputStream extends FilterInputStream {
    // the current debug setting
    protected boolean debug = false;

    // the target trace output stream.
    protected OutputStream traceStream;

    /**
     * Construct a debug trace stream.
     * 
     * @param in
     *            The source input stream.
     * @param traceStream
     *            The side trace stream to which trace data gets written.
     * @param encode
     *            Indicates whether we wish the Trace data to be Q-P encoded.
     */
    public TraceInputStream(InputStream in, OutputStream traceStream, boolean debug, boolean encode) {
        super(in);
        this.debug = debug;
        if (encode) {
            this.traceStream = new QuotedPrintableOutputStream(traceStream, false);
        } else {
            this.traceStream = traceStream;
        }
    }

    /**
     * Set the current setting of the debug trace stream debug flag.
     * 
     * @param d
     *            The new debug flag settings.
     */
    public void setDebug(boolean d) {
        debug = d;
    }

    /**
     * Reads up to len bytes of data from this input stream, placing them directly 
     * into the provided byte array. 
     * 
     * @param b   the provided data buffer. 
     * @param off the starting offset within the buffer for placing the data. 
     * @param len the maximum number of bytes to read. 
     * @return    that number of bytes that have been read and copied into the 
     *            buffer or -1 if an end of stream occurs. 
     * @exception IOException for any I/O errors. 
     */
    public int read(byte b[], int off, int len) throws IOException {
        int count = in.read(b, off, len);
        if (debug && count > 0) {
            traceStream.write(b, off, count);
        }
        return count;
    }

    /**
     * Read the next byte of data from the input stream, returning it as an 
     * int value.  Returns -1 if the end of stream is detected. 
     * 
     * @return The next byte of data or -1 to indicate end-of-stream.      
     * @exception IOException for any I/O errors
     */
    public int read() throws IOException {
        int b = in.read();
        if (debug) {
            traceStream.write(b);
        }
        return b;
    }

    public int read(byte[] b) throws IOException {
        final int read = in.read(b);
        if (debug && read > 0) {
            traceStream.write(b, 0, read);
        }
        return read;
    }
}
