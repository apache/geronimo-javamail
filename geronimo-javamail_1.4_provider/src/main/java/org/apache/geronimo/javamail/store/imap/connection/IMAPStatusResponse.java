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
 * Utility class to aggregate status responses for a mailbox.
 */
public class IMAPStatusResponse extends IMAPUntaggedResponse {
    // the mail box name 
    public String mailbox; 
    // number of messages in the box
    public int messages = -1;
    // number of recent messages 
    public int recentMessages = -1; 
    // the number of unseen messages
    public int unseenMessages = -1;
    // the next UID for this mailbox
    public long uidNext = -1L;
    // the UID validity item
    public long uidValidity = -1L;

    public IMAPStatusResponse(byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super("STATUS",  data); 
        
        // the mail box name is supposed to be encoded, so decode it now.
        mailbox = source.readEncodedString();

        // parse the list of flag values
        List flags = source.readStringList();

        for (int i = 0; i < flags.size(); i += 2) {
            String field = ((String)flags.get(i)).toUpperCase();
            String stringValue = ((String)flags.get(i + 1)); 
            long value; 
            try {
                value = Long.parseLong(stringValue); 
            } catch (NumberFormatException e) {
                throw new MessagingException("Invalid IMAP Status response", e); 
            }
                

            if (field.equals("MESSAGES")) {
                messages = (int)value; 
            }
            else if (field.equals("RECENT")) {
                recentMessages = (int)value;
            }
            else if (field.equals("UIDNEXT")) {
                uidNext = value;
            }
            else if (field.equals("UIDVALIDITY")) {
                uidValidity = value; 
            }
            else if (field.equals("UNSEEN")) {
                unseenMessages = (int)value; 
            }
        }
    }
}

