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

public class IMAPFetchBodyPart extends IMAPFetchDataItem {
    // the parse body section information. 
    protected IMAPBodySection section; 

    /**
     * Construct a base BODY section subpiece.
     * 
     * @param type    The fundamental type of the body section.  This will be either BODY, TEXT,
     *                or HEADER, depending on the subclass.
     * @param section The section information.  This will contain the section numbering information,
     *                the section name, and and substring information if this was a partial fetch
     *                request.
     */
    public IMAPFetchBodyPart(int type, IMAPBodySection section) {
        super(type); 
        this.section = section; 
    }
    
    /**
     * Get the part number information associated with this request.
     * 
     * @return The string form of the part number. 
     */
    public String getPartNumber() {
        return section.partNumber;
    }
    
    /**
     * Get the section type information.  This is the qualifier that appears
     * within the "[]" of the body sections.
     * 
     * @return The numeric identifier for the type from the IMAPBodySection.
     */
    public int getSectionType() {
        return section.section; 
    }
    
    /**
     * Get the substring start location.  
     * 
     * @return The start location for the substring.  Returns -1 if this is not a partial 
     *         fetch.
     */
    public int getSubstringStart() {
        return section.start; 
    }
    
    /**
     * Returns the length of the substring section.  
     * 
     * @return The length of the substring section.  Returns -1 if this was not a partial 
     *         fetch.
     */
    public int getSubstringLength() {
        return section.length; 
    }
}
