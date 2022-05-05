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

package org.apache.geronimo.mail.store.imap.connection;

import jakarta.mail.MessagingException;

import org.apache.geronimo.mail.util.Base64;

/**
 * Util class to represent a response from a IMAP server
 *
 * @version $Rev$ $Date$
 */
public class IMAPTaggedResponse extends IMAPResponse {

    // the reply state
    protected String status;
    // the tag associated with a reply.
    protected String tag;
    // the message associated with the completion response 
    protected String message;

    /**
     * Create a command completion response for a 
     * submitted command.  The tag prefix identifies 
     * the command this response is for. 
     * 
     * @param tag      The command tag value.
     * @param status   The Status response (OK, BAD, or NO).
     * @param message  The remainder of the response, as a string.
     * @param response The response data used to create the reply object.
     */
    public IMAPTaggedResponse(String tag, String status, String message, byte [] response) {
        super(response); 
        this.tag = tag; 
        this.status = status;
        this.message = message; 
    }


    /**
     * Create a continuation response for a 
     * submitted command.  
     * 
     * @param response The response data used to create the reply object.
     */
    public IMAPTaggedResponse(byte [] response) {
        super(response); 
        this.tag = "";  
        this.status = "CONTINUATION";
        this.message = message; 
    }

    /**
     * Test if the response code was "OK".
     *
     * @return True if the response status was OK, false for any other status.
     */
    public boolean isOK() {
        return status.equals("OK");
    }

    /**
     * Test for an error return from a command.
     *
     * @return True if the response status was BAD.
     */
    public boolean isBAD() {
        return status.equals("BAD"); 
    }

    /**
     * Test for an error return from a command.
     *
     * @return True if the response status was NO.
     */
    public boolean isNO() {
        return status.equals("NO"); 
    }
    
    /**
     * Get the message included on the tagged response. 
     * 
     * @return The String message data. 
     */
    public String getMessage() {
        return message; 
    }
    
    /**
     * Decode the message portion of a continuation challenge response.
     * 
     * @return The byte array containing the decoded data. 
     */
    public byte[] decodeChallengeResponse() 
    {
        // we're passed back a challenge value, Base64 encoded.  Decode that portion of the 
        // response data. 
    	
    	//handle plain authentication gracefully, see GERONIMO-6526
    	if(response.length <= 2){
    		return null;
    	}
    	
        return Base64.decode(response, 2, response.length - 2);
    }
    
    
    /**
     * Test if this is a continuation response. 
     * 
     * @return True if this a continuation.  false for a normal tagged response. 
     */
    public boolean isContinuation() {
        return status.equals("CONTINUATION"); 
    }
    
    
    /**
     * Test if the untagged response includes a given 
     * status indicator.  Mostly used for checking 
     * READ-ONLY or READ-WRITE status after selecting a 
     * mail box.
     * 
     * @param name   The status name to check.
     * 
     * @return true if this is found in the "[" "]" delimited
     *         section of the response message.
     */
    public boolean hasStatus(String name) {
        // see if we have the status bits at all 
        int statusStart = message.indexOf('['); 
        if (statusStart == -1) {
            return false; 
        }
        
        int statusEnd = message.indexOf(']'); 
        String statusString = message.substring(statusStart, statusEnd).toUpperCase(); 
        // just search for the status token. 
        return statusString.indexOf(name) != -1; 
    }
}


