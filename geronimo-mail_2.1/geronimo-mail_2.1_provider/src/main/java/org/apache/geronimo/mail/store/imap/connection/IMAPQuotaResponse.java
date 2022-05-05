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

import java.util.ArrayList; 
import java.util.List;

import jakarta.mail.MessagingException;
import jakarta.mail.Quota;

/**
 * Util class to represent a list response from a IMAP server
 *
 * @version $Rev$ $Date$
 */

public class IMAPQuotaResponse extends IMAPUntaggedResponse {
    // the returned quota item 
    public Quota quota; 

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
    public IMAPQuotaResponse(byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super("QUOTA", data); 

        // first token is the root name, which can be either an atom or a string. 
        String tokenName = source.readString(); 
        
        // create a quota item for this 
        quota = new Quota(tokenName); 
        
        source.checkLeftParen(); 
        
        List resources = new ArrayList(); 
        
        while (source.notListEnd()) {
            // quotas are returned as a set of triplets.  The first element is the 
            // resource name, followed by the current usage and the limit value. 
            Quota.Resource resource = new Quota.Resource(source.readAtom(), source.readLong(), source.readLong()); 
            resources.add(resource); 
        }
        
        quota.resources = (Quota.Resource[])resources.toArray(new Quota.Resource[resources.size()]); 
    }
}

