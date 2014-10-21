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

import junit.framework.TestCase;
import org.apache.geronimo.mail.util.Base64;

import javax.mail.Folder;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class AuthenticationTest extends TestCase {

    public void testAuthenticatePlain() throws Exception {

        //greenmail does not have AUTHENTICATE "PLAIN" support
        FakeImapAuthPlainServer fs = new FakeImapAuthPlainServer(null, "user", "pass");
        fs.startServer();
        // Setup JavaMail session
        Properties props = new Properties();
        props.setProperty("mail.imap.port", "5111");
        props.setProperty("mail.debug", String.valueOf(true));
        props.setProperty("mail.debug.auth", String.valueOf(true));

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect("localhost", "user", "pass");
        assertTrue(store.isConnected());
        fs.join();
        assertNull(fs.exception);
    }

    public void testAuthenticatePlainFail() throws Exception {

        //greenmail does not have AUTHENTICATE "PLAIN" support
        FakeImapAuthPlainServer fs = new FakeImapAuthPlainServer(null, "user", "pass");
        fs.startServer();
        // Setup JavaMail session
        Properties props = new Properties();
        props.setProperty("mail.imap.port", "5111");
        props.setProperty("mail.debug", String.valueOf(true));
        props.setProperty("mail.debug.auth", String.valueOf(true));
        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");

        try {

            store.connect("localhost", "userXXX", "passXXX");
            fail();
        } catch (MessagingException e) {
            //expected
        }
    }

    public void testAuthenticatePlainAuthzid() throws Exception {

        //greenmail does not have AUTHENTICATE "PLAIN" support
        FakeImapAuthPlainServer fs = new FakeImapAuthPlainServer("authzid", "user", "pass");
        fs.startServer();
        // Setup JavaMail session
        Properties props = new Properties();
        props.setProperty("mail.imap.port", "5111");
        props.setProperty("mail.debug", String.valueOf(true));
        props.setProperty("mail.debug.auth", String.valueOf(true));
        props.setProperty("mail.imap.sasl.authorizationid", "authzid");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect("localhost", "user", "pass");
        assertTrue(store.isConnected());
        fs.join();
        assertNull(fs.exception);
    }


    private class FakeImapAuthPlainServer extends Thread{

        private ServerSocket serverSocket;
        private Socket socket;
        private String authzid;
        private String username;
        private String password;
        Exception exception;

        private FakeImapAuthPlainServer(String authzid, String username, String password) {
            this.password = password;
            this.username = username;
            this.authzid = authzid==null?"":authzid;
        }

        void startServer() throws IOException {

            serverSocket = new ServerSocket(5111);
            this.setDaemon(false);
            this.start();

        }


        public void run() {
            try {
                socket = serverSocket.accept();
                BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                pw.write("* OK ready\r\n");
                pw.flush();
                String tag = br.readLine().split(" ")[0];
                pw.write("* OK IMAP4rev1 Server ready\r\n");
                pw.write("* CAPABILITY IMAP4rev1 AUTH=PLAIN\r\n");
                pw.write(tag+" OK CAPABILITY completed.\r\n");
                pw.flush();
                tag = br.readLine().split(" ")[0];
                pw.write("+ \r\n");
                pw.flush();
                String authline = new String(Base64.decode(br.readLine()));
                System.out.println("authline : "+authline );

                if(!"".equals(authzid) && !(authzid+"\0"+username+"\0"+password).equals(authline)) {
                    pw.write(tag+" BAD username password invalid.\r\n");
                    pw.flush();
                    return;
                }

                if("".equals(authzid) && !(username+"\0"+username+"\0"+password).equals(authline) && !("\0"+username+"\0"+password).equals(authline)) {
                    pw.write(tag+" BAD username password invalid.\r\n");
                    pw.flush();
                    return;
                }

                pw.write(tag + " OK Authenticated.\r\n");
                pw.flush();

                String fin = br.readLine();
                tag = fin.split(" ")[0];

                if(fin.contains("CAPA")) {
                    pw.write("* CAPABILITY IMAP4rev1 AUTH=PLAIN\r\n");
                    pw.write(tag+" OK CAPABILITY completed.\r\n");
                    pw.flush();
                    tag = br.readLine().split(" ")[0];
                    pw.write(tag+" OK NOOP.\r\n");
                }
                else {
                    pw.write(tag+" OK NOOP.\r\n");
                }

                pw.flush();

            } catch (Exception e) {
                exception = e;
            }finally {

                try {
                    socket.close();
                } catch (Exception e) {
                    //ignore
                }

                try {
                    serverSocket.close();
                } catch (Exception e) {
                    //ignore
                }

            }
        }
    }

}
