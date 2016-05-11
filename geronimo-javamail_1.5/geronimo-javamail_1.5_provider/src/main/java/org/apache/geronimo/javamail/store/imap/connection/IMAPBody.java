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

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.mail.MessagingException;


/**
 * The full body content of a message.
 */
public class IMAPBody extends IMAPFetchBodyPart {
    // the body content data
    byte[] content = null;

    /**
     * Construct a top-level MessageText data item. 
     * 
     * @param data   The data for the Message Text     
     * 
     * @exception MessagingException
     */
    public IMAPBody(byte[] data) throws MessagingException {
        this(new IMAPBodySection(IMAPBodySection.BODY), data);
    }
    
    /**
     * Create a Message Text instance. 
     * 
     * @param section The section information.  This may include substring information if this
     *                was just a partical fetch.
     * @param data    The message content data.
     * 
     * @exception MessagingException
     */
    public IMAPBody(IMAPBodySection section, byte[] data) throws MessagingException {
        super(BODY, section);
        // save the content 
        content = data; 
    }


    /**
     * Get the part content as a byte array.
     *
     * @return The part content as a byte array.
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * Get an input stream for reading the part content.
     *
     * @return An ByteArrayInputStream sourced to the part content.
     */
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }
}

