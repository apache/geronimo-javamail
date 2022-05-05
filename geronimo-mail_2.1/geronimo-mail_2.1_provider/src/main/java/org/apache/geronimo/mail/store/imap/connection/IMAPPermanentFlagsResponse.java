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

import jakarta.mail.Flags;
import jakarta.mail.MessagingException;

/**
 * Util class to represent an untagged response from a IMAP server
 *
 * @version $Rev$ $Date$
 */
public class IMAPPermanentFlagsResponse extends IMAPUntaggedResponse {
    // the response flags value  
    public Flags flags;     
    /**
     * Create a reply object from a server response line (normally, untagged).  This includes
     * doing the parsing of the response line.
     *
     * @param response The response line used to create the reply object.
     */
    public IMAPPermanentFlagsResponse(byte [] response, IMAPResponseTokenizer source) throws MessagingException {
        super("PERMANENTFLAGS", response); 
        flags = source.readFlagList(); 
    }
}

