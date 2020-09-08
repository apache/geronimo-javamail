/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.geronimo.javamail.authentication;

import javax.security.auth.callback.*;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class Oauth2SaslClient implements SaslClient {

    private CallbackHandler callBackHandler;
    private boolean isComplete = false;

    public Oauth2SaslClient(CallbackHandler callBackHandler) {
        this.callBackHandler = callBackHandler;
    }

    @Override
    public String getMechanismName() {
        return "XOAUTH2";
    }

    @Override
    public boolean hasInitialResponse() {
        return true;
    }

    @Override
    public byte[] evaluateChallenge(byte[] challenge) throws SaslException {

        if (isComplete) {
            return new byte[0];
        }

        NameCallback ncb = new NameCallback("User name:");
        PasswordCallback pcb = new PasswordCallback("OAuth token:", false);

        try {
            callBackHandler.handle(new Callback[] { ncb, pcb });
        } catch (UnsupportedCallbackException ex) {
            throw new SaslException("Unsupported callback", ex);
        } catch (IOException ex) {
            throw new SaslException("Callback handler failed", ex);
        }

        String userName = ncb.getName();
        String oauthToken = new String(pcb.getPassword());
        pcb.clearPassword();
        String resp = "user=" + userName + "\001auth=Bearer " + oauthToken + "\001\001";
        byte[] response;

        try {
            response = resp.getBytes("utf-8");
        } catch (UnsupportedEncodingException ex) {
            response = getBytes(resp);
        }

        isComplete = true;
        return response;
    }

    private static byte[] getBytes(String s) {
        char[] chars = s.toCharArray();
        int size = chars.length;
        byte[] bytes = new byte[size];

        for (int i = 0; i < size; ) {
            bytes[i] = (byte) chars[i++];
        }
        return bytes;
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
        throw new SaslException("OAUTH2 unwrap is not supported");
    }

    @Override
    public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
        throw new SaslException("OAUTH2 wrap is not supported");
    }

    @Override
    public Object getNegotiatedProperty(String propName) {
        if (!isComplete){
            throw new IllegalStateException("OAUTH2 getNegotiatedProperty");
        }
        return null;
    }

    @Override
    public void dispose() throws SaslException {
    }
}