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
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Quota;
import jakarta.mail.UIDFolder;

import jakarta.mail.search.AddressTerm;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.BodyTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.DateTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.FromTerm;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.HeaderTerm;
import jakarta.mail.search.MessageIDTerm;
import jakarta.mail.search.MessageNumberTerm;
import jakarta.mail.search.NotTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.RecipientTerm;
import jakarta.mail.search.RecipientStringTerm;
import jakarta.mail.search.SearchException;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SentDateTerm;
import jakarta.mail.search.SizeTerm;
import jakarta.mail.search.StringTerm;
import jakarta.mail.search.SubjectTerm;

import org.apache.geronimo.mail.store.imap.ACL;
import org.apache.geronimo.mail.store.imap.IMAPFolder;
import org.apache.geronimo.mail.store.imap.connection.IMAPResponseTokenizer.Token;

import org.apache.geronimo.mail.util.CommandFailedException;


/**
 * Utility class for building up what might be complex arguments
 * to a command.  This includes the ability to directly write out
 * binary arrays of data and have them constructed as IMAP
 * literals.
 */
public class IMAPCommand {

    // digits table for encoding IMAP modified Base64.  Note that this differs
    // from "normal" base 64 by using ',' instead of '/' for the last digit.
    public static final char[] encodingTable = {
        'A', 'B', 'C', 'D', 'E', 'F', 'G',
        'H', 'I', 'J', 'K', 'L', 'M', 'N',
        'O', 'P', 'Q', 'R', 'S', 'T', 'U',
        'V', 'W', 'X', 'Y', 'Z',
        'a', 'b', 'c', 'd', 'e', 'f', 'g',
        'h', 'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't', 'u',
        'v', 'w', 'x', 'y', 'z',
        '0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9',
        '+', ','
    };

    protected boolean needWhiteSpace = false;

    // our utility writer stream
    protected DataOutputStream out;
    // the real output target
    protected ByteArrayOutputStream sink;
    // our command segment set.  If the command contains literals, then the literal
    // data must be sent after receiving an continue response back from the server.
    protected List segments = null;
    // the append tag for the response
    protected String tag;

    // our counter used to generate command tags.
    static protected int tagCounter = 0;

    /**
     * Create an empty command.
     */
    public IMAPCommand() {
        try {
            sink = new ByteArrayOutputStream();
            out = new DataOutputStream(sink);

            // write the tag data at the beginning of the command.
            out.writeBytes(getTag());
            // need a blank separator
            out.write(' ');
        } catch (IOException e ) {
        }
    }

    /**
     * Create a command with an initial command string.
     *
     * @param command The command string used to start this command.
     */
    public IMAPCommand(String command) {
        this();
        append(command);
    }

    public String getTag() {
        if (tag == null) {
            // the tag needs to be non-numeric, so tack a convenient alpha character on the front.
            tag = "a" + tagCounter++;
        }
        return tag;
    }


    /**
     * Save the current segment of the command we've accumulated.  This
     * generally occurs because we have a literal element in the command
     * that's going to require a continuation response from the server before
     * we can send it.
     */
    private void saveCurrentSegment()
    {
        try {
            out.flush();     // make sure everything is written
                             // get the data so far and reset the sink
            byte[] segment = sink.toByteArray();
            sink.reset();
            // most commands don't have segments, so don't create the list until we do.
            if (segments == null) {
                segments = new ArrayList();
            }
            // ok, we need to issue this command as a conversation.
            segments.add(segment);
        } catch (IOException e) {
        }
    }


    /**
     * Write all of the command data to the stream.  This includes the
     * leading tag data.
     *
     * @param outStream
     * @param connection
     *
     * @exception IOException
     * @exception MessagingException
     */
    public void writeTo(OutputStream outStream, IMAPConnection connection) throws IOException, MessagingException
    {

        // just a simple, single string-encoded command?
        if (segments == null) {
            // make sure the output stream is flushed
            out.flush();
            // just copy the command data to the output stream
            sink.writeTo(outStream);
            // we need to end the command with a CRLF sequence.
            outStream.write('\r');
            outStream.write('\n');
        }
        // multiple-segment mode, which means we need to deal with continuation responses at
        // each of the literal boundaries.
        else {
            // at this point, we have a list of command pieces that must be written out, then a
            // continuation response checked for after each write.  Once each of these pieces is
            // written out, we still have command stuff pending in the out stream, which we'll tack
            // on to the end.
            for (int i = 0; i < segments.size(); i++) {
                outStream.write((byte [])segments.get(i));
                // now wait for a response from the connection.  We should be getting a
                // continuation response back (and might have also received some asynchronous
                // replies, which we'll leave in the queue for now.  If we get some status back
                // other than than a continue, we've got an error in our command somewhere.
                IMAPTaggedResponse response = connection.receiveResponse();
                if (!response.isContinuation()) {
                    throw new CommandFailedException("Error response received on a IMAP continued command:  " + response);
                }
            }
            out.flush();
            // all leading segments written with the appropriate continuation received in reply.
            // just copy the command data to the output stream
            sink.writeTo(outStream);
            // we need to end the command with a CRLF sequence.
            outStream.write('\r');
            outStream.write('\n');
        }
    }


