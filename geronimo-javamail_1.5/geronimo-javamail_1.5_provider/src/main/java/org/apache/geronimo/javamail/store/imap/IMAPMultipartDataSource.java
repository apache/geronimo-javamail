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

package org.apache.geronimo.javamail.store.imap;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.MultipartDataSource;

import javax.mail.internet.MimePart;
import javax.mail.internet.MimePartDataSource;

import org.apache.geronimo.javamail.store.imap.connection.IMAPBodyStructure;

public class IMAPMultipartDataSource extends MimePartDataSource implements MultipartDataSource {
    // the list of parts
    protected BodyPart[] parts;

    IMAPMultipartDataSource(IMAPMessage message, MimePart parent, String section, IMAPBodyStructure bodyStructure) {
        super(parent);

        parts = new BodyPart[bodyStructure.parts.length];
        
        // We're either created from the parent message, in which case we're the top level 
        // of the hierarchy, or we're created from a nested message, so we need to apply the 
        // parent numbering prefix. 
        String sectionBase = section == null ? "" : section + "."; 

        for (int i = 0; i < parts.length; i++) {
            // create a section id.  This is either the count (origin zero) or a subpart of the previous section.
            parts[i] = new IMAPMimeBodyPart(message, (IMAPBodyStructure)bodyStructure.parts[i], sectionBase + (i + 1));
        }
    }

    public int getCount() {
        return parts.length;
    }

    public BodyPart getBodyPart(int index) throws MessagingException {
        return parts[index];
    }
}
