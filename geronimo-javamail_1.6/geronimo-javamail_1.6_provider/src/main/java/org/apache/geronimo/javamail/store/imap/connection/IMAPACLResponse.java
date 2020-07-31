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

import java.util.ArrayList;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.geronimo.javamail.store.imap.ACL;
import org.apache.geronimo.javamail.store.imap.Rights;

import org.apache.geronimo.javamail.store.imap.connection.IMAPResponseTokenizer.Token; 

/**
 * Utility class to aggregate status responses for a mailbox.
 */
public class IMAPACLResponse extends IMAPUntaggedResponse {
    public String mailbox; 
    public ACL[] acls; 
    
    public IMAPACLResponse(byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super("ACL",  data); 
        
        mailbox = source.readEncodedString();
        List temp = new ArrayList(); 
        
        while (source.hasMore()) {
            String name = source.readString(); 
            String rights = source.readString(); 
            temp.add(new ACL(name, new Rights(rights))); 
        }
        
        acls = new ACL[temp.size()]; 
        acls = (ACL[])temp.toArray(acls); 
    }
}