    /**
     * Directly append a value to the buffer without attempting
     * to insert whitespace or figure out any format encodings.
     *
     * @param value  The value to append.
     */
    public void append(String value) {
        try {
            // add the bytes direcly
            out.writeBytes(value);
            // assume we're needing whitespace after this (pretty much unknown).
            needWhiteSpace = true;
        } catch (IOException e) {
        }
    }


    /**
     * Append a string value to a command buffer.  This sorts out
     * what form the string needs to be appended in (LITERAL, QUOTEDSTRING,
     * or ATOM).
     *
     * @param target The target buffer for appending the string.
     * @param value  The value to append.
     */
    public void appendString(String value) {
        try {
            // work off the byte values
            appendString(value.getBytes("ISO8859-1"));
        } catch (UnsupportedEncodingException e) {
        }
    }


    /**
     * Append a string value to a command buffer.  This always appends as
     * a QUOTEDSTRING
     *
     * @param value  The value to append.
     */
    public void appendQuotedString(String value) {
        try {
            // work off the byte values
            appendQuotedString(value.getBytes("ISO8859-1"));
        } catch (UnsupportedEncodingException e) {
        }
    }


    /**
     * Append a string value to a command buffer, with encoding.  This sorts out
     * what form the string needs to be appended in (LITERAL, QUOTEDSTRING,
     * or ATOM).
     *
     * @param target The target buffer for appending the string.
     * @param value  The value to append.
     */
    public void appendEncodedString(String value) {
        // encode first.
        value = encode(value);
        try {
            // work off the byte values
            appendString(value.getBytes("ISO8859-1"));
        } catch (UnsupportedEncodingException e) {
        }
    }


    /**
     * Encode a string using the modified UTF-7 encoding.
     *
     * @param original The original string.
     *
     * @return The original string encoded with modified UTF-7 encoding.
     */
    public String encode(String original) {

        // buffer for encoding sections of data
        byte[] buffer = new byte[4];
        int bufferCount = 0;

        StringBuffer result = new StringBuffer();

        // state flag for the type of section we're in.
        boolean encoding = false;

        for (int i = 0; i < original.length(); i++) {
            char ch = original.charAt(i);

            // processing an encoded section?
            if (encoding) {
                // is this a printable character?
                if (ch > 31 && ch < 127) {
                    // encode anything in the buffer
                    encode(buffer, bufferCount, result);
                    // add the section terminator char
                    result.append('-');
                    encoding = false;
                    // we now fall through to the printable character section.
                }
                // still an unprintable
                else {
                    // add this char to the working buffer?
                    buffer[++bufferCount] = (byte)(ch >> 8);
                    buffer[++bufferCount] = (byte)(ch & 0xff);
                    // if we have enough to encode something, do it now.
                    if (bufferCount >= 3) {
                        bufferCount = encode(buffer, bufferCount, result);
                    }
                    // go back to the top of the loop.
                    continue;
                }
            }
            // is this the special printable?
            if (ch == '&') {
                // this is the special null escape sequence
                result.append('&');
                result.append('-');
            }
            // is this a printable character?
            else if (ch > 31 && ch < 127) {
                // just add to the result
                result.append(ch);
            }
            else {
                // write the escape character
                result.append('&');

                // non-printable ASCII character, we need to switch modes
                // both bytes of this character need to be encoded.  Each
                // encoded digit will basically be a "character-and-a-half".
                buffer[0] = (byte)(ch >> 8);
                buffer[1] = (byte)(ch & 0xff);
                bufferCount = 2;
                encoding = true;
            }
        }
        // were we in a non-printable section at the end?
        if (encoding) {
            // take care of any remaining characters
            encode(buffer, bufferCount, result);
            // add the section terminator char
            result.append('-');
        }
        // convert the encoded string.
        return result.toString();
    }


