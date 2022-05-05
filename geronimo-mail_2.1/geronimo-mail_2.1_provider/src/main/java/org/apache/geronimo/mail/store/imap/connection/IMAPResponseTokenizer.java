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

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MailDateFormat;
import jakarta.mail.internet.ParameterList;

import org.apache.geronimo.mail.util.ResponseFormatException;

/**
 * @version $Rev$ $Date$
 */
public class IMAPResponseTokenizer {
    /*
     * set up the decoding table.
     */
    protected static final byte[] decodingTable = new byte[256];

    protected static void initializeDecodingTable()
    {
        for (int i = 0; i < IMAPCommand.encodingTable.length; i++)
        {
            decodingTable[IMAPCommand.encodingTable[i]] = (byte)i;
        }
    }


    static {
        initializeDecodingTable();
    }

    // a singleton formatter for header dates.
    protected static MailDateFormat dateParser = new MailDateFormat();


    public static class Token {
        // Constant values from J2SE 1.4 API Docs (Constant values)
        public static final int ATOM = -1;
        public static final int QUOTEDSTRING = -2;
        public static final int LITERAL = -3;
        public static final int NUMERIC = -4;
        public static final int EOF = -5;
        public static final int NIL = -6;
        // special single character markers
        public static final int CONTINUATION = '+';
        public static final int UNTAGGED = '*';

        /**
         * The type indicator.  This will be either a specific type, represented by
         * a negative number, or the actual character value.
         */
        private int type;
        /**
         * The String value associated with this token.  All tokens have a String value,
         * except for the EOF and NIL tokens.
         */
        private String value;

        public Token(int type, String value) {
            this.type = type;
            this.value = value;
        }

        public int getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public boolean isType(int type) {
            return this.type == type;
        }

        /**
         * Return the token as an integer value.  If this can't convert, an exception is
         * thrown.
         *
         * @return The integer value of the token.
         * @exception ResponseFormatException
         */
        public int getInteger() throws MessagingException {
            if (value != null) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                }
            }

