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

public class IMAPMessageText extends IMAPFetchBodyPart {
    // the header data
    protected byte[] data;

    /**
     * Construct a top-level TEXT data item. 
     * 
     * @param data   The data for the message text.
     * 
     * @exception MessagingException
     */
    public IMAPMessageText(byte[] data) throws MessagingException {
        this(new IMAPBodySection(IMAPBodySection.TEXT), data);
    }
    
    
    public IMAPMessageText(IMAPBodySection section, byte[] data) throws MessagingException {
        super(TEXT, section);
        this.data = data; 
    }
    
    /**
     * Retrieved the header data.
     *
     * @return The header data.
     */
    public byte[] getContent() {
        return data;
    }
}

