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
import jakarta.activation.ActivationDataFlavor;
import jakarta.activation.DataContentHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMultipart;

/**
 * @version $Rev$ $Date$
 */
public class MultipartHandler implements DataContentHandler {
    private final ActivationDataFlavor flavour;

    public MultipartHandler() {
        flavour = new ActivationDataFlavor(MimeMultipart.class, "multipart/mixed", "Multipart MIME");
    }

    public ActivationDataFlavor[] getTransferDataFlavors() {
        return new ActivationDataFlavor[]{flavour};
    }

    public Object getTransferData(ActivationDataFlavor df, DataSource ds) throws IOException {
        return flavour.equals(df) ? getContent(ds) : null;
    }

    public Object getContent(DataSource ds) throws IOException {
        try {
            return new MimeMultipart(ds);
        } catch (MessagingException e) {
            throw (IOException) new IOException(e.getMessage()).initCause(e);
        }
    }

    public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
        if (obj instanceof MimeMultipart) {
            MimeMultipart mp = (MimeMultipart) obj;
            try {
                mp.writeTo(os);
            } catch (MessagingException e) {
                throw (IOException) new IOException(e.getMessage()).initCause(e);
            }
        }
    }
}
