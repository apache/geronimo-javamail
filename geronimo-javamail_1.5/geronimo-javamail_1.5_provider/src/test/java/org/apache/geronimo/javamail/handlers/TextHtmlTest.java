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

/**
 * @version $Rev$ $Date$
 */
public class TextHtmlTest extends AbstractHandler {
    public void testDataFlavor() {
        DataFlavor[] flavours = dch.getTransferDataFlavors();
        assertEquals(1, flavours.length);
        DataFlavor flavor = flavours[0];
        assertEquals(String.class, flavor.getRepresentationClass());
        assertEquals("text/html", flavor.getMimeType());
        assertEquals("HTML Text", flavor.getHumanPresentableName());
    }
    
    protected void setUp() throws Exception {
        super.setUp();
        
        dch = new TextHtmlHandler();
        mimeType = "text/html"; 
    }
}
