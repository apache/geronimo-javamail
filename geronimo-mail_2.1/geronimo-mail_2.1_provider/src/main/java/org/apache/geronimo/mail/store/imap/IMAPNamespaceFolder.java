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

package org.apache.geronimo.mail.store.imap;

import org.apache.geronimo.mail.store.imap.connection.IMAPNamespace;

/**
 * An override of the base IMAPFolder class for folders representing namespace roots. 
 * @see javax.mail.Folder
 *
 * @version $Rev$
 */
public class IMAPNamespaceFolder extends IMAPFolder {
    
    IMAPNamespaceFolder(IMAPStore store, IMAPNamespace namespace) {
        // initialize with the namespace information 
        super(store, namespace.prefix, namespace.separator); 
    }
    
    
    /**
     * Override of the default IMAPFolder method to provide the mailbox name 
     * as the prefix + delimiter. 
     * 
     * @return The string name to use as the mailbox name for exists() and issubscribed() 
     *         calls.
     */
    protected String getMailBoxName() {
        // no delimiter is a possibility, so 
        // we need to check.  
        if (separator == '\0') {
            return fullname;
        }
        return fullname + separator; 
    }
}
