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

import java.io.File;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import junit.framework.Assert;

import org.apache.geronimo.javamail.testserver.AbstractProtocolTest;

public class GERONIMO6480Test extends AbstractProtocolTest {

    public void testGERONIMO6480_0() throws Exception {
        BodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(new FileDataSource(getAbsoluteFilePathFromClassPath("pdf-test.pdf"))));
        attachmentPart.setFileName("test.pdf");
        String contentType = getSendedAttachmentContentType(attachmentPart);
        Assert.assertEquals("application/octet-stream; name=test.pdf".toLowerCase(), contentType.toLowerCase());
        // "text/plain; name=test.pdf" with Geronimo because setFileName force it to 'text/plain' when adding the 'name=' part instead of keeping it null
    }

    public void testGERONIMO6480_1() throws Exception {
        BodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.addHeader("Content-Type", "aplication/pdf");
        // setDataHandler reset "Content-Type" so equivalent to previous test
        attachmentPart.setDataHandler(new DataHandler(new FileDataSource(getAbsoluteFilePathFromClassPath("pdf-test.pdf"))));
        attachmentPart.setFileName("test.pdf");
        String contentType = getSendedAttachmentContentType(attachmentPart);
        Assert.assertEquals("application/octet-stream; name=test.pdf".toLowerCase(), contentType.toLowerCase());
        // "text/plain; name=test.pdf" with Geronimo because setFileName force it to 'text/plain' when adding the 'name=' part instead of keeping it null
    }

    public void testGERONIMO6480_2() throws Exception {
        BodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(new FileDataSource(getAbsoluteFilePathFromClassPath("pdf-test.pdf"))));
        attachmentPart.addHeader("Content-Type", "aplication/pdf");
        attachmentPart.setFileName("test.pdf");
        String contentType = getSendedAttachmentContentType(attachmentPart);
        Assert.assertEquals("aplication/pdf; name=test.pdf".toLowerCase(), contentType.toLowerCase());
    }

    public void testGERONIMO6480_3() throws Exception {
        System.setProperty("mail.mime.setcontenttypefilename", Boolean.FALSE.toString());
        try {
            BodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setDataHandler(new DataHandler(new FileDataSource(getAbsoluteFilePathFromClassPath("pdf-test.pdf"))));
            attachmentPart.setFileName("test.pdf");
            String contentType = getSendedAttachmentContentType(attachmentPart);
            Assert.assertEquals("application/octet-stream; name=test.pdf".toLowerCase(), contentType.toLowerCase());
        } finally {
            System.setProperty("mail.mime.setcontenttypefilename", Boolean.TRUE.toString());
        }
    }

    public void testGERONIMO6480_4() throws Exception {
        BodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setFileName("test.pdf");
        attachmentPart.setDataHandler(new DataHandler(new FileDataSource(getAbsoluteFilePathFromClassPath("pdf-test.pdf"))));
        String contentType = getSendedAttachmentContentType(attachmentPart);
        Assert.assertEquals("application/octet-stream; name=test.pdf".toLowerCase(), contentType.toLowerCase());
    }

    private File getAbsoluteFilePathFromClassPath(String filename) throws Exception {
        return new File(GERONIMO6480Test.class.getClassLoader().getResource(filename).toURI());
    }

    private String getSendedAttachmentContentType(BodyPart attachmentPart) throws Exception {

        start();
        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.store.protocol", "imap");
        props.setProperty("mail.imap.port", String.valueOf(imapConf.getListenerPort()));
        props.setProperty("mail.smtp.port", String.valueOf(smtpConf.getListenerPort()));
        //props.setProperty("mail.debug", "true");
        Session session = Session.getInstance(props);

        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText("See attachment.");

        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);
        multipart.addBodyPart(attachmentPart);

        Message message = new MimeMessage(session);
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse("test@mockserver.com"));
        message.setSubject("Test attachment content-type");
        message.setContent(multipart);

        Transport.send(message);

        return getAttachmentContentType(session);
    }

    private String getAttachmentContentType(Session session) throws Exception {
        Store store = session.getStore();
        store.connect("127.0.0.1", "serveruser", "serverpass");

        Folder folder = store.getDefaultFolder();
        folder = folder.getFolder("inbox");
        folder.open(Folder.READ_ONLY);

        server.ensureMsgCount(1);

        Message message = folder.getMessage(1);
        MimeMultipart multipart = (MimeMultipart) message.getContent();
        BodyPart attachmentPart = multipart.getBodyPart(1);
        return attachmentPart.getContentType();
    }

}