    /**
     * Encode a single buffer of characters.  This buffer will have
     * between 0 and 4 bytes to encode.
     *
     * @param buffer The buffer to encode.
     * @param count  The number of characters in the buffer.
     * @param result The accumulator for appending the result.
     *
     * @return The remaining number of bytes remaining in the buffer (return 0
     *         unless the count was 4 at the beginning).
     */
    protected static int encode(byte[] buffer, int count, StringBuffer result) {
        byte b1 = 0;
        byte b2 = 0;
        byte b3 = 0;

        // different processing based on how much we have in the buffer
        switch (count) {
            // ended at a boundary.  This is cool, not much to do.
            case 0:
                // no residual in the buffer
                return 0;

            // just a single left over byte from the last encoding op.
            case 1:
                b1 = buffer[0];
                result.append(encodingTable[(b1 >>> 2) & 0x3f]);
                result.append(encodingTable[(b1 << 4) & 0x30]);
                return 0;

            // one complete char to encode
            case 2:
                b1 = buffer[0];
                b2 = buffer[1];
                result.append(encodingTable[(b1 >>> 2) & 0x3f]);
                result.append(encodingTable[((b1 << 4) & 0x30) + ((b2 >>>4) & 0x0f)]);
                result.append(encodingTable[((b2 << 2) & (0x3c))]);
                return 0;

            // at least a full triplet of bytes to encode
            case 3:
            case 4:
                b1 = buffer[0];
                b2 = buffer[1];
                b3 = buffer[2];
                result.append(encodingTable[(b1 >>> 2) & 0x3f]);
                result.append(encodingTable[((b1 << 4) & 0x30) + ((b2 >>>4) & 0x0f)]);
                result.append(encodingTable[((b2 << 2) & 0x3c) + ((b3 >>> 6) & 0x03)]);
                result.append(encodingTable[b3 & 0x3f]);

                // if we have more than the triplet, we need to move the extra one into the first
                // position and return the residual indicator
                if (count == 4) {
                    buffer[0] = buffer[4];
                    return 1;
                }
                return 0;
        }
        return 0;
    }


    /**
     * Append a string value to a command buffer.  This sorts out
     * what form the string needs to be appended in (LITERAL, QUOTEDSTRING,
     * or ATOM).
     *
     * @param target The target buffer for appending the string.
     * @param value  The value to append.
     */
    public void appendString(String value, String charset) throws MessagingException {
        if (charset == null) {
            try {
                // work off the byte values
                appendString(value.getBytes("ISO8859-1"));
            } catch (UnsupportedEncodingException e) {
            }
        }
        else {
            try {
                // use the charset to extract the bytes
                appendString(value.getBytes(charset));
                throw new MessagingException("Invalid text encoding");
            } catch (UnsupportedEncodingException e) {
            }
        }
    }


    /**
     * Append a value in a byte array to a command buffer.  This sorts out
     * what form the string needs to be appended in (LITERAL, QUOTEDSTRING,
     * or ATOM).
     *
     * @param target The target buffer for appending the string.
     * @param value  The value to append.
     */
    public void appendString(byte[] value) {
        // sort out how we need to append this
        switch (IMAPResponseTokenizer.getEncoding(value)) {
            case Token.LITERAL:
                appendLiteral(value);
                break;
            case Token.QUOTEDSTRING:
                appendQuotedString(value);
                break;
            case Token.ATOM:
                appendAtom(value);
                break;
        }
    }


    /**
     * Append an integer value to the command, converting
     * the integer into string form.
     *
     * @param value  The value to append.
     */
    public void appendInteger(int value) {
        appendAtom(Integer.toString(value));
    }


    /**
     * Append a long value to the command, converting
     * the integer into string form.
     *
     * @param value  The value to append.
     */
    public void appendLong(long value) {
        appendAtom(Long.toString(value));
    }


    /**
     * Append an atom value to the command.  Atoms are directly
     * appended without using literal encodings.
     *
     * @param value  The value to append.
     */
    public void appendAtom(String value) {
        try {
            appendAtom(value.getBytes("ISO8859-1"));
        } catch (UnsupportedEncodingException e) {
        }
    }



    /**
     * Append an atom to the command buffer.  Atoms are directly
     * appended without using literal encodings.  White space is
     * accounted for with the append operation.
     *
     * @param value  The value to append.
     */
    public void appendAtom(byte[] value) {
        try {
            // give a token separator
            conditionalWhitespace();
            // ATOMs are easy
            out.write(value);
        } catch (IOException e) {
        }
    }


    /**
     * Append an IMAP literal values to the command.
     * literals are written using a header with the length
     * specified, followed by a CRLF sequence, followed
     * by the literal data.
     *
     * @param value  The literal data to write.
     */
    public void appendLiteral(byte[] value) {
        try {
            appendLiteralHeader(value.length);
            out.write(value);
        } catch (IOException e) {
        }
    }

    /**
     * Add a literal header to the buffer.  The literal
     * header is the literal length enclosed in a
     * "{n}" pair, followed by a CRLF sequence.
     *
     * @param size   The size of the literal value.
     */
    protected void appendLiteralHeader(int size) {
        try {
            conditionalWhitespace();
            out.writeByte('{');
            out.writeBytes(Integer.toString(size));
            out.writeBytes("}\r\n");
            // the IMAP client is required to send literal data to the server by
            // writing the command up to the header, then waiting for a continuation
            // response to send the rest.
            saveCurrentSegment();
        } catch (IOException e) {
        }
    }


    /**
     * Append literal data to the command where the
     * literal sourcd is a ByteArrayOutputStream.
     *
     * @param value  The source of the literal data.
     */
    public void appendLiteral(ByteArrayOutputStream value) {
        try {
            appendLiteralHeader(value.size());
            // have this output stream write directly into our stream
            value.writeTo(out);
        } catch (IOException e) {
        }
    }

