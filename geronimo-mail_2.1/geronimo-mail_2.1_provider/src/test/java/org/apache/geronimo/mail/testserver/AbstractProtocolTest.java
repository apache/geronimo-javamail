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
package org.apache.geronimo.mail.testserver;

import java.io.InputStream;
import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.geronimo.mail.store.pop3.POP3StoreTest;

public abstract class AbstractProtocolTest extends TestCase {

    protected MailServer server = new MailServer();
    protected MailServer.Pop3TestConfiguration pop3Conf;
    protected MailServer.SmtpTestConfiguration smtpConf;
    protected MailServer.ImapTestConfiguration imapConf;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pop3Conf = new MailServer.Pop3TestConfiguration();
        smtpConf = new MailServer.SmtpTestConfiguration();
        imapConf = new MailServer.ImapTestConfiguration();

    }

    protected void start() throws Exception {

        server.start(smtpConf, pop3Conf, imapConf);

    }

    public void testImplUsageImap() throws Exception {

        //check that we load our mail impl
        final Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imap");
        final Session jmsession = Session.getInstance(props);
        Assert.assertEquals(org.apache.geronimo.mail.store.imap.IMAPStore.class, jmsession.getStore().getClass());

    }

    public void testImplUsagePop3() throws Exception {

        //check that we load our mail impl
        final Properties props = new Properties();
        props.setProperty("mail.store.protocol", "pop3");
        final Session jmsession = Session.getInstance(props);
        Assert.assertEquals(org.apache.geronimo.mail.store.pop3.POP3Store.class, jmsession.getStore().getClass());

    }

    public void testImplUsageSmtp() throws Exception {

        //check that we load our mail impl
        final Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        final Session jmsession = Session.getInstance(props);
        Assert.assertEquals(org.apache.geronimo.mail.transport.smtp.SMTPTransport.class, jmsession.getTransport().getClass());

    }

    protected void sendTestMsgs() throws Exception {
        final Properties props = new Properties();
        props.setProperty("mail.smtp.port", String.valueOf(smtpConf.getListenerPort()));
        props.setProperty("mail.debug", "true");
        final Session session = Session.getInstance(props);
        sendMessage(session, "/messages/multipart.msg");
        sendMessage(session, "/messages/simple.msg");
        server.ensureMsgCount(2);
    }

    protected void sendMessage(final Session session, final String msgFile) throws Exception {
        MimeMessage message;
        final InputStream in = POP3StoreTest.class.getResourceAsStream(msgFile);
        try {
            message = new MimeMessage(session, in);
        } finally {
            in.close();
        }
        Transport.send(message, new Address[] { new InternetAddress("serveruser@localhost") });
    }
    
    protected void sendMessage(final MimeMessage message) throws Exception {
        Transport.send(message, new Address[] { new InternetAddress("serveruser@localhost") });
    }

    @Override
    protected void tearDown() throws Exception {
        server.stop();
    }

}
