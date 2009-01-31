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
package org.apache.geronimo.javamail.store.pop3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import junit.framework.TestCase;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

public class POP3StoreTest extends TestCase {
    
    private GreenMail greenMail;
    private Message[] messages;
    
    @Override
    protected void setUp() throws Exception {
        // Setup GreenMail
        greenMail = new GreenMail(ServerSetupTest.SMTP_POP3);       
        greenMail.start();
        greenMail.setUser("test@localhost", "test", "test");
        // Setup JavaMail session
        Properties props = new Properties();
        props.setProperty("mail.smtp.port", String.valueOf(greenMail.getSmtp().getPort()));
        props.setProperty("mail.pop3.port", String.valueOf(greenMail.getPop3().getPort()));
        
        System.out.println("stmp.port: " + greenMail.getSmtp().getPort());
        System.out.println("pop3 port: " + greenMail.getPop3().getPort());
        
        Session session = Session.getInstance(props);
        // Send messages for the current test to GreenMail
        sendMessage(session, "/messages/multipart.msg");
        sendMessage(session, "/messages/simple.msg");
        
        // Load the message from POP3
        Store store = session.getStore("pop3");
        store.connect("localhost", "test", "test");
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_ONLY);
        this.messages = folder.getMessages();
        assertEquals(2, messages.length);
    }
    
    @Override
    protected void tearDown() throws Exception {
        greenMail.stop();
    }

    private void sendMessage(Session session, String msgFile) throws Exception {
        MimeMessage message;
        InputStream in = POP3StoreTest.class.getResourceAsStream(msgFile);
        try {
            message = new MimeMessage(session, in);
        } finally {
            in.close();
        }
        Transport.send(message, new Address[] { new InternetAddress("test@localhost") });
    }
    
    public void testMessages() throws Exception {
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
