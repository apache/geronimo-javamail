/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.geronimo.mail.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataContentHandler;
import jakarta.activation.DataSource;
import jakarta.mail.Message;
import jakarta.mail.MessageAware;
import jakarta.mail.MessageContext;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * Content handler for RFC-822 compliant messages.
 * @version $Rev$ $Date$
 */
public class RFC822MessageHandler implements DataContentHandler {
    // the data flavor defines what this looks like, and is fixed once the
    // handler is instantiated
    protected final ActivationDataFlavor flavour;

    public RFC822MessageHandler() {
        flavour = new ActivationDataFlavor(Message.class, "message/rfc822", "Message");
    }

    /**
     * Return all of the flavors processed by this handler.  This
     * is just the singleton flavor.
     *
     * @return An array of the transfer flavors supported by this handler.
     */
    public ActivationDataFlavor[] getTransferDataFlavors() {
        return new ActivationDataFlavor[] { flavour };
    }

    /**
     * Retrieve the transfer data from the data source, but
     * only if the requested flavor matches what we support.
     *
     * @param df     The requested data flavor.
     * @param ds     The source DataSource.
     *
     * @return The extracted content object, or null if there is a
     *         mismatch of flavors.
     * @exception IOException
     */
    public Object getTransferData(ActivationDataFlavor df, DataSource ds) throws IOException {
        return flavour.equals(df) ? getContent(ds) : null;
    }

    /**
     * Extract the RFC822 Message content from a DataSource.
     *
     * @param ds     The source data source.
     *
     * @return An extracted MimeMessage object.
     * @exception IOException
     */
    public Object getContent(DataSource ds) throws IOException {
        try {
            // creating a MimeMessage instance requires a session.  If the DataSource
            // is a MessageAware one, we can get the session information from the MessageContext.
            // This is generally the case, but if it is not available, then just retrieve
            // the default instance and use it.
            if (ds instanceof MessageAware) {
                MessageContext context = ((MessageAware)ds).getMessageContext();
                return new MimeMessage(context.getSession(), ds.getInputStream());
            }
            else {
                return new MimeMessage(Session.getDefaultInstance(new Properties(), null), ds.getInputStream());
            }
        } catch (MessagingException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }

    /**
     * Write an RFC 822 message object out to an output stream.
     *
     * @param obj      The source message object.
     * @param mimeType The target mimetype
     * @param os       The target output stream.
     *
     * @exception IOException
     */
    public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
        // we only handle message instances here
        if (obj instanceof Message) {
            Message message = (Message) obj;
            try {
                message.writeTo(os);
            } catch (MessagingException e) {
                throw (IOException) new IOException(e.getMessage()).initCause(e);
            }
        }
    }
}
