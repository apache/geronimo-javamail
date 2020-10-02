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
package org.apache.geronimo.javamail.transport.smtp;

import java.util.Properties;

import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.geronimo.javamail.testserver.AbstractProtocolTest;

public class SMTPTransportTest extends AbstractProtocolTest {

    public void testSSLEnable() throws Exception {

        
        smtpConf.enableSSL(false, false);

        start();

        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.smtp.port", String.valueOf(smtpConf.getListenerPort()));
        props.setProperty("mail.debug", "true");

        Session jmsession = Session.getInstance(props);
        Transport t = jmsession.getTransport();
        t.connect();
        
        MimeMessage msg = new MimeMessage(jmsession);
        msg.setFrom(new InternetAddress("test@apache.org"));
        msg.setSubject("Hi!");
        msg.setText("All your base are belong to us");
        
        
        t.sendMessage(msg, new InternetAddress[]{new InternetAddress("testto@apache.org")});

    }


}
