/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.geronimo.javamail.store.imap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.geronimo.javamail.testserver.AbstractProtocolTest;


public class IMAPStoreTest extends AbstractProtocolTest {
    
    public void testSimple() throws Exception {
       
        start();
        sendTestMsgs();
        
        Properties props = new Properties();
        props.setProperty("mail.imap.port", String.valueOf(imapConf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        Session session = Session.getInstance(props);
        
        // Load the message from IMAP
        Store store = session.getStore("imap");
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_ONLY);
        Message[] messages = new Message[2];
        messages[0] = folder.getMessage(1);
        messages[1] = folder.getMessage(2);
        checkMessages(messages);
        folder.close(false);
        store.close();
    }
    
    
    private void checkMessages(Message[] messages) throws Exception {
        MimeMessage msg1 = (MimeMessage)messages[0];
        Object content = msg1.getContent();
        assertTrue(content instanceof MimeMultipart);
        MimeMultipart multipart = (MimeMultipart)content;
        assertEquals("First part", multipart.getBodyPart(0).getContent());
        assertEquals("Second part", multipart.getBodyPart(1).getContent());        
        checkMessage(msg1);
        
        MimeMessage msg2 = (MimeMessage)messages[1];
        assertEquals("Foo Bar", msg2.getContent().toString().trim());
        checkMessage(msg2);
    }
    
    private void checkMessage(MimeMessage input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        input.writeTo(out);
        
        Properties props = new Properties();
        Session s = Session.getInstance(props);
        
        byte [] inputData = out.toByteArray();
        System.out.println(new String(inputData, 0, inputData.length));
        
        MimeMessage output = new MimeMessage(s, new ByteArrayInputStream(inputData));
        
        assertEquals(input.getContentType().toLowerCase(), output.getContentType().toLowerCase());        
    }
    
 
}