    /**
     * Write out a string of literal data, taking into
     * account the need to escape both '"' and '\'
     * characters.
     *
     * @param value  The bytes of the string to write.
     */
    public void appendQuotedString(byte[] value) {
        try {
            conditionalWhitespace();
            out.writeByte('"');

            // look for chars requiring escaping
            for (int i = 0; i < value.length; i++) {
                byte ch = value[i];

                if (ch == '"' || ch == '\\') {
                    out.writeByte('\\');
                }
                out.writeByte(ch);
            }

            out.writeByte('"');
        } catch (IOException e) {
        }
    }

    /**
     * Mark the start of a list value being written to
     * the command.  A list is a sequences of different
     * tokens enclosed in "(" ")" pairs.  Lists can
     * be nested.
     */
    public void startList() {
        try {
            conditionalWhitespace();
            out.writeByte('(');
            needWhiteSpace = false;
        } catch (IOException e) {
        }
    }

    /**
     * Write out the end of the list.
     */
    public void endList() {
        try {
            out.writeByte(')');
            needWhiteSpace = true;
        } catch (IOException e) {
        }
    }


    /**
     * Add a whitespace character to the command if the
     * previous token was a type that required a
     * white space character to mark the boundary.
     */
    protected void conditionalWhitespace() {
        try {
            if (needWhiteSpace) {
                out.writeByte(' ');
            }
            // all callers of this are writing a token that will need white space following, so turn this on
            // every time we're called.
            needWhiteSpace = true;
        } catch (IOException e) {
        }
    }


    /**
     * Append a body section specification to a command string.  Body
     * section specifications are of the form "[section]<start.count>".
     *
     * @param section  The section numeric identifier.
     * @param partName The name of the body section we want (e.g. "TEST", "HEADERS").
     */
    public void appendBodySection(String section, String partName) {
        try {
            // we sometimes get called from the top level
            if (section == null) {
                appendBodySection(partName);
                return;
            }

            out.writeByte('[');
            out.writeBytes(section);
            if (partName != null) {
                out.writeByte('.');
                out.writeBytes(partName);
            }
            out.writeByte(']');
            needWhiteSpace = true;
        } catch (IOException e) {
        }
    }


    /**
     * Append a body section specification to a command string.  Body
     * section specifications are of the form "[section]".
     *
     * @param partName The partname we require.
     */
    public void appendBodySection(String partName) {
        try {
            out.writeByte('[');
            out.writeBytes(partName);
            out.writeByte(']');
            needWhiteSpace = true;
        } catch (IOException e) {
        }
    }


    /**
     * Append a set of flags to a command buffer.
     *
     * @param flags  The flag set to append.
     */
    public void appendFlags(Flags flags) {
        startList();

        Flags.Flag[] systemFlags = flags.getSystemFlags();

        // process each of the system flag names
        for (int i = 0; i < systemFlags.length; i++) {
            Flags.Flag flag = systemFlags[i];

            if (flag == Flags.Flag.ANSWERED) {
                appendAtom("\\Answered");
            }
            else if (flag == Flags.Flag.DELETED) {
                appendAtom("\\Deleted");
            }
            else if (flag == Flags.Flag.DRAFT) {
                appendAtom("\\Draft");
            }
            else if (flag == Flags.Flag.FLAGGED) {
                appendAtom("\\Flagged");
            }
            else if (flag == Flags.Flag.RECENT) {
                appendAtom("\\Recent");
            }
            else if (flag == Flags.Flag.SEEN) {
                appendAtom("\\Seen");
            }
        }

        // now process the user flags, which just get appended as is.
        String[] userFlags = flags.getUserFlags();

        for (int i = 0; i < userFlags.length; i++) {
            appendAtom(userFlags[i]);
        }

        // close the list off
        endList();
    }


    /**
     * Format a date into the form required for IMAP commands.
     *
     * @param d      The source Date.
     */
    public void appendDate(Date d) {
        // get a formatter to create IMAP dates.  Use the US locale, as the dates are not localized.
        IMAPDateFormat formatter = new IMAPDateFormat();
        // date_time strings need to be done as quoted strings because they contain blanks.
        appendString(formatter.format(d));
    }


    /**
     * Format a date into the form required for IMAP search commands.
     *
     * @param d      The source Date.
     */
    public void appendSearchDate(Date d) {
        // get a formatter to create IMAP dates.  Use the US locale, as the dates are not localized.
        IMAPSearchDateFormat formatter = new IMAPSearchDateFormat();
        // date_time strings need to be done as quoted strings because they contain blanks.
        appendString(formatter.format(d));
    }


