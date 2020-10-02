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

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.mail.MessagingException;

import org.apache.geronimo.javamail.util.ResponseFormatException; 
import org.apache.geronimo.javamail.store.imap.connection.IMAPResponseTokenizer.Token; 

/**
 * Class to represent a FETCH response BODY segment qualifier.  The qualifier is 
 * of the form "BODY[<section>]<<partial>>".  The optional section qualifier is 
 * a "." separated part specifiers.  A part specifier is either a number, or 
 * one of the tokens HEADER, HEADER.FIELD, HEADER.FIELD.NOT, MIME, and TEXT.  
 * The partial specification is in the form "<start.length>". 
 *
 * @version $Rev$ $Date$
 */
public class IMAPBodySection {
    // the section type qualifiers 
    static public final int BODY = 0; 
    static public final int HEADERS = 1; 
    static public final int HEADERSUBSET = 2; 
    static public final int MIME = 3;
    static public final int TEXT = 4; 
    
    // the optional part number 
    public String partNumber = "1"; 
    // the string name of the section 
    public String sectionName = ""; 
    // the section qualifier 
    public int section; 
    // the starting substring position 
    public int start = -1; 
    // the substring length (requested)
    public int length = -1; 
    // the list of any explicit header names 
    public List headers = null; 
    
    /**
     * Construct a simple-toplevel BodySection tag.
     * 
     * @param section The section identifier.
     */
    public IMAPBodySection(int section) {
        this.section = section; 
        partNumber = "1"; 
        start = -1; 
        length = -1; 
    }
    
