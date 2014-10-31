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
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Properties;

import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMessage.RecipientType;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

public class IssuesTest extends TestCase {

    public void testGERONIMO6519() throws Exception {
        try {
            GreenMail greenMail = new GreenMail(ServerSetupTest.SMTP);
            greenMail.start();
            greenMail.setUser("test@localhost", "test", "test");
            // Setup JavaMail session
            Properties props = new Properties();
            props.setProperty("mail.debug", "true");
            props.setProperty("mail.smtp.port", String.valueOf(greenMail.getSmtp().getPort()));
            props.setProperty("mail.smtp.localhost", "some.full.qualified.name.com");
            
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos, true));
            
            Session session = Session.getInstance(props);
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("test@localhost"));
            message.setRecipient(RecipientType.TO, new InternetAddress("test@localhost"));
            message.setText("test");
            
            Transport.send(message);
            Assert.assertTrue(baos.toString().contains("EHLO some.full.qualified.name.com"));
            
        } finally {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
        }
        
        
    }

}
