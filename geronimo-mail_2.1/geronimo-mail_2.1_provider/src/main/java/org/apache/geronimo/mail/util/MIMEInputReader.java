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

package org.apache.geronimo.mail.util;

import java.io.IOException;
import java.io.Reader; 

/**
 * An implementation of an OutputStream that performs MIME linebreak
 * canonicalization and "byte-stuff" so that data content does not get mistaken
 * for a message data-end marker (CRLF.CRLF)l
 * 
 * @version $Rev$ $Date$
 */
public class MIMEInputReader extends Reader {

    // the wrappered output stream.
    protected Reader source;

    // a flag to indicate we've just processed a line break. This is used for
    // byte stuffing purposes. This
    // is initially true, because if the first character of the content is a
    // period, we need to byte-stuff
    // immediately.
    protected boolean atLineBreak = true;
    // we've hit the terminating marker on the data
    protected boolean endOfData = false; 
    

    /**
     * Create an input reader that reads from the source input reader  
     * 
     * @param out
     *            The wrapped Reader        
     */
    public MIMEInputReader(Reader source) {
        this.source = source; 
    }
    
    /**
     * Concrete implementation of the Reader read() 
     * abstract method.  This appears to be the only 
     * abstract method, so all of the other reads must 
     * funnel through this method. 
     * 
     * @param buffer The buffer to fill.
     * @param off    The offset to start adding characters.
     * @param len    The number of requested characters.
     * 
     * @return The actual count of characters read.  Returns -1 
     *         if we hit an EOF without reading any characters.
     * @exception IOException
     */
    public int read(char buffer[], int off, int len) throws IOException {
        // we've been asked for nothing, we'll return nothing. 
        if (len == 0) {
            return 0; 
        }
        
        // have we hit the end of data?  Return a -1 indicator
        if (endOfData) {
            return -1; 
        }
        
        // number of bytes read 
        int bytesRead = 0; 
        
        int lastRead; 
        
        while (bytesRead < len && (lastRead = source.read()) >= 0) {
            // We are checking for the end of a multiline response
            // the format is .CRLF
            
            // we also have to check for byte-stuffing situation 
            // where we remove a leading period.  
            if (atLineBreak && lastRead == '.') {
                // step to the next character 
                lastRead = source.read();
                // we have ".CR"...this is our end of stream 
                // marker.  Consume the LF from the reader and return 
                if (lastRead == '\r') {
                    source.read(); 
                    // no more reads from this point. 
                    endOfData = true; 
                    break; 
                }
                // the next character SHOULD be a ".".  We swallow the first 
                // dot and just write the next character to the buffer 
                atLineBreak = false; 
            }
            else if (lastRead == '\n') {
                // hit an end-of-line marker?
                // remember we just had a line break 
                atLineBreak = true; 
            }
            else 
            {
                // something other than a line break character 
                atLineBreak = false; 
            }
            // add the character to the buffer 
            buffer[off++] = (char)lastRead; 
            bytesRead++; 
        }
        
        // we must have had an EOF condition of some sort 
        if (bytesRead == 0) {
            return -1; 
        }
        // return the actual length read in 
        return bytesRead; 
    }
    
     /**
      * Close the stream.  This is a NOP for this stream.  
      * 
      * @exception IOException
      */
     public void close() throws IOException {
         // does nothing 
     }
}

