/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.geronimo.javamail.store.pop3.connection;

import java.io.ByteArrayInputStream;

import org.apache.geronimo.javamail.store.pop3.POP3Constants;

import org.apache.geronimo.mail.util.Base64;

/**
 * This class provides the basic implementation for the POP3Response.
 * 
 * @see org.apache.geronimo.javamail.store.pop3.POP3Response
 * @version $Rev$ $Date$
 */

public class POP3Response implements POP3Constants {

    private int status = ERR;

    private String firstLine;

    private byte[] data;

    POP3Response(int status, String firstLine, byte []data) {
        this.status = status;
        this.firstLine = firstLine;
        this.data = data;
    }

    public int getStatus() {
        return status;
    }
    
    public byte[] getData() {
        return data; 
    }

    public ByteArrayInputStream getContentStream() {
        return new ByteArrayInputStream(data);
    }

    public String getFirstLine() {
        return firstLine;
    }
    
    public boolean isError() {
        return status == ERR; 
    }
    
    public boolean isChallenge() {
        return status == CHALLENGE; 
    }
    
    /**
     * Decode the message portion of a continuation challenge response.
     * 
     * @return The byte array containing the decoded data. 
     */
    public byte[] decodeChallengeResponse() 
    {
        // the challenge response is a base64 encoded string...
        return Base64.decode(firstLine.trim()); 
    }

}

