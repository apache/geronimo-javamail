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
package org.apache.geronimo.javamail.handlers;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

import javax.activation.DataContentHandler;
import javax.activation.DataSource;

import javax.mail.internet.ContentType; 
import javax.mail.internet.MimeUtility;

/**
 * @version $Rev: 669902 $ $Date: 2008-06-20 10:04:41 -0400 (Fri, 20 Jun 2008) $
 */
public class AbstractTextHandler implements DataContentHandler {
    private final DataFlavor flavour;

    public AbstractTextHandler(DataFlavor flavour) {
        this.flavour = flavour;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {flavour};
    }

    public Object getTransferData(DataFlavor dataFlavor, DataSource dataSource) throws UnsupportedFlavorException, IOException {
        return flavour.equals(dataFlavor) ? getContent(dataSource) : null;
    }

    /**
     * Read the content from the DataSource and transform 
     * it into a text object (String). 
     * 
     * @param ds     The source DataSource.
     * 
     * @return The content object. 
     * @exception IOException
     */
    public Object getContent(DataSource ds) throws IOException {
        InputStream is = ds.getInputStream(); 
        InputStreamReader reader;
        // process any encoding to make sure the chars get transformed into the 
        // correct byte types. 
        try {
            String charset = getCharSet(ds.getContentType());
            reader = new InputStreamReader(is, charset);
        } catch (Exception ex) {
            throw new UnsupportedEncodingException(ex.toString());
        }
        StringBuffer result = new StringBuffer(1024);
        char[] buffer = new char[32768];
        int count;
        while ((count = reader.read(buffer)) > 0) {
            result.append(buffer, 0, count);
        }
        return result.toString();
    }

    
    /**
     * Write an object of "our" type out to the provided 
     * output stream.  The content type might modify the 
     * result based on the content type parameters. 
     * 
     * @param object The object to write.
     * @param contentType
     *               The content mime type, including parameters.
     * @param outputstream
     *               The target output stream.
     * 
     * @throws IOException
     */
    public void writeTo(Object o, String contentType, OutputStream outputstream) throws IOException {
        String s;
        if (o instanceof String) {
            s = (String) o;
        } else if (o != null) {
            s = o.toString();
        } else {
            return;
        }
        // process any encoding to make sure the chars get transformed into the 
        // correct byte types. 
        OutputStreamWriter writer;
        try {
            String charset = getCharSet(contentType);
            writer = new OutputStreamWriter(outputstream, charset);
        } catch (Exception ex) {
            ex.printStackTrace(); 
            throw new UnsupportedEncodingException(ex.toString());
        }
        writer.write(s);
        writer.flush();
    }
    

    /**
     * get the character set from content type
     * @param contentType
     * @return
     * @throws ParseException
     */
    protected String getCharSet(String contentType) throws Exception {
        ContentType type = new ContentType(contentType);
        String charset = type.getParameter("charset");
        if (charset == null) {
            charset = "us-ascii";
        }
        return MimeUtility.javaCharset(charset);
    }
}