    /**
     * append an IMAP search sequence from a SearchTerm.  SearchTerms
     * terms can be complex sets of terms in a tree form, so this
     * may involve some recursion to completely translate.
     *
     * @param term    The search term we're processing.
     * @param charset The charset we need to use when generating the sequence.
     *
     * @exception MessagingException
     */
    public void appendSearchTerm(SearchTerm term, String charset) throws MessagingException {
        // we need to do this manually, by inspecting the term object against the various SearchTerm types
        // defined by the mail spec.

        // Flag searches are used internally by other operations, so this is a good one to check first.
        if (term instanceof FlagTerm) {
            appendFlag((FlagTerm)term, charset);
        }
        // after that, I'm not sure there's any optimal order to these.  Let's start with the conditional
        // modifiers (AND, OR, NOT), then just hit each of the header types
        else if (term instanceof AndTerm) {
            appendAnd((AndTerm)term, charset);
        }
        else if (term instanceof OrTerm) {
            appendOr((OrTerm)term, charset);
        }
        else if (term instanceof NotTerm) {
            appendNot((NotTerm)term, charset);
        }
        // multiple forms of From: search
        else if (term instanceof FromTerm) {
            appendFrom((FromTerm)term, charset);
        }
        else if (term instanceof FromStringTerm) {
            appendFrom((FromStringTerm)term, charset);
        }
        else if (term instanceof HeaderTerm) {
            appendHeader((HeaderTerm)term, charset);
        }
        else if (term instanceof RecipientTerm) {
            appendRecipient((RecipientTerm)term, charset);
        }
        else if (term instanceof RecipientStringTerm) {
            appendRecipient((RecipientStringTerm)term, charset);
        }
        else if (term instanceof SubjectTerm) {
            appendSubject((SubjectTerm)term, charset);
        }
        else if (term instanceof BodyTerm) {
            appendBody((BodyTerm)term, charset);
        }
        else if (term instanceof SizeTerm) {
            appendSize((SizeTerm)term, charset);
        }
        else if (term instanceof SentDateTerm) {
            appendSentDate((SentDateTerm)term, charset);
        }
        else if (term instanceof ReceivedDateTerm) {
            appendReceivedDate((ReceivedDateTerm)term, charset);
        }
        else if (term instanceof MessageIDTerm) {
            appendMessageID((MessageIDTerm)term, charset);
        }
        else {
            // don't know what this is
            throw new SearchException("Unsupported search type");
        }
    }

    /**
     * append IMAP search term information from a FlagTerm item.
     *
     * @param term    The source FlagTerm
     * @param charset target charset for the search information (can be null).
     * @param out     The target command buffer.
     */
    protected void appendFlag(FlagTerm term, String charset) {
        // decide which one we need to test for
        boolean set = term.getTestSet();

        Flags flags = term.getFlags();
        Flags.Flag[] systemFlags = flags.getSystemFlags();

        String[] userFlags = flags.getUserFlags();

        // empty search term?  not sure if this is an error.  The default search implementation would
        // not consider this an error, so we'll just ignore this.
        if (systemFlags.length == 0 && userFlags.length == 0) {
            return;
        }

        if (set) {
            for (int i = 0; i < systemFlags.length; i++) {
                Flags.Flag flag = systemFlags[i];

                if (flag == Flags.Flag.ANSWERED) {
                    appendAtom("ANSWERED");
                }
                else if (flag == Flags.Flag.DELETED) {
                    appendAtom("DELETED");
                }
                else if (flag == Flags.Flag.DRAFT) {
                    appendAtom("DRAFT");
                }
                else if (flag == Flags.Flag.FLAGGED) {
                    appendAtom("FLAGGED");
                }
                else if (flag == Flags.Flag.RECENT) {
                    appendAtom("RECENT");
                }
                else if (flag == Flags.Flag.SEEN) {
                    appendAtom("SEEN");
                }
            }
        }
        else {
            for (int i = 0; i < systemFlags.length; i++) {
                Flags.Flag flag = systemFlags[i];

                if (flag == Flags.Flag.ANSWERED) {
                    appendAtom("UNANSWERED");
                }
                else if (flag == Flags.Flag.DELETED) {
                    appendAtom("UNDELETED");
                }
                else if (flag == Flags.Flag.DRAFT) {
                    appendAtom("UNDRAFT");
                }
                else if (flag == Flags.Flag.FLAGGED) {
                    appendAtom("UNFLAGGED");
                }
                else if (flag == Flags.Flag.RECENT) {
                    // not UNRECENT?
                    appendAtom("OLD");
                }
                else if (flag == Flags.Flag.SEEN) {
                    appendAtom("UNSEEN");
                }
            }
        }


        // User flags are done as either "KEYWORD name" or "UNKEYWORD name"
        for (int i = 0; i < userFlags.length; i++) {
            appendAtom(set ? "KEYWORD" : "UNKEYWORD");
            appendAtom(userFlags[i]);
        }
    }


    /**
     * append IMAP search term information from an AndTerm item.
     *
     * @param term    The source AndTerm
     * @param charset target charset for the search information (can be null).
     * @param out     The target command buffer.
     */
    protected void appendAnd(AndTerm term, String charset) throws MessagingException {
        // ANDs are pretty easy.  Just append all of the terms directly to the
        // command as is.

        SearchTerm[] terms = term.getTerms();

        for (int i = 0; i < terms.length; i++) {
            appendSearchTerm(terms[i], charset);
        }
    }


