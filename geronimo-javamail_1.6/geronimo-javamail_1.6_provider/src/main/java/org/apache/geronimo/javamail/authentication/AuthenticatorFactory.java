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

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Properties;

import org.apache.geronimo.javamail.util.ProtocolProperties;

public class AuthenticatorFactory {
    // the list of authentication mechanisms we have direct support for.  Others come from
    // SASL, if it's available.

    public static final String AUTHENTICATION_PLAIN = "PLAIN";
    public static final String AUTHENTICATION_LOGIN = "LOGIN";
    public static final String AUTHENTICATION_CRAMMD5 = "CRAM-MD5";
    public static final String AUTHENTICATION_DIGESTMD5 = "DIGEST-MD5";
    public static final String AUTHENTICATION_XOAUTH2 = "XOAUTH2";

    static public ClientAuthenticator getAuthenticator(ProtocolProperties props, List mechanisms, String host, String username, String password, String authId, String realm)
    {
        // if the authorization id isn't given, then this is the same as the logged in user name.
        if (authId == null) {
            authId = username;
        }

        // if SASL is enabled, try getting a SASL authenticator first
        if (props.getBooleanProperty("sasl.enable", false)) {
            // we need to convert the mechanisms map into an array of strings for SASL.
            String [] mechs = (String [])mechanisms.toArray(new String[mechanisms.size()]);

            try {

                if(mechanisms.contains(AUTHENTICATION_XOAUTH2)){
                    return new XOAUTH2Authenticator(authId, username, password);
                }

                // need to try to load this using reflection since it has references to
                // the SASL API.  That's only available with 1.5 or later.
                Class authenticatorClass = Class.forName("org.apache.geronimo.javamail.authentication.SASLAuthenticator");
                Constructor c = authenticatorClass.getConstructor(new Class[] {
                    (new String[0]).getClass(),
                    Properties.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class
                });

                Object[] args = { mechs, props.getProperties(), props.getProtocol(), host, realm, authId, username, password };

                return (ClientAuthenticator)c.newInstance(args);
            } catch (Throwable e) {
                // Any exception is likely because we're running on 1.4 and can't use the Sasl API.
                // just ignore and use our fallback implementations.
            }
        }

        // now go through the progression of mechanisms we support, from the
        // most secure to the least secure.

        if (mechanisms.contains(AUTHENTICATION_DIGESTMD5)) {
            return new DigestMD5Authenticator(host, username, password, realm);
        } else if (mechanisms.contains(AUTHENTICATION_CRAMMD5)) {
            return new CramMD5Authenticator(username, password);
        } else if (mechanisms.contains(AUTHENTICATION_LOGIN)) {
            return new LoginAuthenticator(username, password);
        } else if (mechanisms.contains(AUTHENTICATION_PLAIN)) {
            return new PlainAuthenticator(authId, username, password);
        } else {
            // can't find a mechanism we support in common
            return null;
        }
    }
}

