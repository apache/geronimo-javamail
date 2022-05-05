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

import java.util.Date;

import jakarta.mail.MessagingException;

import jakarta.mail.internet.InternetAddress;


public class IMAPEnvelope extends IMAPFetchDataItem {
    // the following are various fields from the FETCH ENVELOPE structure.  These
    // should be self-explanitory.
    public Date date;
    public String subject;
    public InternetAddress[] from;
    public InternetAddress[] sender;
    public InternetAddress[] replyTo;
    public InternetAddress[] to;
    public InternetAddress[] cc;
    public InternetAddress[] bcc;

    public String inReplyTo;
    public String messageID;


    /**
     * Parse an IMAP FETCH ENVELOPE response into the component pieces.
     * 
     * @param source The tokenizer for the response we're processing.
     */
    public IMAPEnvelope(IMAPResponseTokenizer source) throws MessagingException {
        super(ENVELOPE);

        // these should all be a parenthetical list 
        source.checkLeftParen(); 
        // the following fields are all positional
        // The envelope date is defined in the spec as being an "nstring" value, which 
        // means it is either a string value or NIL.  
        date = source.readDateOrNil(); 
        subject = source.readStringOrNil();
        from = source.readAddressList();
        sender = source.readAddressList();
        replyTo = source.readAddressList();
        to = source.readAddressList();
        cc = source.readAddressList();
        bcc = source.readAddressList();
        inReplyTo = source.readStringOrNil();
        messageID = source.readStringOrNil();

        // make sure we have a correct close on the field.
        source.checkRightParen();
    }
}
