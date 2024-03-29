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
package org.apache.geronimo.mail.store.imap.connection;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;

import jakarta.mail.internet.MimeMessage;

import junit.framework.TestCase;
import org.apache.geronimo.mail.store.imap.IMAPStoreTest;

public class IMAPBodyStructureTest extends TestCase {

    public void testMultipart() throws Exception {
        InputStream in = IMAPStoreTest.class.getResourceAsStream("/imap/multipart.bodystructure");
        BufferedReader r = new BufferedReader(new InputStreamReader(in));
        try {
            IMAPResponseTokenizer tokenizer = new IMAPResponseTokenizer(r.readLine().getBytes("ISO8859-1"));
            IMAPBodyStructure s = new IMAPBodyStructure(tokenizer);
            assertNull(s.disposition.getDisposition());
            assertNull(s.md5Hash);
        } finally {
            in.close();
        }
    }
}
