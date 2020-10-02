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

public class IMAPListResponse extends IMAPUntaggedResponse {
    // parsed flag responses
    public boolean noinferiors = false;
    public boolean noselect = false;
    public boolean marked = false;
    public boolean unmarked = false;

    // the name separator character
    public char separator;
    // the mail box name
    public String mailboxName;
    // this is for support of the get attributes command
    public String[] attributes;

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
    public IMAPListResponse(String type, byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super(type, data); 

        // parse the list of flag values
        List flags = source.readSystemNameList(); 
        
        // copy this into the attributes array. 
        attributes = new String[flags.size()]; 
        attributes = (String[])flags.toArray(attributes); 

        for (int i = 0; i < flags.size(); i++) {
            String flag = ((String)flags.get(i));

            if (flag.equalsIgnoreCase("\\Marked")) {
                marked = true;
            }
            else if (flag.equalsIgnoreCase("\\Unmarked")) {
                unmarked = true;
            }
            else if (flag.equalsIgnoreCase("\\Noselect")) {
                noselect = true;
            }
            else if (flag.equalsIgnoreCase("\\Noinferiors")) {
                noinferiors = true;
            }
        }

        // set a default sep value 
        separator = '\0';    
        // get the separator and name tokens
        String separatorString = source.readQuotedStringOrNil();
        if (separatorString != null && separatorString.length() == 1) {
            separator = separatorString.charAt(0); 
        }
        mailboxName = source.readEncodedString();
    }
}

