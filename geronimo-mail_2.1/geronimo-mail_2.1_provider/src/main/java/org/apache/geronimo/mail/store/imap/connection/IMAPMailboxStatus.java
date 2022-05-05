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

import java.util.List;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;

import org.apache.geronimo.mail.store.imap.connection.IMAPResponseTokenizer.Token;


/**
 * Utility class to aggregate status responses for a mailbox.
 */
public class IMAPMailboxStatus {
    // the set of available flag values for this mailbox
    public Flags availableFlags = null;
    // the permanent flags for this mailbox.
    public Flags permanentFlags = null;
    // the open mode flags
    public int mode = Folder.READ_WRITE;

    // number of messages in the box
    public int messages = -1;
    // the number of newly added messages
    public int recentMessages = -1;
    // the number of unseen messages
    public int unseenMessages = -1;

    // the next UID for this mailbox
    public long uidNext = -1L;
    // the UID validity item
    public long uidValidity = -1L;

    public IMAPMailboxStatus() {
    }


    /**
     * Merge information from a server status message.  These
     * messages are in the form "* NAME args".  We only handle
     * STATUS and FLAGS messages here.
     *
     * @param source The parsed status message.
     *
     * @exception MessagingException
     */
    public void mergeStatus(IMAPStatusResponse source) throws MessagingException {
        // update any of the values that have changed since the last. 
        if (source.messages != -1) {
            messages = source.messages; 
        }
        if (source.uidNext != -1L) {
            uidNext = source.uidNext; 
        }
        if (source.uidValidity != -1L) {
            uidValidity = source.uidValidity; 
        }
        if (source.recentMessages != -1) {
            recentMessages = source.recentMessages; 
        }
        if (source.unseenMessages != -1) {
            unseenMessages = source.unseenMessages; 
        }
    }
    
    /**
     * Merge in the FLAGS response from an EXAMINE or 
     * SELECT mailbox command.
     * 
     * @param response The returned FLAGS item.
     * 
     * @exception MessagingException
     */
    public void mergeFlags(IMAPFlagsResponse response) throws MessagingException {
        if (response != null) {
            availableFlags = response.getFlags(); 
        }
    }
    
    
    public void mergeSizeResponses(List responses) throws MessagingException  
      {  
        for (int i = 0; i < responses.size(); i++) {
            mergeStatus((IMAPSizeResponse)responses.get(i)); 
        }
    }
    
    
    public void mergeOkResponses(List responses) throws MessagingException {
        for (int i = 0; i < responses.size(); i++) {
            mergeStatus((IMAPOkResponse)responses.get(i)); 
        }
    }

    
    /**
     * Gather mailbox status information from mailbox status
     * messages.  These messages come in as untagged messages in the
     * form "* nnn NAME".
     *
     * @param source The parse message information.
     *
     * @exception MessagingException
     */
    public void mergeStatus(IMAPSizeResponse source) throws MessagingException {
        if (source != null) {
            String name = source.getKeyword(); 

            // untagged exists response
            if (source.isKeyword("EXISTS")) {
                messages = source.getSize();
            }
            // untagged resent response
            else if (source.isKeyword("RECENT")) {
                recentMessages = source.getSize();
            }
        }
    }

    

    
    /**
     * Gather mailbox status information from mailbox status
     * messages.  These messages come in as untagged messages in the
     * form "* OK [NAME args]".
     *
     * @param source The parse message information.
     *
     * @exception MessagingException
     */
    public void mergeStatus(IMAPOkResponse source) throws MessagingException {
        if (source != null) {
            String name = source.getKeyword(); 

            // untagged UIDVALIDITY response 
            if (source.isKeyword("UIDVALIDITY")) {
                List arguments = source.getStatus(); 
                uidValidity = ((Token)arguments.get(0)).getLong(); 
            }
            // untagged UIDNEXT response 
            if (source.isKeyword("UIDNEXT")) {
                List arguments = source.getStatus(); 
                uidNext = ((Token)arguments.get(0)).getLong(); 
            }
            // untagged unseen response
            else if (source.isKeyword("UNSEEN")) {
                List arguments = source.getStatus(); 
                uidValidity = ((Token)arguments.get(0)).getInteger(); 
            }
        }
    }

    
    /**
     * Gather mailbox status information from mailbox status
     * messages.  These messages come in as untagged messages in the
     * form "* OK [NAME args]".
     *
     * @param source The parse message information.
     *
     * @exception MessagingException
     */
    public void mergeStatus(IMAPPermanentFlagsResponse source) throws MessagingException {
        if (source != null) {
            // this is already parsed.          
            permanentFlags = source.flags; 
        }
    }
}