            throw new ResponseFormatException("Number value expected in response; fount: " + value);
        }

        /**
         * Return the token as a long value.  If it can't convert, an exception is
         * thrown.
         *
         * @return The token as a long value.
         * @exception ResponseFormatException
         */
        public long getLong() throws MessagingException {
            if (value != null) {
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                }
            }
            throw new ResponseFormatException("Number value expected in response; fount: " + value);
        }

        /**
         * Handy debugging toString() method for token.
         *
         * @return The string value of the token.
         */
        public String toString() {
            if (type == NIL) {
                return "NIL";
            }
            else if (type == EOF) {
                return "EOF";
            }

            if (value == null) {
                return "";
            }
            return value;
        }
    }

    public static final Token EOF = new Token(Token.EOF, null);
    public static final Token NIL = new Token(Token.NIL, null);

    private static final String WHITE = " \t\n\r";
    // The list of delimiter characters we process when
    // handling parsing of ATOMs.
    private static final String atomDelimiters = "(){}%*\"\\" + WHITE;
    // this set of tokens is a slighly expanded set used for
    // specific response parsing.  When dealing with Body
    // section names, there are sub pieces to the name delimited
    // by "[", "]", ".", "<", ">" and SPACE, so reading these using
    // a superset of the ATOM processing makes for easier parsing.
    private static final String tokenDelimiters = "<>[].(){}%*\"\\" + WHITE;

    // the response data read from the connection
    private byte[] response;
    // current parsing position
    private int pos;

    public IMAPResponseTokenizer(byte [] response) {
        this.response = response;
    }

    /**
     * Get the remainder of the response as a string.
     *
     * @return A string representing the remainder of the response.
     */
    public String getRemainder() {
        // make sure we're still in range
        if (pos >= response.length) {
            return "";
        }

        try {
            return new String(response, pos, response.length - pos, "ISO8859-1");
        } catch (UnsupportedEncodingException e) {
            return null; 
        }
    }


    public Token next() throws MessagingException {
        return next(false);
    }

    public Token next(boolean nilAllowed) throws MessagingException {
        return readToken(nilAllowed, false);
    }

    public Token next(boolean nilAllowed, boolean expandedDelimiters) throws MessagingException {
        return readToken(nilAllowed, expandedDelimiters);
    }

    public Token peek() throws MessagingException {
        return peek(false, false);
    }

    public Token peek(boolean nilAllowed) throws MessagingException {
        return peek(nilAllowed, false);
    }

    public Token peek(boolean nilAllowed, boolean expandedDelimiters) throws MessagingException {
        int start = pos;
        try {
            return readToken(nilAllowed, expandedDelimiters);
        } finally {
            pos = start;
        }
    }

    /**
     * Read an ATOM token from the parsed response.
     *
     * @return A token containing the value of the atom token.
     */
    private Token readAtomicToken(String delimiters) {
        // skip to next delimiter
        int start = pos;
        while (++pos < response.length) {
            // break on the first non-atom character.
            byte ch = response[pos];
            if (delimiters.indexOf(response[pos]) != -1 || ch < 32 || ch >= 127) {
                break;
            }
        }

        try {
            // Numeric tokens we store as a different type.
            String value = new String(response, start, pos - start, "ISO8859-1");
            try {
                int intValue = Integer.parseInt(value);
                return new Token(Token.NUMERIC, value);
            } catch (NumberFormatException e) {
            }
            return new Token(Token.ATOM, value);
        } catch (UnsupportedEncodingException e) {
            return null; 
        }
    }

    /**
     * Read the next token from the response.
     *
     * @return The next token from the response.  White space is skipped, and comment
     *         tokens are also skipped if indicated.
     * @exception ResponseFormatException
     */
    private Token readToken(boolean nilAllowed, boolean expandedDelimiters) throws MessagingException {
        String delimiters = expandedDelimiters ? tokenDelimiters : atomDelimiters;

        if (pos >= response.length) {
            return EOF;
        } else {
            byte ch = response[pos];
            if (ch == '\"') {
                return readQuotedString();
            // beginning of a length-specified literal?
            } else if (ch == '{') {
                return readLiteral();
            // white space, eat this and find a real token.
            } else if (WHITE.indexOf(ch) != -1) {
                eatWhiteSpace();
                return readToken(nilAllowed, expandedDelimiters);
            // either a CTL or special.  These characters have a self-defining token type.
            } else if (ch < 32 || ch >= 127 || delimiters.indexOf(ch) != -1) {
                pos++;
                return new Token((int)ch, String.valueOf((char)ch));
            } else {
                // start of an atom, parse it off.
                Token token = readAtomicToken(delimiters);
                // now, if we've been asked to look at NIL tokens, check to see if it is one,
                // and return that instead of the ATOM.
                if (nilAllowed) {
                    if (token.getValue().equalsIgnoreCase("NIL")) {
                        return NIL;
                    }
                }
                return token;
            }
        }
    }

    /**
     * Read the next token from the response, returning it as a byte array value.
     *
     * @return The next token from the response.  White space is skipped, and comment
     *         tokens are also skipped if indicated.
     * @exception ResponseFormatException
     */
    private byte[] readData(boolean nilAllowed) throws MessagingException {
        if (pos >= response.length) {
            return null;
        } else {
            byte ch = response[pos];
            if (ch == '\"') {
                return readQuotedStringData();
            // beginning of a length-specified literal?
            } else if (ch == '{') {
                return readLiteralData();
            // white space, eat this and find a real token.
            } else if (WHITE.indexOf(ch) != -1) {
                eatWhiteSpace();
                return readData(nilAllowed);
            // either a CTL or special.  These characters have a self-defining token type.
            } else if (ch < 32 || ch >= 127 || atomDelimiters.indexOf(ch) != -1) {
                throw new ResponseFormatException("Invalid string value: " + ch);
            } else {
                // only process this if we're allowing NIL as an option.
                if (nilAllowed) {
                    // start of an atom, parse it off.
                    Token token = next(true);
                    if (token.isType(Token.NIL)) {
                        return null;
                    }
                    // invalid token type.
                    throw new ResponseFormatException("Invalid string value: " + token.getValue());
                }
                // invalid token type.
                throw new ResponseFormatException("Invalid string value: " + ch);
            }
        }
    }

    /**
     * Extract a substring from the response string and apply any
     * escaping/folding rules to the string.
     *
     * @param start  The starting offset in the response.
     * @param end    The response end offset + 1.
     *
     * @return The processed string value.
     * @exception ResponseFormatException
     */
    private byte[] getEscapedValue(int start, int end) throws MessagingException {
        ByteArrayOutputStream value = new ByteArrayOutputStream();

        for (int i = start; i < end; i++) {
            byte ch = response[i];
            // is this an escape character?
            if (ch == '\\') {
                i++;
                if (i == end) {
                    throw new ResponseFormatException("Invalid escape character");
                }
                value.write(response[i]);
            }
            // line breaks are ignored, except for naked '\n' characters, which are consider
            // parts of linear whitespace.
            else if (ch == '\r') {
                // see if this is a CRLF sequence, and skip the second if it is.
                if (i < end - 1 && response[i + 1] == '\n') {
                    i++;
                }
            }
            else {
                // just append the ch value.
                value.write(ch);
            }
        }
        return value.toByteArray();
    }

    /**
     * Parse out a quoted string from the response, applying escaping
     * rules to the value.
     *
     * @return The QUOTEDSTRING token with the value.
     * @exception ResponseFormatException
     */
    private Token readQuotedString() throws MessagingException {
        try {
            String value = new String(readQuotedStringData(), "ISO8859-1");
            return new Token(Token.QUOTEDSTRING, value);
        } catch (UnsupportedEncodingException e) {
            return null; 
        }
    }

    /**
     * Parse out a quoted string from the response, applying escaping
     * rules to the value.
     *
     * @return The byte array with the resulting string bytes.
     * @exception ResponseFormatException
     */
    private byte[] readQuotedStringData() throws MessagingException {
        int start = pos + 1;
        boolean requiresEscaping = false;

        // skip to end of comment/string
        while (++pos < response.length) {
            byte ch = response[pos];
            if (ch == '"') {
                byte[] value;
                if (requiresEscaping) {
                    value = getEscapedValue(start, pos);
                }
                else {
                    value = subarray(start, pos);
                }
                // step over the delimiter for all cases.
                pos++;
                return value;
            }
            else if (ch == '\\') {
                pos++;
                requiresEscaping = true;
            }
            // we need to process line breaks also
            else if (ch == '\r') {
                requiresEscaping = true;
            }
        }

        throw new ResponseFormatException("Missing '\"'");
    }


    /**
     * Parse out a literal string from the response, using the length
     * encoded before the listeral.
     *
     * @return The LITERAL token with the value.
     * @exception ResponseFormatException
     */
    protected Token readLiteral() throws MessagingException {
        try {
            String value = new String(readLiteralData(), "ISO8859-1");
            return new Token(Token.LITERAL, value);
        } catch (UnsupportedEncodingException e) {
            return null; 
        }
    }


    /**
     * Parse out a literal string from the response, using the length
     * encoded before the listeral.
     *
     * @return The byte[] array with the value.
     * @exception ResponseFormatException
     */
    protected byte[] readLiteralData() throws MessagingException {
        int lengthStart = pos + 1;

        // see if we have a close marker.
        int lengthEnd = indexOf("}\r\n", lengthStart);
        if (lengthEnd == -1) {
            throw new ResponseFormatException("Missing terminator on literal length");
        }

        int count = 0;
        try {
            count = Integer.parseInt(substring(lengthStart, lengthEnd));
        } catch (NumberFormatException e) {
            throw new ResponseFormatException("Invalid literal length " + substring(lengthStart, lengthEnd));
        }

        // step over the length
        pos = lengthEnd + 3;

        // too long?
        if (pos + count > response.length) {
            throw new ResponseFormatException("Invalid literal length: " + count);
        }

        byte[] value = subarray(pos, pos + count);
        pos += count;

        return value;
    }


    /**
     * Extract a substring from the response buffer.
     *
     * @param start  The starting offset.
     * @param end    The end offset (+ 1).
     *
     * @return A String extracted from the buffer.
     */
    protected String substring(int start, int end ) {
        try {
            return new String(response, start, end - start, "ISO8859-1");
        } catch (UnsupportedEncodingException e) {
            return null; 
        }
    }


    /**
     * Extract a subarray from the response buffer.
     *
     * @param start  The starting offset.
     * @param end    The end offset (+ 1).
     *
     * @return A byte array string extracted rom the buffer.
     */
    protected byte[] subarray(int start, int end ) {
        byte[] result = new byte[end - start];
        System.arraycopy(response, start, result, 0, end - start);
        return result;
    }


    /**
     * Test if the bytes in the response buffer match a given
     * string value.
     *
     * @param position The compare position.
     * @param needle   The needle string we're testing for.
     *
     * @return True if the bytes match the needle value, false for any
     *         mismatch.
     */
    public boolean match(int position, String needle) {
        int length = needle.length();

        if (response.length - position < length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (response[position + i ] != needle.charAt(i)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Search for a given string starting from the current position
     * cursor.
     *
     * @param needle The search string.
     *
     * @return The index of a match (in absolute byte position in the
     *         response buffer).
     */
    public int indexOf(String needle) {
        return indexOf(needle, pos);
    }

    /**
     * Search for a string in the response buffer starting from the
     * indicated position.
     *
     * @param needle   The search string.
     * @param position The starting buffer position.
     *
     * @return The index of the match position.  Returns -1 for no match.
     */
    public int indexOf(String needle, int position) {
        // get the last possible match position
        int last = response.length - needle.length();
        // no match possible
        if (last < position) {
            return -1;
        }

        for (int i = position; i <= last; i++) {
            if (match(i, needle)) {
                return i;
            }
        }
        return -1;
    }



    /**
     * Skip white space in the token string.
     */
    private void eatWhiteSpace() {
        // skip to end of whitespace
        while (++pos < response.length
                && WHITE.indexOf(response[pos]) != -1)
            ;
    }


    /**
     * Ensure that the next token in the parsed response is a
     * '(' character.
     *
     * @exception ResponseFormatException
     */
    public void checkLeftParen() throws MessagingException {
        Token token = next();
        if (token.getType() != '(') {
            throw new ResponseFormatException("Missing '(' in response");
        }
    }


    /**
     * Ensure that the next token in the parsed response is a
     * ')' character.
     *
     * @exception ResponseFormatException
     */
    public void checkRightParen() throws MessagingException {
        Token token = next();
        if (token.getType() != ')') {
            throw new ResponseFormatException("Missing ')' in response");
        }
    }


    /**
     * Read a string-valued token from the response.  A string
     * valued token can be either a quoted string, a literal value,
     * or an atom.  Any other token type is an error.
     *
     * @return The string value of the source token.
     * @exception ResponseFormatException
     */
    public String readString() throws MessagingException {
        Token token = next(true);
        int type = token.getType();
        if (type == Token.NIL) {
            return null;
        }
        if (type != Token.ATOM && type != Token.QUOTEDSTRING && type != Token.LITERAL && type != Token.NUMERIC) {
            throw new ResponseFormatException("String token expected in response: " + token.getValue());
        }
        return token.getValue();
    }


    /**
     * Read an encoded string-valued token from the response.  A string
     * valued token can be either a quoted string, a literal value,
     * or an atom.  Any other token type is an error.
     *
     * @return The string value of the source token.
     * @exception ResponseFormatException
     */
    public String readEncodedString() throws MessagingException {
        String value = readString();
        return decode(value);
    }


    /**
     * Decode a Base 64 encoded string value.
     *
     * @param original The original encoded string.
     *
     * @return The decoded string.
     * @exception MessagingException
     */
    public String decode(String original) throws MessagingException {
        StringBuffer result = new StringBuffer();

        for (int i = 0; i < original.length(); i++) {
            char ch = original.charAt(i);

            if (ch == '&') {
                i = decode(original, i, result);
            }
            else {
                result.append(ch);
            }
        }

        return result.toString();
    }


    /**
     * Decode a section of an encoded string value.
     *
     * @param original The original source string.
     * @param index    The current working index.
     * @param result   The StringBuffer used for the decoded result.
     *
     * @return The new index for the decoding operation.
     * @exception MessagingException
     */
    public static int decode(String original, int index, StringBuffer result) throws MessagingException {
        // look for the section terminator
        int terminator = original.indexOf('-', index);

        // unmatched?
        if (terminator == -1) {
            throw new MessagingException("Invalid UTF-7 encoded string");
        }

        // is this just an escaped "&"?
        if (terminator == index + 1) {
            // append and skip over this.
            result.append('&');
            return index + 2;
        }

        // step over the starting char
        index++;

        int chars = terminator - index;
        int quads = chars / 4;
        int residual = chars % 4;

        // buffer for decoded characters
        byte[] buffer = new byte[4];
        int bufferCount = 0;

        // process each of the full triplet pairs
        for (int i = 0; i < quads; i++) {
            byte b1 = decodingTable[original.charAt(index++) & 0xff];
            byte b2 = decodingTable[original.charAt(index++) & 0xff];
            byte b3 = decodingTable[original.charAt(index++) & 0xff];
            byte b4 = decodingTable[original.charAt(index++) & 0xff];

            buffer[bufferCount++] = (byte)((b1 << 2) | (b2 >> 4));
            buffer[bufferCount++] = (byte)((b2 << 4) | (b3 >> 2));
            buffer[bufferCount++] = (byte)((b3 << 6) | b4);

            // we've written 3 bytes to the buffer, but we might have a residual from a previous
            // iteration to deal with.
            if (bufferCount == 4) {
                // two complete chars here
                b1 = buffer[0];
                b2 = buffer[1];
                result.append((char)((b1 << 8) + (b2 & 0xff)));
                b1 = buffer[2];
                b2 = buffer[3];
                result.append((char)((b1 << 8) + (b2 & 0xff)));
                bufferCount = 0;
            }
            else {
                // we need to save the 3rd byte for the next go around
                b1 = buffer[0];
                b2 = buffer[1];
                result.append((char)((b1 << 8) + (b2 & 0xff)));
                buffer[0] = buffer[2];
                bufferCount = 1;
            }
        }

        // properly encoded, we should have an even number of bytes left.

        switch (residual) {
            // no residual...so we better not have an extra in the buffer
            case 0:
                // this is invalid...we have an odd number of bytes so far,
                if (bufferCount == 1) {
                    throw new MessagingException("Invalid UTF-7 encoded string");
                }
            // one byte left.  This shouldn't be valid.  We need at least 2 bytes to
            // encode one unprintable char.
            case 1:
                throw new MessagingException("Invalid UTF-7 encoded string");

            // ok, we have two bytes left, which can only encode a single byte.  We must have
            // a dangling unhandled char.
            case 2:
            {
                if (bufferCount != 1) {
                    throw new MessagingException("Invalid UTF-7 encoded string");
                }
                byte b1 = decodingTable[original.charAt(index++) & 0xff];
                byte b2 = decodingTable[original.charAt(index++) & 0xff];
                buffer[bufferCount++] = (byte)((b1 << 2) | (b2 >> 4));

                b1 = buffer[0];
                b2 = buffer[1];
                result.append((char)((b1 << 8) + (b2 & 0xff)));
                break;
            }

            // we have 2 encoded chars.  In this situation, we can't have a leftover.
            case 3:
            {
                // this is invalid...we have an odd number of bytes so far,
                if (bufferCount == 1) {
                    throw new MessagingException("Invalid UTF-7 encoded string");
                }
                byte b1 = decodingTable[original.charAt(index++) & 0xff];
                byte b2 = decodingTable[original.charAt(index++) & 0xff];
                byte b3 = decodingTable[original.charAt(index++) & 0xff];

                buffer[bufferCount++] = (byte)((b1 << 2) | (b2 >> 4));
                buffer[bufferCount++] = (byte)((b2 << 4) | (b3 >> 2));

                b1 = buffer[0];
                b2 = buffer[1];
                result.append((char)((b1 << 8) + (b2 & 0xff)));
                break;
            }
        }

        // return the new scan location
        return terminator + 1;
    }

    /**
     * Read a string-valued token from the response, verifying this is an ATOM token.
     *
     * @return The string value of the source token.
     * @exception ResponseFormatException
     */
    public String readAtom() throws MessagingException {
        return readAtom(false);
    }


    /**
     * Read a string-valued token from the response, verifying this is an ATOM token.
     *
     * @return The string value of the source token.
     * @exception ResponseFormatException
     */
    public String readAtom(boolean expandedDelimiters) throws MessagingException {
        Token token = next(false, expandedDelimiters);
        int type = token.getType();

        if (type != Token.ATOM) {
            throw new ResponseFormatException("ATOM token expected in response: " + token.getValue());
        }
        return token.getValue();
    }


    /**
     * Read a number-valued token from the response.  This must be an ATOM
     * token.
     *
     * @return The integer value of the source token.
     * @exception ResponseFormatException
     */
    public int readInteger() throws MessagingException {
        Token token = next();
        return token.getInteger();
    }


    /**
     * Read a number-valued token from the response.  This must be an ATOM
     * token.
     *
     * @return The long value of the source token.
     * @exception ResponseFormatException
     */
    public int readLong() throws MessagingException {
        Token token = next();
        return token.getInteger();
    }


    /**
     * Read a string-valued token from the response.  A string
     * valued token can be either a quoted string, a literal value,
     * or an atom.  Any other token type is an error.
     *
     * @return The string value of the source token.
     * @exception ResponseFormatException
     */
    public String readStringOrNil() throws MessagingException {
        // we need to recognize the NIL token.
        Token token = next(true);
        int type = token.getType();

        if (type != Token.ATOM && type != Token.QUOTEDSTRING && type != Token.LITERAL && type != Token.NIL) {
            throw new ResponseFormatException("String token or NIL expected in response: " + token.getValue());
        }
        // this returns null if the token is the NIL token.
        return token.getValue();
    }


    /**
     * Read a quoted string-valued token from the response.
     * Any other token type other than NIL is an error.
     *
     * @return The string value of the source token.
     * @exception ResponseFormatException
     */
    protected String readQuotedStringOrNil() throws MessagingException {
        // we need to recognize the NIL token.
        Token token = next(true);
        int type = token.getType();

        if (type != Token.QUOTEDSTRING && type != Token.NIL) {
            throw new ResponseFormatException("String token or NIL expected in response");
        }
        // this returns null if the token is the NIL token.
        return token.getValue();
    }


    /**
     * Read a date from a response string.  This is expected to be in
     * Internet Date format, but there's a lot of variation implemented
     * out there.  If we're unable to format this correctly, we'll
     * just return null.
     *
     * @return A Date object created from the source date.
     */
    public Date readDate() throws MessagingException {
        String value = readString();

        try {
            return dateParser.parse(value);
        } catch (Exception e) {
            // we're just skipping over this, so return null
            return null;
        }
    }


    /**
     * Read a date from a response string.  This is expected to be in
     * Internet Date format, but there's a lot of variation implemented
     * out there.  If we're unable to format this correctly, we'll
     * just return null.
     *
     * @return A Date object created from the source date.
     */
    public Date readDateOrNil() throws MessagingException {
        String value = readStringOrNil();
        // this might be optional
        if (value == null) {
            return null;
        }

        try {
            return dateParser.parse(value);
        } catch (Exception e) {
            // we're just skipping over this, so return null
            return null;
        }
    }

    /**
     * Read an internet address from a Fetch response.  The
     * addresses are returned as a set of string tokens in the
     * order "personal list mailbox host".  Any of these tokens
     * can be NIL.
     *
     * The address may also be the start of a group list, which
     * is indicated by the host being NIL.  If we have found the
     * start of a group, then we need to parse multiple elements
     * until we find the group end marker (indicated by both the
     * mailbox and the host being NIL), and create a group
     * InternetAddress instance from this.
     *
     * @return An InternetAddress instance parsed from the
     *         element.
     * @exception ResponseFormatException
     */
    public InternetAddress readAddress() throws MessagingException {
        // we recurse, expecting a null response back for sublists.
        if (peek().getType() != '(') {
            return null;
        }

        // must start with a paren
        checkLeftParen();

        // personal information
        String personal = readStringOrNil();
        // the domain routine information.
        String routing = readStringOrNil();
        // the target mailbox
        String mailbox = readStringOrNil();
        // and finally the host
        String host = readStringOrNil();
        // and validate the closing paren
        checkRightParen();

        // if this is a real address, we need to compose
        if (host != null) {
            StringBuffer address = new StringBuffer();
            if (routing != null) {
                address.append(routing);
                address.append(':');
            }
            address.append(mailbox);
            address.append('@');
            address.append(host);

            try {
                return new InternetAddress(address.toString(), personal);
            } catch (UnsupportedEncodingException e) {
                throw new ResponseFormatException("Invalid Internet address format");
            }
        }
        else {
            // we're going to recurse on this.  If the mailbox is null (the group name), this is the group item
            // terminator.
            if (mailbox == null) {
                return null;
            }

            StringBuffer groupAddress = new StringBuffer();

            groupAddress.append(mailbox);
            groupAddress.append(':');
            int count = 0;

            while (true) {
                // now recurse until we hit the end of the list
                InternetAddress member = readAddress();
                if (member == null) {
                    groupAddress.append(';');

                    try {
                        return new InternetAddress(groupAddress.toString(), personal);
                    } catch (UnsupportedEncodingException e) {
                        throw new ResponseFormatException("Invalid Internet address format");
                    }
                }
                else {
                    if (count != 0) {
                        groupAddress.append(',');
                    }
                    groupAddress.append(member.toString());
                    count++;
                }
            }
        }
    }


    /**
     * Parse out a list of addresses.  This list of addresses is
     * surrounded by parentheses, and each address is also
     * parenthized (SP?).
     *
     * @return An array of the parsed addresses.
     * @exception ResponseFormatException
     */
    public InternetAddress[] readAddressList() throws MessagingException {
        // must start with a paren, but can be NIL also.
        Token token = next(true);
        int type = token.getType();

        // either of these results in a null address.  The caller determines based on
        // context whether this was optional or not.
        if (type == Token.NIL) {
            return null;
        }
        // non-nil address and no paren.  This is a syntax error.
        else if (type != '(') {
            throw new ResponseFormatException("Missing '(' in response");
        }

        List addresses = new ArrayList();

        // we have a list, now parse it.
        while (notListEnd()) {
            // go read the next address.  If we had an address, add to the list.
            // an address ITEM cannot be NIL inside the parens.
            InternetAddress address = readAddress();
            addresses.add(address);
        }
        // we need to skip over the peeked token.
        checkRightParen();
        return (InternetAddress[])addresses.toArray(new InternetAddress[addresses.size()]);
    }


    /**
     * Check to see if we're at the end of a parenthized list
     * without advancing the parsing pointer.  If we are at the
     * end, then this will step over the closing paren.
     *
     * @return True if the next token is a closing list paren, false otherwise.
     * @exception ResponseFormatException
     */
    public boolean checkListEnd() throws MessagingException {
        Token token = peek(true);
        if (token.getType() == ')') {
            // step over this token.
            next();
            return true;
        }
        return false;
    }


    /**
     * Reads a string item which can be encoded either as a single
     * string-valued token or a parenthized list of string tokens.
     *
     * @return A List containing all of the strings.
     * @exception ResponseFormatException
     */
    public List readStringList() throws MessagingException {
        Token token = peek(true);

        if (token.getType() == '(') {
            List list = new ArrayList();

            next();

            while (notListEnd()) {
                String value = readString();
                // this can be NIL, technically
                if (value != null) {
                    list.add(value);
                }
            }
            // step over the closing paren
            next();

            return list;
        }
        else if (token != NIL) {
            List list = new ArrayList();

            // just a single string value.
            String value = readString();
            // this can be NIL, technically
            if (value != null) {
                list.add(value);
            }

            return list;
        } else {
            next();
        }
        return null;
    }


    /**
     * Reads all remaining tokens and returns them as a list of strings.
     * NIL values are not supported.
     *
     * @return A List containing all of the strings.
     * @exception ResponseFormatException
     */
    public List readStrings() throws MessagingException {
        List list = new ArrayList();

        while (hasMore()) {
            String value = readString();
            list.add(value);
        }
        return list;
    }


    /**
     * Skip over an extension item.  This may be either a string
     * token or a parenthized item (with potential nesting).
     *
     * At the point where this is called, we're looking for a closing
     * ')', but we know it is not that.  An EOF is an error, however,
     */
    public void skipExtensionItem() throws MessagingException {
        Token token = next();
        int type = token.getType();

        // list form?  Scan to find the correct list closure.
        if (type == '(') {
            skipNestedValue();
        }
        // found an EOF?  Big problem
        else if (type == Token.EOF) {
            throw new ResponseFormatException("Missing ')'");
        }
    }

    /**
     * Skip over a parenthized value that we're not interested in.
     * These lists may contain nested sublists, so we need to
     * handle the nesting properly.
     */
    public void skipNestedValue() throws MessagingException {
        Token token = next();

        while (true) {
            int type = token.getType();
            // list terminator?
            if (type == ')') {
                return;
            }
            // unexpected end of the tokens.
            else if (type == Token.EOF) {
                throw new ResponseFormatException("Missing ')'");
            }
            // encountered a nested list?
            else if (type == '(') {
                // recurse and finish this list.
                skipNestedValue();
            }
            // we're just skipping the token.
            token = next();
        }
    }

    /**
     * Get the next token and verify that it's of the expected type
     * for the context.
     *
     * @param type   The type of token we're expecting.
     */
    public void checkToken(int type) throws MessagingException {
        Token token = next();
        if (token.getType() != type) {
            throw new ResponseFormatException("Unexpected token: " + token.getValue());
        }
    }


    /**
     * Read the next token as binary data.  The next token can be a literal, a quoted string, or
     * the token NIL (which returns a null result).  Any other token throws a ResponseFormatException.
     *
     * @return A byte array representing the rest of the response data.
     */
    public byte[] readByteArray() throws MessagingException {
        return readData(true);
    }


    /**
     * Determine what type of token encoding needs to be
     * used for a string value.
     *
     * @param value  The string to test.
     *
     * @return Either Token.ATOM, Token.QUOTEDSTRING, or
     *         Token.LITERAL, depending on the characters contained
     *         in the value.
     */
    static public int getEncoding(byte[] value) {

        // a null string always needs to be represented as a quoted literal.
        if (value.length == 0) {
            return Token.QUOTEDSTRING;
        }

        for (int i = 0; i < value.length; i++) {
            int ch = value[i];
            // make sure the sign extension is eliminated
            ch = ch & 0xff;
            // check first for any characters that would
            // disqualify a quoted string
            // NULL
            if (ch == 0x00) {
                return Token.LITERAL;
            }
            // non-7bit ASCII
            if (ch > 0x7F) {
                return Token.LITERAL;
            }
            // carriage return
            if (ch == '\r') {
                return Token.LITERAL;
            }
            // linefeed
            if (ch == '\n') {
                return Token.LITERAL;
            }
            // now check for ATOM disqualifiers
            if (atomDelimiters.indexOf(ch) != -1) {
                return Token.QUOTEDSTRING;
            }
            // CTL character.  We've already eliminated the high characters
            if (ch < 0x20) {
                return Token.QUOTEDSTRING;
            }
        }
        // this can be an ATOM token
        return Token.ATOM;
    }


    /**
     * Read a ContentType or ContentDisposition parameter
     * list from an IMAP command response.
     *
     * @return A ParameterList instance containing the parameters.
     * @exception MessagingException
     */
    public ParameterList readParameterList() throws MessagingException {
        ParameterList params = new ParameterList();

        // read the tokens, taking NIL into account.
        Token token = next(true, false);

        // just return an empty list if this is NIL
        if (token.isType(token.NIL)) {
            return params;
        }

        // these are pairs of strings for each parameter value
        while (notListEnd()) {
            String name = readString();
            String value = readString();
            params.set(name, value);
        }
        // we need to consume the list terminator
        checkRightParen();
        return params;
    }


    /**
     * Test if we have more data in the response buffer.
     *
     * @return true if there are more tokens to process.  false if
     *         we've reached the end of the stream.
     */
    public boolean hasMore() throws MessagingException {
        // we need to eat any white space that might be in the stream.
        eatWhiteSpace();
        return pos < response.length;
    }


    /**
     * Tests if we've reached the end of a parenthetical
     * list in our parsing stream.
     *
     * @return true if the next token will be a ')'.  false if the
     *         next token is anything else.
     * @exception MessagingException
     */
    public boolean notListEnd() throws MessagingException {
        return peek().getType() != ')';
    }

    /**
     * Read a list of Flag values from an IMAP response,
     * returning a Flags instance containing the appropriate
     * pieces.
     *
     * @return A Flags instance with the flag values.
     * @exception MessagingException
     */
    public Flags readFlagList() throws MessagingException {
        Flags flags = new Flags();

        // this should be a list here
        checkLeftParen();

        // run through the flag list
        while (notListEnd()) {
            // the flags are a bit of a pain.  The flag names include "\" in the name, which
            // is not a character allowed in an atom.  This requires a bit of customized parsing
            // to handle this.
            Token token = next();
            // flags can be specified as just atom tokens, so allow this as a user flag.
            if (token.isType(token.ATOM)) {
                // append the atom as a raw name
                flags.add(token.getValue());
            }
            // all of the system flags start with a '\' followed by
            // an atom.  They also can be extension flags.  IMAP has a special
            // case of "\*" that we need to check for.
            else if (token.isType('\\')) {
                token = next();
                // the next token is the real bit we need to process.
                if (token.isType('*')) {
                    // this indicates USER flags are allowed.
                    flags.add(Flags.Flag.USER);
                }
                // if this is an atom name, handle as a system flag
                else if (token.isType(Token.ATOM)) {
                    String name = token.getValue();
                    if (name.equalsIgnoreCase("Seen")) {
                        flags.add(Flags.Flag.SEEN);
                    }
                    else if (name.equalsIgnoreCase("RECENT")) {
                        flags.add(Flags.Flag.RECENT);
                    }
                    else if (name.equalsIgnoreCase("DELETED")) {
                        flags.add(Flags.Flag.DELETED);
                    }
                    else if (name.equalsIgnoreCase("ANSWERED")) {
                        flags.add(Flags.Flag.ANSWERED);
                    }
                    else if (name.equalsIgnoreCase("DRAFT")) {
                        flags.add(Flags.Flag.DRAFT);
                    }
                    else if (name.equalsIgnoreCase("FLAGGED")) {
                        flags.add(Flags.Flag.FLAGGED);
                    }
                    else {
                        // this is a server defined flag....just add the name with the
                        // flag thingy prepended.
                        flags.add("\\" + name);
                    }
                }
                else {
                    throw new MessagingException("Invalid Flag: " + token.getValue());
                }
            }
            else {
                throw new MessagingException("Invalid Flag: " + token.getValue());
            }
        }

        // step over this for good practice.
        checkRightParen();

        return flags;
    }


    /**
     * Read a list of Flag values from an IMAP response,
     * returning a Flags instance containing the appropriate
     * pieces.
     *
     * @return A Flags instance with the flag values.
     * @exception MessagingException
     */
    public List readSystemNameList() throws MessagingException {
        List flags = new ArrayList();

        // this should be a list here
        checkLeftParen();

        // run through the flag list
        while (notListEnd()) {
            // the flags are a bit of a pain.  The flag names include "\" in the name, which
            // is not a character allowed in an atom.  This requires a bit of customized parsing
            // to handle this.
            Token token = next();
            // all of the system flags start with a '\' followed by
            // an atom.  They also can be extension flags.  IMAP has a special
            // case of "\*" that we need to check for.
            if (token.isType('\\')) {
                token = next();
                // if this is an atom name, handle as a system flag
                if (token.isType(Token.ATOM)) {
                    // add the token value to the list WITH the
                    // flag indicator included.  The attributes method returns
                    // these flag indicators, so we need to include it.
                    flags.add("\\" + token.getValue());
                }
                else {
                    throw new MessagingException("Invalid Flag: " + token.getValue());
                }
            }
            else {
                throw new MessagingException("Invalid Flag: " + token.getValue());
            }
        }

        // step over this for good practice.
        checkRightParen();

        return flags;
    }
}

