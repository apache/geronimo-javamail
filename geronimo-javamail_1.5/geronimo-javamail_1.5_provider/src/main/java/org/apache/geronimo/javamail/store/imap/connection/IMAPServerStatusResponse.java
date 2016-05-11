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

/**
 * Util class to represent an untagged response from a IMAP server
 *
 * @version $Rev: 594520 $ $Date: 2007-11-13 07:57:39 -0500 (Tue, 13 Nov 2007) $
 */
public class IMAPServerStatusResponse extends IMAPUntaggedResponse {
    // any message following the response 
    protected String message; 

    /**
     * Create a reply object from a server response line (normally, untagged).  This includes
     * doing the parsing of the response line.
     *
     * @param response The response line used to create the reply object.
     */
    public IMAPServerStatusResponse(String keyword, String message, byte [] response) {
        super(keyword, response); 
        this.message = message; 
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

