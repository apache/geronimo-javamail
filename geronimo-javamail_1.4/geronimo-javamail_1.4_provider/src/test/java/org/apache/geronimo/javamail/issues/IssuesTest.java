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
package org.apache.geronimo.javamail.issues;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;
import javax.mail.internet.MimeMultipart;

import junit.framework.Assert;

import org.apache.geronimo.javamail.testserver.AbstractProtocolTest;
import org.apache.geronimo.javamail.testserver.MailServer;

public class IssuesTest extends AbstractProtocolTest {

    public void testGERONIMO6519() throws Exception {

        PrintStream original = System.out;

        try {

            start();
            // Setup JavaMail session
            Properties props = new Properties();
            props.setProperty("mail.debug", "true");
            props.setProperty("mail.smtp.port", String.valueOf(smtpConf.getListenerPort()));
            props.setProperty("mail.smtp.localhost", "some.full.qualified.name.com");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos, true));

            Session session = Session.getInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("test@localhost"));
            message.setRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress("test@localhost"));
            message.setText("test");

            Transport.send(message);
            Assert.assertTrue(baos.toString().contains("EHLO some.full.qualified.name.com"));

        } finally {
            System.setOut(original);
        }

    }

    public void testGERONIMO4594() throws Exception {

        System.setProperty("mail.mime.decodefilename", "true");
        System.setProperty("mail.mime.encodefilename", "true");
        try {

            start();

            // Setup JavaMail session
            Properties props = new Properties();
            props.setProperty("mail.transport.protocol", "smtp");
            props.setProperty("mail.smtp.port", String.valueOf(smtpConf.getListenerPort()));
            props.setProperty("mail.store.protocol", "imap");
            props.setProperty("mail.imap.port", String.valueOf(imapConf.getListenerPort()));
            //props.setProperty("mail.debug","true");
            Session session = Session.getInstance(props);

            MimeMessage msg = new MimeMessage(session);
            msg.setSubject("a file for you");
            msg.setRecipient(javax.mail.Message.RecipientType.TO, new InternetAddress("serveruser@localhost"));
            msg.setFrom(new InternetAddress("serveruser@localhost"));

            MimeBodyPart messageBodyPart = new MimeBodyPart();
            Multipart multipart = new MimeMultipart();
            messageBodyPart.setText("This is message body");
            File file = MailServer.getAbsoluteFilePathFromClassPath("encoded_filename_ÄÜÖ(test).pdf");
            Assert.assertTrue(file.exists());
            DataSource source = new FileDataSource(file.getAbsoluteFile());
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName(file.getName());
            multipart.addBodyPart(messageBodyPart);
            msg.setContent(multipart);
            sendMessage(msg);
            server.ensureMsgCount(1);

            Session jmsession = Session.getInstance(props);
            Store store = jmsession.getStore();
            store.connect("127.0.0.1", "serveruser", "serverpass");
            Folder f = store.getFolder("INBOX");
            f.open(Folder.READ_ONLY); //TODO STAT only when folder open???
            Assert.assertEquals(1, f.getMessageCount());
            Message[] messages = new Message[2];
            messages[0] = f.getMessage(1);
            Assert.assertEquals("encoded_filename_ÄÜÖ(test).pdf", ((Multipart) messages[0].getContent()).getBodyPart(0).getFileName());
            f.close(false);
            store.close();

        } finally {
            System.setProperty("mail.mime.decodefilename", "false");
            System.setProperty("mail.mime.encodefilename", "false");
        }

    }

}
