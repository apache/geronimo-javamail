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

package org.apache.geronimo.javamail.store.imap.connection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.geronimo.javamail.store.imap.connection.IMAPResponseTokenizer.Token; 
import org.apache.geronimo.javamail.util.ResponseFormatException; 

/**
 * Util class to represent a NAMESPACE response from a IMAP server
 *
 * @version $Rev$ $Date$
 */

public class IMAPNamespaceResponse extends IMAPUntaggedResponse {
    // the personal namespaces defined 
    public List personalNamespaces; 
    // the other use name spaces this user has access to. 
    public List otherUserNamespaces; 
    // the list of shared namespaces 
    public List sharedNamespaces; 
    
    // construct a default IMAPNamespace response for return when the server doesn't support this. 
    public IMAPNamespaceResponse() 
    {
        super("NAMESPACE", null); 
        // fill in default lists to simplify processing 
        personalNamespaces = Collections.EMPTY_LIST; 
        otherUserNamespaces = Collections.EMPTY_LIST; 
        sharedNamespaces = Collections.EMPTY_LIST; 
    }

    /**
     * Construct a LIST response item.  This can be either 
     * a response from a LIST command or an LSUB command, 
     * and will be tagged accordingly.
     * 
     * @param type   The type of resonse (LIST or LSUB).
     * @param data   The raw response data.
     * @param source The tokenizer source.
     * 
     * @exception MessagingException
     */
    public IMAPNamespaceResponse(byte[] data, IMAPResponseTokenizer source) throws MessagingException {
        super("NAMESPACE", data); 
        // the namespace response is a set of 3 items, which will be either NIL or a "list of lists".  
        // if the item exists, then there will be a set of list parens, with 1 or more subitems inside. 
        // Each of the subitems will consist of a namespace prefix and the hierarchy delimiter for that 
        // particular namespace. 
        personalNamespaces = parseNamespace(source); 
        otherUserNamespaces = parseNamespace(source); 
        sharedNamespaces = parseNamespace(source); 
    }
    
    private List parseNamespace(IMAPResponseTokenizer source) throws MessagingException {
        Token token = source.next(true); 
        // is this token the NIL token?
        if (token.getType() == Token.NIL) {
            // no items at this position. 
            return null; 
        }
        if (token.getType() != '(') {
            throw new ResponseFormatException("Missing '(' in response");
        }
        
        // ok, we're processing a namespace list.  Create a list and populate it with IMAPNamespace 
        // items. 
        
        List namespaces = new ArrayList(); 
        
        while (source.notListEnd()) {
            namespaces.add(new IMAPNamespace(source)); 
        }
        // this should always pass, since it terminated the loop 
        source.checkRightParen(); 
        return namespaces; 
    }
}