    /**
     * append IMAP search term information from an OrTerm item.
     *
     * @param term    The source OrTerm
     * @param charset target charset for the search information (can be null).
     * @param out     The target command buffer.
     */
    protected void appendOr(OrTerm term, String charset) throws MessagingException {
        SearchTerm[] terms = term.getTerms();

        // OrTerms are a bit of a pain to translate to IMAP semantics.  The IMAP OR operation only allows 2
        // search keys, while OrTerms can have n keys (including, it appears, just one!  If we have more than
        // 2, it's easiest to convert this into a tree of OR keys and let things generate that way.  The
        // resulting IMAP operation would be OR (key1) (OR (key2) (key3))

        // silly rabbit...somebody doesn't know how to use OR
        if (terms.length == 1) {
            // just append the singleton in place without the OR operation.
            appendSearchTerm(terms[0], charset);
            return;
        }

        // is this a more complex operation?
        if (terms.length > 2) {
            // have to chain these together (shazbat).
            SearchTerm current = terms[0];

            for (int i = 1; i < terms.length; i++) {
                current = new OrTerm(current, terms[i]);
            }

            // replace the term array with the newly generated top array
            terms = ((OrTerm)current).getTerms();
        }

        // we're going to generate this with parenthetical search keys, even if it is just a simple term.
        appendAtom("OR");
        startList();
        // generated OR argument 1
        appendSearchTerm(terms[0], charset);
        endList();
        startList();
        // generated OR argument 2
        appendSearchTerm(terms[0], charset);
        // and the closing parens
        endList();
    }


    /**
     * append IMAP search term information from a NotTerm item.
     *
     * @param term    The source NotTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendNot(NotTerm term, String charset) throws MessagingException {
        // we're goint to generate this with parenthetical search keys, even if it is just a simple term.
        appendAtom("NOT");
        startList();
        // generated the NOT expression
        appendSearchTerm(term.getTerm(), charset);
        // and the closing parens
        endList();
    }


    /**
     * append IMAP search term information from a FromTerm item.
     *
     * @param term    The source FromTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendFrom(FromTerm term, String charset) throws MessagingException {
        appendAtom("FROM");
        // this may require encoding
        appendString(term.getAddress().toString(), charset);
    }


    /**
     * append IMAP search term information from a FromStringTerm item.
     *
     * @param term    The source FromStringTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendFrom(FromStringTerm term, String charset) throws MessagingException {
        appendAtom("FROM");
        // this may require encoding
        appendString(term.getPattern(), charset);
    }


    /**
     * append IMAP search term information from a RecipientTerm item.
     *
     * @param term    The source RecipientTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendRecipient(RecipientTerm term, String charset) throws MessagingException {
        appendAtom(recipientType(term.getRecipientType()));
        // this may require encoding
        appendString(term.getAddress().toString(), charset);
    }


    /**
     * append IMAP search term information from a RecipientStringTerm item.
     *
     * @param term    The source RecipientStringTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendRecipient(RecipientStringTerm term, String charset) throws MessagingException {
        appendAtom(recipientType(term.getRecipientType()));
        // this may require encoding
        appendString(term.getPattern(), charset);
    }


    /**
     * Translate a recipient type into it's string name equivalent.
     *
     * @param type   The source recipient type
     *
     * @return A string name matching the recipient type.
     */
    protected String recipientType(Message.RecipientType type) throws MessagingException {
        if (type == Message.RecipientType.TO) {
            return "TO";
        }
        if (type == Message.RecipientType.CC) {
            return "CC";
        }
        if (type == Message.RecipientType.BCC) {
            return "BCC";
        }

        throw new SearchException("Unsupported RecipientType");
    }


