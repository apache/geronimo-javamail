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

package org.apache.geronimo.javamail.authentication;

import java.io.UnsupportedEncodingException;

import javax.mail.MessagingException;

//Implements RFC 4616 PLAIN SASL mechanism
//See also RFC 3501, section 6.2.2"
//an RFC 2595, section 6"
public class PlainAuthenticator implements ClientAuthenticator {

    // the sasl authzid we're authenticating
    protected String authzid;

    // the user we're authenticating
    protected String username;

    // the user's password (the "shared secret")
    protected String password;

    // indicates whether we've gone through the entire challenge process.
    protected boolean complete = false;

    /**
     * Main constructor.
     *
     * @param authzid
     *            SASL authenticationid (optional)
     * @param username
     *            The login user name.
     * @param password
     *            The login password.
     */
    public PlainAuthenticator(String authzid, String username, String password) {
        this.authzid = authzid;
        this.username = username;
        this.password = password;
    }

     /**
      * Constructor without authzid
      *
      * @param username
      *            The login user name.
      * @param password
      *            The login password.
      */
     public PlainAuthenticator(String username, String password) {
         this(null, username, password);
     }

    /**
     * Respond to the hasInitialResponse query. This mechanism does have an
     * initial response, which is the entire challenge sequence.
     * 
     * @return Always returns true.
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
     * @return Always returns the string "PLAIN"
     */
    public String getMechanismName() {
        return "PLAIN";
    }

    /**
     * Evaluate a PLAIN login challenge, returning the a result string that
     * should satisfy the challenge.
     * 
     * @param challenge
     *            For PLAIN Authentication there is no challenge (so this is unused)
     * 
     * @return A formatted challenge response, as an array of bytes.
     * @exception MessagingException
     */
    public byte[] evaluateChallenge(byte[] challenge) throws MessagingException {
        try {

            String result = "\0"+username+"\0"+password;

            if(authzid != null && authzid.length() > 0) {
                result = authzid+result;
            }

            complete = true;
            return result.getBytes("UTF-8");

        } catch (UnsupportedEncodingException e) {
            // got an error, fail this
            throw new MessagingException("Invalid encoding");
        }
    }
}
