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

import jakarta.mail.MessagingException;

/**
 * Util class to represent a NAMESPACE response from a IMAP server
 *
 * @version $Rev$ $Date$
 */

public class IMAPNamespace {
    // the namespace prefix 
    public String prefix; 
    // the namespace hierarchy delimiter
    public char separator = '\0'; 
    
    public IMAPNamespace(IMAPResponseTokenizer source) throws MessagingException {
        source.checkLeftParen(); 
        // read the two that make up the response and ...
        prefix = source.readString(); 
        String delim = source.readString(); 
        // if the delimiter is not a null string, grab the first character. 
        if (delim.length() != 0) {
            separator = delim.charAt(0); 
        }
        source.checkRightParen(); 
    }
}