    /**
     * construct a BodySegment descriptor from the FETCH returned name. 
     * 
     * @param name   The name code, which may be encoded with a section identifier and
     *               substring qualifiers.
     * 
     * @exception MessagingException
     */
    public IMAPBodySection(IMAPResponseTokenizer source) throws MessagingException {
        
        // this could be just "BODY" alone.  
        if (!source.peek(false, true).isType('[')) {
            // complete body, all other fields take default  
            section = BODY;             
            return; 
        }
        
        // now we need to scan along this, building up the pieces as we go. 
        // NOTE:  The section identifiers use "[", "]", "." as delimiters, which 
        // are normally acceptable in ATOM names.  We need to use the expanded 
        // delimiter set to parse these tokens off. 
        Token token = source.next(false, true); 
        // the first token was the "[", now step to the next token in line. 
        token = source.next(false, true); 
        
        if (token.isType(Token.NUMERIC)) {
            token = parsePartNumber(token, source); 
        }
        
        // have a potential name here?
        if (token.isType(Token.ATOM)) {
            token = parseSectionName(token, source); 
        }
        
        // the HEADER.FIELD and HEADER.FIELD.NOT section types 
        // are followed by a list of header names. 
        if (token.isType('(')) {
            token = parseHeaderList(source); 
        }
        
        // ok, in theory, our current token should be a ']'
        if (!token.isType(']')) {
            throw new ResponseFormatException("Invalid section identifier on FETCH response"); 
        }
        
        // do we have a substring qualifier?
        // that needs to be stripped off too 
        parseSubstringValues(source); 
        
        // now fill in the type information 
        if (sectionName.equals("")) {
            section = BODY; 
        }
        else if (sectionName.equals("HEADER")) {
            section = HEADERS; 
        }
        else if (sectionName.equals("HEADER.FIELDS")) {
            section = HEADERSUBSET; 
        }
        else if (sectionName.equals("HEADER.FIELDS.NOT")) {
            section = HEADERSUBSET; 
        }
        else if (sectionName.equals("TEXT")) {
            section = TEXT; 
        }
        else if (sectionName.equals("MIME")) {
            section = MIME; 
        }
    }
    
    
    /**
     * Strip the part number off of a BODY section identifier.  The part number 
     * is a series of "." separated tokens.  So "BODY[3.2.1]" would be the BODY for 
     * section 3.2.1 of a multipart message.  The section may also have a qualifier
     * name on the end.  "BODY[3.2.1.HEADER}" would be the HEADERS for that 
     * body section.  The return value is the name of the section, which can 
     * be a "" or the the section qualifier (e.g., "HEADER"). 
     * 
     * @param name   The section name.
     * 
     * @return The remainder of the section name after the numeric part number has 
     *         been removed.
     */
    private Token parsePartNumber(Token token, IMAPResponseTokenizer source) throws MessagingException {
        StringBuffer part = new StringBuffer(token.getValue()); 
        // NB:  We're still parsing with the expanded delimiter set 
        token = source.next(false, true); 
        
        while (true) {
            // Not a period?  We've reached the end of the section number, 
            // finalize the part number and let the caller figure out what 
            // to do from here.  
            if (!token.isType('.')) {
                partNumber = part.toString(); 
                return token; 
            }
            // might have another number section 
            else {
                // step to the next token 
                token = source.next(false, true); 
                // another section number piece?
                if (token.isType(Token.NUMERIC)) {
                    // add this to the collection, and continue 
                    part.append('.'); 
                    part.append(token.getValue()); 
                    token = source.next(false, true); 
                }
                else  {
                    partNumber = part.toString(); 
                    // this is likely the start of the section name 
                    return token; 
                }
            }
        }
    }
    
    
    /**
     * Parse the section name, if any, in a BODY section qualifier.  The 
     * section name may stand alone within the body section (e.g., 
     * "BODY[HEADERS]" or follow the section number (e.g., 
     * "BODY[1.2.3.HEADERS.FIELDS.NOT]".  
     * 
     * @param token  The first token of the name sequence.
     * @param source The source tokenizer.
     * 
     * @return The first non-name token in the response. 
     */
    private Token parseSectionName(Token token, IMAPResponseTokenizer source) throws MessagingException {
        StringBuffer part = new StringBuffer(token.getValue()); 
        // NB:  We're still parsing with the expanded delimiter set 
        token = source.next(false, true); 
        
        while (true) {
            // Not a period?  We've reached the end of the section number, 
            // finalize the part number and let the caller figure out what 
            // to do from here.  
            if (!token.isType('.')) {
                sectionName = part.toString(); 
                return token; 
            }
            // might have another number section 
            else {
                // add this to the collection, and continue 
                part.append('.'); 
                part.append(source.readString()); 
                token = source.next(false, true); 
            }
        }
    }
    
    
    /**
     * Parse a header list that may follow the HEADER.FIELD or HEADER.FIELD.NOT
     * name qualifier.  This is a list of string values enclosed in parens.
     * 
     * @param source The source tokenizer.
     * 
     * @return The next token in the response (which should be the section terminator, ']')
     * @exception MessagingException
     */
    private Token parseHeaderList(IMAPResponseTokenizer source) throws MessagingException {
        headers = new ArrayList();
        
        // normal parsing rules going on here 
        while (source.notListEnd()) {
            String value = source.readString();
            headers.add(value);
        }
        // step over the closing paren 
        source.next(); 
        // NB, back to the expanded token rules again 
        return source.next(false, true); 
    }
    
    
    /**
     * Parse off the substring values following the section identifier, if 
     * any.  If present, they will be in the format "<start.len>".  
     * 
     * @param source The source tokenizer.
     * 
     * @exception MessagingException
     */
    private void parseSubstringValues(IMAPResponseTokenizer source) throws MessagingException {
        // We rarely have one of these, so it's a quick out 
        if (!source.peek(false, true).isType('<')) {
            return; 
        }
        // step over the angle bracket. 
        source.next(false, true); 
        // pull out the start information 
        start = source.next(false, true).getInteger(); 
        // step over the period 
        source.next(false, true);         
        // now the length bit                  
        length = source.next(false, true).getInteger(); 
        // and consume the closing angle bracket 
        source.next(false, true); 
    }
}

