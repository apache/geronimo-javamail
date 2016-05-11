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

import javax.mail.MessagingException;

import org.apache.geronimo.javamail.store.imap.Rights;

/**
 * Utility class to aggregate status responses for a mailbox.
 */
public class IMAPMyRightsResponse extends IMAPUntaggedResponse {
    public String mailbox; 
    public Rights rights; 
    
    public IMAPMyRightsResponse(byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super("MYRIGHTS", data); 
        
        mailbox = source.readEncodedString();
        rights = new Rights(source.readString());
    }
}

