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

import java.io.ByteArrayInputStream;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders; 

public class IMAPInternetHeader extends IMAPFetchBodyPart {
    // the parsed headers
    public InternetHeaders headers; 
    
    /**
     * Construct a top-level HEADER data item. 
     * 
     * @param data   The data for the InternetHeaders.
     * 
     * @exception MessagingException
     */
    public IMAPInternetHeader(byte[] data) throws MessagingException {
        this(new IMAPBodySection(IMAPBodySection.HEADERS), data);
    }
    

    /**
     * Construct a HEADER request data item.
     * 
     * @param section  The Section identifier information.
     * @param data     The raw data for the internet headers.
     * 
     * @exception MessagingException
     */
    public IMAPInternetHeader(IMAPBodySection section, byte[] data) throws MessagingException {
        super(HEADER, section);
        
        // and convert these into real headers 
        ByteArrayInputStream in = new ByteArrayInputStream(data); 
        headers = new InternetHeaders(in); 
    }
    
    /**
     * Test if this is a complete header fetch, or just a partial list fetch.
     * 
     * @return 
     */
    public boolean isComplete() {
        return section.section == IMAPBodySection.HEADERS;
    }
}

