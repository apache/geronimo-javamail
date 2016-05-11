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
import java.io.UnsupportedEncodingException;

import javax.mail.MessagingException;

/**
 * Base class for all response messages.                      
 *
 * @version $Rev: 947075 $ $Date: 2010-05-21 13:12:20 -0400 (Fri, 21 May 2010) $
 */
public class IMAPResponse {
    // The original (raw) response data
    protected byte[] response;

    /**
     * Create a response object from a server response line (normally, untagged).  This includes
     * doing the parsing of the response line.
     *
     * @param response The response line used to create the reply object.
     */
    protected IMAPResponse(byte [] response) {
        // set this as the current message and parse.
        this.response = response;
    }
    
    /**
     * Retrieve the raw response line data for this 
     * response message.  Normally, this will be a complete
     * single line response, unless there are quoted 
     * literals in the response data containing octet
     * data. 
     * 
     * @return The byte array containing the response information.
     */
    public byte[] getResponseData() {
        return response; 
    }

    /**
     * Return the response message as a string value.  
     * This is intended for debugging purposes only.  The 
     * response data might contain octet data that 
     * might not convert to character data appropriately. 
     * 
     * @return The string version of the response. 
     */
    public String toString() {
        try {
            return new String(response, "US-ASCII");
        } catch (UnsupportedEncodingException e) {
        }
        return new String(response);
    }
}

