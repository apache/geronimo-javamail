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
import java.util.List;

import jakarta.mail.MessagingException;

/**
 * Util class to represent an untagged response from a IMAP server
 *
 * @version $Rev$ $Date$
 */
public class IMAPOkResponse extends IMAPUntaggedResponse {
    // the response status value 
    protected List status; 
    // any message following the response 
    protected String message; 

    /**
     * Create a reply object from a server response line (normally, untagged).  This includes
     * doing the parsing of the response line.
     *
     * @param response The response line used to create the reply object.
     */
    public IMAPOkResponse(String keyword, List status, String message, byte [] response) {
        super(keyword, response); 
        this.status = status; 
        this.message = message; 
    }
    
    /**
     * Get the response code included with the OK 
     * response. 
     * 
     * @return The string name of the response code.
     */
    public String getResponseCode() {
        return getKeyword(); 
    }

    /**
     * Return the status argument values associated with
     * this status response.
     * 
     * @return The status value information, as a list of tokens.
     */
    public List getStatus() {
        return status; 
    }
    
    /**
     * Get any trailing message associated with this 
     * status response. 
     * 
     * @return 
     */
    public String getMessage() {
        return message; 
    }
}


