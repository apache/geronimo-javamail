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

import java.util.ArrayList; 
import java.util.List;     

import javax.mail.MessagingException;

/**
 * This class adds functionality to the basic response by parsing the reply for
 * LIST command and obtaining specific information about the msgnum and the
 * size. It could be for one or more msgs depending on wether a msg number was
 * passed or not into the LIST command
 * 
 * @see org.apache.geronimo.javamail.store.pop3.POP3Response
 * @see org.apache.geronimo.javamail.store.pop3.response.DefaultPOP3Response
 * 
 * @version $Rev$ $Date$
 */

public class POP3ListResponse extends POP3Response {

    private int msgnum = 0;

    private int size = 0;

    private List multipleMsgs = null;

    POP3ListResponse(POP3Response baseRes) throws MessagingException {
        super(baseRes.getStatus(), baseRes.getFirstLine(), baseRes.getData());

        // if ERR not worth proceeding any further
        if (OK == getStatus()) {

            // if data == null, then it mean it's a single line response
            if (baseRes.getData() == null) {
                String[] args = getFirstLine().split(SPACE);
                try {
                    msgnum = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    throw new MessagingException("Invalid response for LIST command", e);
                }
                try {
                    size = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    throw new MessagingException("Invalid response for LIST command", e);
                }
            } else {
                int totalMsgs = 0;
                String[] args = getFirstLine().split(SPACE);
                try {
                    totalMsgs = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    throw new MessagingException("Invalid response for LIST command", e);
                }
                multipleMsgs = new ArrayList(totalMsgs);
                // Todo : multi-line response parsing
            }

        }
    }

    public int getMessageNumber() {
        return msgnum;
    }

    public int getSize() {
        return size;
    }

    /**
     * Messages can be accessed by multipleMsgs.getElementAt(msgnum)
     * 
     */
    public List getMultipleMessageDetails() {
        return multipleMsgs;
    }

}
