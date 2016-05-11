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

package org.apache.geronimo.javamail.store.imap.connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import org.apache.geronimo.javamail.store.imap.connection.IMAPResponseTokenizer.Token;

/**
 * Util class to represent a CAPABILITY response from a IMAP server
 *
 * @version $Rev: 594520 $ $Date: 2007-11-13 07:57:39 -0500 (Tue, 13 Nov 2007) $
 */
public class IMAPCapabilityResponse extends IMAPUntaggedResponse {
    // the advertised capabilities 
    protected Map capabilities = new HashMap(); 
    // the authentication mechanisms.  The order is important with 
    // the authentications, as we a) want to process these in the 
    // order presented, and b) need to convert them into String arrays 
    // for Sasl API calls. 
    protected List authentications = new ArrayList(); 

    /**
     * Create a reply object from a server response line (normally, untagged).  This includes
     * doing the parsing of the response line.
     *
     * @param response The response line used to create the reply object.
     */
    public IMAPCapabilityResponse(IMAPResponseTokenizer source, byte [] response) throws MessagingException {
        super("CAPABILITY", response); 
        
        // parse each of the capability tokens.  We're using the default RFC822 parsing rules,
        // which does not consider "=" to be a delimiter token, so all "AUTH=" capabilities will
        // come through as a single token.
        while (source.hasMore()) {
            // the capabilities are always ATOMs. 
            String value = source.readAtom().toUpperCase(); 
            // is this an authentication option?
            if (value.startsWith("AUTH=")) {
                // parse off the mechanism that fillows the "=", and add this to the supported list.
                String mechanism = value.substring(5);
                authentications.add(mechanism);
            }
            else {
                // just add this to the capabilities map.
                capabilities.put(value, value);
            }
        }
    }
    

    /**
     * Return the capability map for the server.
     * 
     * @return A map of the capability items.
     */
    public Map getCapabilities() {
        return capabilities;
    }
    
    /**
     * Retrieve the map of the server-supported authentication
     * mechanisms.
     * 
     * @return 
     */
    public List getAuthentications() {
        return authentications;
    }
}

