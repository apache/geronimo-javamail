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
import java.util.ArrayList;
import java.util.List;

import jakarta.mail.MessagingException;

/**
 * Util class to represent a composite FETCH response from an IMAP server.  The
 * response may have information about multiple message dataItems.
 *
 * @version $Rev$ $Date$
 */
public class IMAPFetchResponse extends IMAPUntaggedResponse {
    // parsed sections within the FETCH response structure 
    protected List dataItems = new ArrayList();
    // the message number to which this applies 
    public int sequenceNumber; 

    public IMAPFetchResponse(int sequenceNumber, byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super("FETCH", data);
        
        this.sequenceNumber = sequenceNumber; 

        // fetch responses are a list, even if there is just a single member.
        source.checkLeftParen();

        // loop until we find the list end.
        while (source.notListEnd()) {
            // the response names are coded as ATOMS.  The BODY one's use a special 
            // syntax, so we need to use the expanded delimiter set to pull this out. 
            String itemName = source.readAtom(true).toUpperCase();

            if (itemName.equals("ENVELOPE")) {
                dataItems.add(new IMAPEnvelope(source));
            }
            else if (itemName.equals("BODYSTRUCTURE")) {
                dataItems.add(new IMAPBodyStructure(source));
            }
            else if (itemName.equals("FLAGS")) {
                dataItems.add(new IMAPFlags(source));
            }
            else if (itemName.equals("INTERNALDATE")) {
                dataItems.add(new IMAPInternalDate(source));
            }
            else if (itemName.equals("UID")) {
                dataItems.add(new IMAPUid(sequenceNumber, source));
            }
            else if (itemName.equals("RFC822")) {
                // all of the RFC822 items are of form 
                // "RFC822.name".  We used the expanded parse above because 
                // the BODY names include some complicated bits.  If we got one 
                // of the RFC822 sections, then parse the rest of the name using 
                // the old rules, which will pull in the rest of the name from the period. 
                itemName = source.readAtom(false).toUpperCase();
                if (itemName.equals(".SIZE")) {
                    dataItems.add(new IMAPMessageSize(source));
                }
                else if (itemName.equals(".HEADER")) {
                    dataItems.add(new IMAPInternetHeader(source.readByteArray()));
                }
                else if (itemName.equals(".TEXT")) {
                    dataItems.add(new IMAPMessageText(source.readByteArray()));
                }
            }
            // this is just the body alone. Specific body segments 
            // have a more complex naming structure.  Believe it or  
            // not, 
            else if (itemName.equals("BODY")) {
                // time to go parse out the section information from the 
                // name.  
                IMAPBodySection section = new IMAPBodySection(source); 
                
                switch (section.section) {
                    case IMAPBodySection.BODY:
                        // a "full body cast".  Just grab the binary data 
                        dataItems.add(new IMAPBody(section, source.readByteArray())); 
                        break; 
                        
                    case IMAPBodySection.HEADERS:
                    case IMAPBodySection.HEADERSUBSET:
                    case IMAPBodySection.MIME:
                        // these 3 are all variations of a header request
                        dataItems.add(new IMAPInternetHeader(section, source.readByteArray())); 
                        break; 
                        
                    case IMAPBodySection.TEXT:
                        // just the text portion of the body 
                        // a "full body cast".  Just grab the binary data 
                        dataItems.add(new IMAPMessageText(section, source.readByteArray())); 
                        break; 
                }
            }
        }
        // swallow the terminating right paren
        source.checkRightParen(); 
    }
    
    /**
     * Retrieve the sequence number for the FETCH item. 
     * 
     * @return The message sequence number this FETCH applies to. 
     */
    public int getSequenceNumber() {
        return sequenceNumber; 
    }

    /**
     * Get the section count.
     *
     * @return The number of sections in the response.
     */
    public int getCount() {
        return dataItems.size();
    }

    /**
     * Get the complete set of response dataItems.
     *
     * @return The List of IMAPFetchResponse values.
     */
    public List getDataItems() {
        return dataItems;
    }


    /**
     * Fetch a particular response type from the response dataItems.
     *
     * @param type   The target FETCH type.
     *
     * @return The first IMAPFetchDataItem item that matches the response type.
     */
    public IMAPFetchDataItem getDataItem(int type) {
        for (int i = 0; i < dataItems.size(); i ++) {
            IMAPFetchDataItem item = (IMAPFetchDataItem)dataItems.get(i);
            if (item.isType(type)) {
                return item;
            }
        }
        return null;
    }
    
}