    /**
     * append IMAP search term information from a HeaderTerm item.
     *
     * @param term    The source HeaderTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendHeader(HeaderTerm term, String charset) throws MessagingException {
        appendAtom("HEADER");
        appendString(term.getHeaderName());
        appendString(term.getPattern(), charset);
    }



    /**
     * append IMAP search term information from a SubjectTerm item.
     *
     * @param term    The source SubjectTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendSubject(SubjectTerm term, String charset) throws MessagingException {
        appendAtom("SUBJECT");
        appendString(term.getPattern(), charset);
    }


    /**
     * append IMAP search term information from a BodyTerm item.
     *
     * @param term    The source BodyTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendBody(BodyTerm term, String charset) throws MessagingException {
        appendAtom("BODY");
        appendString(term.getPattern(), charset);
    }


    /**
     * append IMAP search term information from a SizeTerm item.
     *
     * @param term    The source SizeTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendSize(SizeTerm term, String charset) throws MessagingException {

        // these comparisons can be a real pain.  IMAP only supports LARGER and SMALLER.  So comparisons
        // other than GT and LT have to be composed of complex sequences of these.  For example, an EQ
        // comparison becomes NOT LARGER size NOT SMALLER size

        if (term.getComparison() == ComparisonTerm.GT) {
            appendAtom("LARGER");
            appendInteger(term.getNumber());
        }
        else if (term.getComparison() == ComparisonTerm.LT) {
            appendAtom("SMALLER");
            appendInteger(term.getNumber());
        }
        else if (term.getComparison() == ComparisonTerm.EQ) {
            appendAtom("NOT");
            appendAtom("LARGER");
            appendInteger(term.getNumber());

            appendAtom("NOT");
            appendAtom("SMALLER");
            // it's just right <g>
            appendInteger(term.getNumber());
        }
        else if (term.getComparison() == ComparisonTerm.NE) {
            // this needs to be an OR comparison
            appendAtom("OR");
            appendAtom("LARGER");
            appendInteger(term.getNumber());

            appendAtom("SMALLER");
            appendInteger(term.getNumber());
        }
        else if (term.getComparison() == ComparisonTerm.LE) {
            // just the inverse of LARGER
            appendAtom("NOT");
            appendAtom("LARGER");
            appendInteger(term.getNumber());
        }
        else if (term.getComparison() == ComparisonTerm.GE) {
            // and the reverse.
            appendAtom("NOT");
            appendAtom("SMALLER");
            appendInteger(term.getNumber());
        }
    }


    /**
     * append IMAP search term information from a MessageIDTerm item.
     *
     * @param term    The source MessageIDTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendMessageID(MessageIDTerm term, String charset) throws MessagingException {

        // not directly supported by IMAP, but we can compare on the header information.
        appendAtom("HEADER");
        appendString("Message-ID");
        appendString(term.getPattern(), charset);
    }


    /**
     * append IMAP search term information from a SendDateTerm item.
     *
     * @param term    The source SendDateTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendSentDate(SentDateTerm term, String charset) throws MessagingException {
        Date date = term.getDate();

        switch (term.getComparison()) {
            case ComparisonTerm.EQ:
                appendAtom("SENTON");
                appendSearchDate(date);
                break;
            case ComparisonTerm.LT:
                appendAtom("SENTBEFORE");
                appendSearchDate(date);
                break;
            case ComparisonTerm.GT:
                appendAtom("SENTSINCE");
                appendSearchDate(date);
                break;
            case ComparisonTerm.GE:
                appendAtom("OR");
                appendAtom("SENTSINCE");
                appendSearchDate(date);
                appendAtom("SENTON");
                appendSearchDate(date);
                break;
            case ComparisonTerm.LE:
                appendAtom("OR");
                appendAtom("SENTBEFORE");
                appendSearchDate(date);
                appendAtom("SENTON");
                appendSearchDate(date);
                break;
            case ComparisonTerm.NE:
                appendAtom("NOT");
                appendAtom("SENTON");
                appendSearchDate(date);
                break;
            default:
                throw new SearchException("Unsupported date comparison type");
        }
    }


    /**
     * append IMAP search term information from a ReceivedDateTerm item.
     *
     * @param term    The source ReceivedDateTerm
     * @param charset target charset for the search information (can be null).
     */
    protected void appendReceivedDate(ReceivedDateTerm term, String charset) throws MessagingException {
        Date date = term.getDate();

        switch (term.getComparison()) {
            case ComparisonTerm.EQ:
                appendAtom("ON");
                appendSearchDate(date);
                break;
            case ComparisonTerm.LT:
                appendAtom("BEFORE");
                appendSearchDate(date);
                break;
            case ComparisonTerm.GT:
                appendAtom("SINCE");
                appendSearchDate(date);
                break;
            case ComparisonTerm.GE:
                appendAtom("OR");
                appendAtom("SINCE");
                appendSearchDate(date);
                appendAtom("ON");
                appendSearchDate(date);
                break;
            case ComparisonTerm.LE:
                appendAtom("OR");
                appendAtom("BEFORE");
                appendSearchDate(date);
                appendAtom("ON");
                appendSearchDate(date);
                break;
            case ComparisonTerm.NE:
                appendAtom("NOT");
                appendAtom("ON");
                appendSearchDate(date);
                break;
            default:
                throw new SearchException("Unsupported date comparison type");
        }
    }


    /**
     * Run the tree of search terms, checking for problems with
     * the terms that may require specifying a CHARSET modifier
     * on a SEARCH command sent to the server.
     *
     * @param term   The term to check.
     *
     * @return True if there are 7-bit problems, false if the terms contain
     *         only 7-bit ASCII characters.
     */
    static public boolean checkSearchEncoding(SearchTerm term) {
        // StringTerm is the basis of most of the string-valued terms, and are most important ones to check.
        if (term instanceof StringTerm) {
            return checkStringEncoding(((StringTerm)term).getPattern());
        }
        // Address terms are basically string terms also, but we need to check the string value of the
        // addresses, since that's what we're sending along.  This covers a lot of the TO/FROM, etc. searches.
        else if (term instanceof AddressTerm) {
            return checkStringEncoding(((AddressTerm)term).getAddress().toString());
        }
        // the NOT contains a term itself, so recurse on that.  The NOT does not directly have string values
        // to check.
        else if (term instanceof NotTerm) {
            return checkSearchEncoding(((NotTerm)term).getTerm());
        }
        // AND terms and OR terms have lists of subterms that must be checked.
        else if (term instanceof AndTerm) {
            return checkSearchEncoding(((AndTerm)term).getTerms());
        }
        else if (term instanceof OrTerm) {
            return checkSearchEncoding(((OrTerm)term).getTerms());
        }

        // non of the other term types (FlagTerm, SentDateTerm, etc.) pose a problem, so we'll give them
        // a free pass.
        return false;
    }


