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

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;  
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import javax.activation.ActivationDataFlavor; 
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.activation.UnsupportedDataTypeException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;

/**
 * @version $Rev$ $Date$
 */
public class AbstractImageHandler implements DataContentHandler {
    private final ActivationDataFlavor flavour;

    public AbstractImageHandler(ActivationDataFlavor flavour) {
        this.flavour = flavour;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{flavour};
    }

    public Object getTransferData(DataFlavor dataFlavor, DataSource dataSource) throws UnsupportedFlavorException, IOException {
        return flavour.equals(dataFlavor) ? getContent(dataSource) : null;
    }

    public Object getContent(DataSource ds) throws IOException {
        Iterator i = ImageIO.getImageReadersByMIMEType(flavour.getMimeType());
        if (!i.hasNext()) {
            throw new UnsupportedDataTypeException("Unknown image type " + flavour.getMimeType());
        }
        ImageReader reader = (ImageReader) i.next();
        ImageInputStream iis = ImageIO.createImageInputStream(ds.getInputStream());
        reader.setInput(iis);
        return reader.read(0);
    }

    public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
        Iterator i = ImageIO.getImageWritersByMIMEType(flavour.getMimeType());
        if (!i.hasNext()) {
            throw new UnsupportedDataTypeException("Unknown image type " + flavour.getMimeType());
        }
        ImageWriter writer = (ImageWriter) i.next();
        writer.setOutput(ImageIO.createImageOutputStream(os));

        if (obj instanceof RenderedImage) {
            writer.write((RenderedImage) obj);
        } else if (obj instanceof BufferedImage) {
            BufferedImage buffered = (BufferedImage) obj;
            writer.write(new IIOImage(buffered.getRaster(), null, null));
        } else if (obj instanceof Image) {
            Image image = (Image) obj;
            BufferedImage buffered = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = buffered.createGraphics();
            graphics.drawImage(image, 0, 0, null, null);
            writer.write(new IIOImage(buffered.getRaster(), null, null));
        } else {
            throw new UnsupportedDataTypeException("Unknown image type " + obj.getClass().getName());
        }
        os.flush();
    }
}
