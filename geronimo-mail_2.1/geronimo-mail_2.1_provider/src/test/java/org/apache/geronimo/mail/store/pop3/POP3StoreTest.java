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
package org.apache.geronimo.mail.store.pop3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import junit.framework.Assert;

import org.apache.geronimo.mail.testserver.AbstractProtocolTest;
import org.apache.geronimo.mail.testserver.MailServer.DummySocketFactory;

public class POP3StoreTest extends AbstractProtocolTest {

    
    
    
    public void testSendRetrieve() throws Exception {
        
        start();
        
        // Setup mail session
        Properties props = new Properties();
        props.setProperty("mail.smtp.port", String.valueOf(smtpConf.getListenerPort()));
        props.setProperty("mail.debug","true");
        Session session = Session.getInstance(props);
        // Send messages for the current test to James
        sendMessage(session, "/messages/multipart.msg");
        sendMessage(session, "/messages/simple.msg");
        server.ensureMsgCount(2);
        
        props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        Assert.assertEquals(2, f.getMessageCount());
        Message[] messages = new Message[2];
        messages[0] = f.getMessage(1);
        messages[1] = f.getMessage(2);
        checkMessages(messages);
        f.close(false);
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
    

    public void testStartTLS() throws Exception {

        pop3Conf.enableSSL(true, false);

        start();

        sendTestMsgs();
        
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3.starttls.required", "true");
        props.setProperty("mail.pop3.ssl.trust", "*");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        Assert.assertEquals(2, f.getMessageCount());
        f.close(false);
        store.close();

    }

    public void testAPOP() throws Exception {

        pop3Conf.enableSSL(true, false);

        start();
        sendTestMsgs();
        
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3.apop.enable", "true");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        Assert.assertEquals(2, f.getMessageCount());
        f.close(false);
        store.close();

    }

    public void testFetch() throws Exception {

        
        pop3Conf.enableSSL(true, false);

        start();
        sendTestMsgs();
        
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        FetchProfile fp = new FetchProfile();
        fp.add(UIDFolder.FetchProfileItem.UID);
        fp.add(FetchProfile.Item.CONTENT_INFO);
        
        Message[] msgs = f.getMessages();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Assert.assertEquals(2, msgs.length);
        
        f.fetch(msgs, fp);
        Assert.assertEquals(2, f.getMessageCount());
        
        for (int i = 0; i < msgs.length; i++) {
            Message message = msgs[i];
            message.writeTo(bout);
            String msg = bout.toString();
            Assert.assertNotNull(msg);
            int num = message.getMessageNumber();
            Assert.assertTrue(num > 0);
            String uid = ((POP3Folder) f).getUID(message);
            Assert.assertNotNull(uid);
            Assert.assertTrue(!uid.isEmpty());
        }
        
        f.close(false);
        store.close();

    }
    
    
    
    public void testDelete() throws Exception {

        
        pop3Conf.enableSSL(true, false);

        start();
        sendTestMsgs();
        
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_WRITE); //TODO STAT only when folder open???
        Assert.assertEquals(2, f.getMessageCount());
        Message[] msgs =  f.getMessages();
        f.setFlags(msgs, new Flags(Flag.DELETED), true);
        Assert.assertEquals(2, f.getMessageCount());
        f.getMessage(1).getSubject(); //should fail
        //Assert.assertEquals(2, f.expunge());
        f.close(false);
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        Assert.assertEquals(0, f.getMessageCount());
        store.close();

    }
    
    
    
    public void testStartTLSFail() throws Exception {

        
        pop3Conf.enableSSL(false, false);

        start();
        sendTestMsgs();
        
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3.starttls.required", "true");
        props.setProperty("mail.pop3.ssl.trust", "*");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        try {
            store.connect("127.0.0.1", "serveruser", "serverpass");
            fail();
        } catch (MessagingException e) {
            //Expected
        }
    }

    public void testSSLEnable() throws Exception {

        
        pop3Conf.enableSSL(false, true);

        start();
        sendTestMsgs();

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3.ssl.enable", "true");
        props.setProperty("mail.pop3.ssl.trust", "*");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        Assert.assertEquals(2, f.getMessageCount());
        f.close(false);
        store.close();

    }

    public void testSSLPop3s() throws Exception {

        
        pop3Conf.enableSSL(false, true);

        start();
        sendTestMsgs();

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3s");
        props.setProperty("mail.pop3s.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3s.ssl.trust", "*");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        Assert.assertEquals(2, f.getMessageCount());
        f.close(false);
        store.close();

    }
    
    public void testSSLPop3sFactoryClass() throws Exception {

        
        pop3Conf.enableSSL(false, true);

        start();
        sendTestMsgs();

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3s");
        props.setProperty("mail.pop3s.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3s.ssl.trust", "*");
        props.setProperty("mail.pop3s.ssl.socketFactory.class", DummySocketFactory.class.getName());
       

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        try {
            store.connect("127.0.0.1", "serveruser", "serverpass");
            fail();
        } catch (MessagingException e) {
            Assert.assertEquals("dummy socket factory", e.getCause().getCause().getMessage());
            
            //Expected
        }

        
        
    }

    public void testSSLPop3sFactoryInstance() throws Exception {

        
        pop3Conf.enableSSL(false, true);

        start();
        sendTestMsgs();

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3s");
        props.setProperty("mail.pop3s.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3s.ssl.trust", "*");
        props.put("mail.pop3s.ssl.socketFactory", new DummySocketFactory());
       

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        try {
            store.connect("127.0.0.1", "serveruser", "serverpass");
            fail();
        } catch (MessagingException e) {
            Assert.assertEquals("dummy socket factory", e.getCause().getMessage());
            
            //Expected
        }

    }
    
    public void testSSLPop3sNotEnabled() throws Exception {

        
        pop3Conf.enableSSL(false, false);

        start();
        sendTestMsgs();

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3s");
        props.setProperty("mail.pop3s.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.pop3s.ssl.trust", "*");
        props.setProperty("mail.pop3s.ssl.enable", "false");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        Assert.assertEquals(2, f.getMessageCount());
        f.close(false);
        store.close();

    }
    
    public void testPop3GetMsgs() throws Exception {

        
        pop3Conf.enableSSL(false, false);

        start();
        sendTestMsgs();

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        props.setProperty("mail.pop3.port", String.valueOf(pop3Conf.getListenerPort()));
        props.setProperty("mail.debug", "true");

        Session jmsession = Session.getInstance(props);
        Store store = jmsession.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");
        Folder f = store.getFolder("INBOX");
        f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
        
        
        Message[] msgs =  f.getMessages();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Assert.assertEquals(2, msgs.length);
        
        for (int i = 0; i < msgs.length; i++) {
            Message message = msgs[i];
            message.writeTo(bout);
            String msg = bout.toString();
            Assert.assertNotNull(msg);
            int num = message.getMessageNumber();
            Assert.assertTrue(num > 0);
            String uid = ((POP3Folder) f).getUID(message);
            Assert.assertNotNull(uid);
            Assert.assertTrue(!uid.isEmpty());
        }
        
        f.close(false);
        store.close();

    }

}
