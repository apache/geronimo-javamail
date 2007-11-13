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

package org.apache.geronimo.javamail.store.imap;

import javax.activation.DataHandler;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.MethodNotSupportedException;

import org.apache.geronimo.javamail.store.imap.connection.IMAPEnvelope;
import org.apache.geronimo.javamail.store.imap.connection.IMAPBodyStructure;

/**
 * A nested message attachement inside of another 
 * IMAP message.  This is a less-functional version 
 * of the top-level message.
 */
public class IMAPAttachedMessage extends IMAPMessage {
    // the parent enclosing message.
    protected IMAPMessage parent;

    /**
     * Constructor for an attached message part.
     * 
     * @param parent   The parent message (outer-most message).
     * @param section  The section identifier for this embedded part
     *                 in IMAP section format.  This will identify
     *                 the part hierarchy used to locate this part within
     *                 the message.
     * @param envelope The Envelope that describes this part.
     * @param bodyStructure
     *                 The Body structure element that describes this part.
     */
    public IMAPAttachedMessage(IMAPMessage parent, String section, IMAPEnvelope envelope, IMAPBodyStructure bodyStructure) {
        super((IMAPFolder)parent.getFolder(), parent.store, parent.getMessageNumber(), parent.sequenceNumber);
        this.parent = parent;
        // sets the subset we're looking for 
        this.section = section;
        // the envelope and body structure are loaded from the server by the parent 
        this.envelope = envelope;
        this.bodyStructure = bodyStructure;
    }

    /**
     * Check if this message is still valid.  This is 
     * delegated to the outer-most message.
     * 
     * @exception MessagingException
     */
    protected void checkValidity() throws MessagingException {
        parent.checkValidity();
    }

    /**
     * Check if the outer-most message has been expunged.
     * 
     * @return true if the message has been expunged.
     */
    public boolean isExpunged() {
        return parent.isExpunged();
    }

    /**
     * Get the size of this message part.
     * 
     * @return The estimate size of this message part, in bytes.
     */
    public int getSize() {
        return bodyStructure.bodySize;
    }

    
    /**
     * Return a copy the flags associated with this message.
     *
     * @return a copy of the flags for this message
     * @throws MessagingException if there was a problem accessing the Store
     */
    public Flags getFlags() throws MessagingException {
        return parent.getFlags(); 
    }


    /**
     * Check whether the supplied flag is set.
     * The default implementation checks the flags returned by {@link #getFlags()}.
     *
     * @param flag the flags to check for
     * @return true if the flags is set
     * @throws MessagingException if there was a problem accessing the Store
     */
    public boolean isSet(Flags.Flag flag) throws MessagingException {
        // load the flags, if needed 
        return parent.isSet(flag); 
    }

    /**
     * Set or clear a flag value.
     *
     * @param flags  The set of flags to effect.
     * @param set    The value to set the flag to (true or false).
     *
     * @exception MessagingException
     */
    public void setFlags(Flags flag, boolean set) throws MessagingException {
        throw new MethodNotSupportedException("Flags cannot be set on message attachements"); 
    }
}

