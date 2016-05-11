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

import java.util.List;

import javax.mail.MessagingException;

/**
 * Util class to represent a list response from a IMAP server
 *
 * @version $Rev$ $Date$
 */

public class IMAPQuotaRootResponse extends IMAPUntaggedResponse {
    // the mailbox this applies to 
    public String mailbox; 
    // The list of quota roots 
    public List roots; 
    

    /**
     * Construct a LIST response item.  This can be either 
     * a response from a LIST command or an LSUB command, 
     * and will be tagged accordingly.
     * 
     * @param type   The type of resonse (LIST or LSUB).
     * @param data   The raw response data.
     * @param source The tokenizer source.
     * 
     * @exception MessagingException
     */
    public IMAPQuotaRootResponse(byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super("QUOTAROOT", data); 

        // first token is the mailbox 
        mailbox = source.readEncodedString(); 
        // get the root name list as the remainder of the command. 
        roots = source.readStrings(); 
    }
}

