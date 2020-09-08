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

import java.security.Provider;
import java.security.Security;
import java.util.Map;
import java.util.Properties;

import javax.mail.MessagingException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;

public class XOAUTH2Authenticator implements ClientAuthenticator, CallbackHandler {
    // The realm we're authenticating within
    protected String realm;
    // the user we're authenticating
    protected String username;
    // the user's password (the "shared secret")
    protected String password;
    // the authenticator we're proxying
    protected SaslClient authenticator;

    protected boolean complete = false;


    private static final String SECURITY_PROVIDER = "JavaMail-OAuth2";
    private static final String CLIENT_FACTORY_NAME = "SaslClientFactory.XOAUTH2";

    /**
     * XOAUTH2 SASL Mechanism
     */
    static class Oauth2Provider extends Provider {
        private static final long serialVersionUID = 1L;
        public Oauth2Provider() {
            super(SECURITY_PROVIDER, 1.0, "XOAUTH2 SASL Mechanism");
            put(CLIENT_FACTORY_NAME, XOAUTH2Authenticator.class.getName());
        }
    }

    /**
     * Initialize OAUTH2 provider if there isn't one already.
     * If the operation is not allowed we don't enforce it.
     */
    public static void init() {
        try {
            if (Security.getProvider(SECURITY_PROVIDER) == null) {
                Security.addProvider(new Oauth2Provider());
            }
        } catch (SecurityException ex) {
            //We don't enforce it.
        }
    }

    /**
     * Main constructor.
     *
     * @param username The login user name.
     * @param password The Oauth2 token.
     */
    public XOAUTH2Authenticator(String[] mechanisms, Properties properties, String protocol, String host, String realm,
                                String authorizationID, String username, String password) throws MessagingException {
        this.realm = realm;
        this.username = username;
        this.password = password;
        authenticator = new Oauth2SaslClient( this);
    }

    /**
     * Respond to the hasInitialResponse query. We defer this to the Sasl client.
     *
     * @return The SaslClient response to the same query.
     */
    public boolean hasInitialResponse() {
        return authenticator.hasInitialResponse();
    }

    /**
     * Indicate whether the challenge/response process is complete.
     *
     * @return True if the last challenge has been processed, false otherwise.
     */
    public boolean isComplete() {
        return authenticator.hasInitialResponse();
    }

    /**
     * Retrieve the authenticator mechanism name.
     *
     * @return Will returns the string "XOAUTH2"
     */
    public String getMechanismName() {
        return authenticator.getMechanismName();
    }

    /**
     * Evaluate a login challenge, returning the a result string that
     * should satisfy the challenge.  This is forwarded to the
     * SaslClient, which will use the CallBackHandler to retrieve the
     * information it needs for the given protocol.
     *
     * @param challenge The decoded challenge data, as byte array.
     * @return A formatted challenge response, as an array of bytes.
     * @throws MessagingException
     */
    public byte[] evaluateChallenge(byte[] challenge) throws MessagingException {
        // for an initial response challenge, there's no challenge date.  The SASL
        // client still expects a byte array argument.
        if (challenge == null) {
            challenge = new byte[0];
        }

        try {
            return authenticator.evaluateChallenge(challenge);
        } catch (SaslException e) {
            // got an error, fail this
            throw new MessagingException("Error performing XOAUTH2 validation", e);
        }
    }

    public void handle(Callback[] callBacks) {
        for (int i = 0; i < callBacks.length; i++) {
            Callback callBack = callBacks[i];
            // requesting the user name
            if (callBack instanceof NameCallback) {
                ((NameCallback)callBack).setName(username);
            }
            // need the password
            else if (callBack instanceof PasswordCallback) {

                ((PasswordCallback)callBack).setPassword(password.toCharArray());
            }
            // direct request for the realm information
            else if (callBack instanceof RealmCallback) {
                RealmCallback realmCallback = (RealmCallback)callBack;
                // we might not have a realm, so use the default from the
                // callback item
                if (realm == null) {
                    realmCallback.setText(realmCallback.getDefaultText());
                }
                else {
                    realmCallback.setText(realm);
                }
            }
            // asked to select the realm information from a list
            else if (callBack instanceof RealmChoiceCallback) {
                RealmChoiceCallback realmCallback = (RealmChoiceCallback)callBack;
                // if we don't have a realm, just tell it to use the default
                if (realm == null) {
                    realmCallback.setSelectedIndex(realmCallback.getDefaultChoice());
                }
                else {
                    // locate our configured one in the list
                    String[] choices = realmCallback.getChoices();

                    for (int j = 0; j < choices.length; j++) {
                        // set the index to any match and get out of here.
                        if (choices[j].equals(realm)) {
                            realmCallback.setSelectedIndex(j);
                            break;
                        }
                    }
                    // NB:  If there was no match, we don't set anything.
                    // this should cause an authentication failure.
                }
            }
        }
    }
}
