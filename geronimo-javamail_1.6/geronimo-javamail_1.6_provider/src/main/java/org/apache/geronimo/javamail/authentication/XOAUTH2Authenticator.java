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

import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

public class XOAUTH2Authenticator implements ClientAuthenticator {

    //The user we're authenticating
    protected String username;
    //The user's password (the "shared secret")
    protected String password;
    protected boolean complete = false;

    /**
     * Main constructor.
     *
     * @param username The login user name.
     * @param password The Oauth2 token.
     */
    public XOAUTH2Authenticator(String[] mechanisms, Properties properties, String protocol, String host, String realm,
                                String authorizationID, String username, String password) throws MessagingException {
        this.username = username;
        this.password = password;
    }

    /**
     * Respond to the hasInitialResponse query.
     *
     * @return The SaslClient response to the same query.
     */
    public boolean hasInitialResponse() {
        return true;
    }

    /**
     * Indicate whether the challenge/response process is complete.
     *
     * @return True if the last challenge has been processed, false otherwise.
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Retrieve the authenticator mechanism name.
     *
     * @return Will returns the string "XOAUTH2"
     */
    public String getMechanismName() {
        return "XOAUTH2";
    }

    /**
     * Evaluate a login challenge, returning the a result string that
     * should satisfy the challenge.  This use the CallBackHandler to retrieve the
     * information it needs for the given protocol.
     *
     * @param challenge The decoded challenge data, as byte array.
     * @return A formatted challenge response, as an array of bytes.
     * @throws MessagingException
     */
    public byte[] evaluateChallenge(final byte[] challenge) throws MessagingException{
        if (complete) {
            return new byte[0];
        }

        final String response = new StringBuilder()
                .append("user=")
                .append(this.username)
                .append("\001auth=Bearer ")
                .append(this.password)
                .append("\001\001")
                .toString();


        complete = true;

        try {
            return response.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new MessagingException("Invalid encoding");
        }
    }
}
