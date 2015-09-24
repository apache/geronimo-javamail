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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.geronimo.mail.james.mime4j.codec.QuotedPrintableOutputStream;

/**
 * @version $Rev$ $Date$
 */
public class TraceOutputStream extends FilterOutputStream {
    // the current debug setting
    protected boolean debug = false;

    // the target trace output stream.
    protected OutputStream traceStream;

    /**
     * Construct a debug trace stream.
     *
     * @param out
     *            The target out put stream.
     * @param traceStream
     *            The side trace stream to which trace data gets written.
     * @param encode
     *            Indicates whether we wish the Trace data to be Q-P encoded.
     */
    public TraceOutputStream(OutputStream out, OutputStream traceStream, boolean debug, boolean encode) {
        super(out);
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
     * Write a single byte to the output stream.
     * 
     * @param b      The byte to be written.
     * 
     * @exception IOException
     *                   thrown for any I/O errors.
     */
    public void write(int b) throws IOException {
        if (debug) {
            traceStream.write(b);
        }
        super.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if (this.debug) {
            this.traceStream.write(b, off, len);
        }
        out.write(b, off, len);
    }

    public void write(byte[] b) throws IOException {
        if (this.debug) {
            this.traceStream.write(b);
        }
        out.write(b);
    } 
}