    /**
     * Run an array of search term items to check each one for ASCII
     * encoding problems.
     *
     * @param terms  The array of terms to check.
     *
     * @return True if any of the search terms contains a 7-bit ASCII problem,
     *         false otherwise.
     */
    static public boolean checkSearchEncoding(SearchTerm[] terms) {
        for (int i = 0; i < terms.length; i++) {
            if (checkSearchEncoding(terms[i])) {
                return true;
            }
        }
        return false;
    }


    /**
     * Check a string to see if this can be processed using just
     * 7-bit ASCII.
     *
     * @param s      The string to check
     *
     * @return true if the string contains characters outside the 7-bit ascii range,
     *         false otherwise.
     */
    static public boolean checkStringEncoding(String s) {
        for (int i = 0; i < s.length(); i++) {
            // any value greater that 0x7f is a problem char.  We're not worried about
            // lower ctl chars (chars < 32) since those are still expressible in 7-bit.
            if (s.charAt(i) > 127) {
                return true;
            }
        }

        return false;
    }


    /**
     * Append a FetchProfile information to an IMAPCommand
     * that's to be issued.
     *
     * @param profile The fetch profile we're using.
     *
     * @exception MessagingException
     */
    public void appendFetchProfile(FetchProfile profile) throws MessagingException {
        // the fetch profile items are a parenthtical list passed on a
        // FETCH command.
        startList();
        if (profile.contains(UIDFolder.FetchProfileItem.UID)) {
            appendAtom("UID");
        }
        if (profile.contains(FetchProfile.Item.ENVELOPE)) {
            // fetching the envelope involves several items
            appendAtom("ENVELOPE");
            appendAtom("INTERNALDATE");
            appendAtom("RFC822.SIZE");
        }
        if (profile.contains(FetchProfile.Item.FLAGS)) {
            appendAtom("FLAGS");
        }
        if (profile.contains(FetchProfile.Item.CONTENT_INFO)) {
            appendAtom("BODYSTRUCTURE");
        }
        if (profile.contains(IMAPFolder.FetchProfileItem.SIZE)) {
            appendAtom("RFC822.SIZE");
        }
        // There are two choices here, that are sort of redundant.
        // if all headers have been requested, there's no point in
        // adding any specifically requested one.
        if (profile.contains(IMAPFolder.FetchProfileItem.HEADERS)) {
            appendAtom("BODY.PEEK[HEADER]");
        }
        else {
            String[] headers = profile.getHeaderNames();
            // have an actual list to retrieve?  need to craft this as a sublist
            // of identified fields.
            if (headers.length > 0) {
                appendAtom("BODY.PEEK[HEADER.FIELDS]");
                startList();
                for (int i = 0; i < headers.length; i++) {
                    appendAtom(headers[i]);
                }
                endList();
            }
        }
        // end the list.
        endList();
    }


    /**
     * Append an ACL value to a command.  The ACL is the writes string name,
     * followed by the rights value.  This version uses no +/- modifier.
     *
     * @param acl    The ACL to append.
     */
    public void appendACL(ACL acl) {
        appendACL(acl, null);
    }

    /**
     * Append an ACL value to a command.  The ACL is the writes string name,
     * followed by the rights value.  A +/- modifier can be added to the
     * // result.
     *
     * @param acl      The ACL to append.
     * @param modifier The modifer string (can be null).
     */
    public void appendACL(ACL acl, String modifier) {
        appendString(acl.getName());
        String rights = acl.getRights().toString();

        if (modifier != null) {
            rights = modifier + rights;
        }
        appendString(rights);
    }


    /**
     * Append a quota specification to an IMAP command.
     *
     * @param quota  The quota value to append.
     */
    public void appendQuota(Quota quota) {
        appendString(quota.quotaRoot);
        startList();
        for (int i = 0; i < quota.resources.length; i++) {
            appendQuotaResource(quota.resources[i]);
        }
        endList();
    }

    /**
     * Append a Quota.Resource element to an IMAP command.  This converts as
     * the resoure name, the usage value and limit value).
     *
     * @param resource The resource element we're appending.
     */
    public void appendQuotaResource(Quota.Resource resource) {
        appendAtom(resource.name);
        // NB:  For command purposes, only the limit is used.
        appendLong(resource.limit);
    }
}

