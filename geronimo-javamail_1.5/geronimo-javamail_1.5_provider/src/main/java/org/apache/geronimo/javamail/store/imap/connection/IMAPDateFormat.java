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

import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Formats ths date as specified by
 * draft-ietf-drums-msg-fmt-08 dated January 26, 2000
 * which supercedes RFC822.
 * <p/>
 * <p/>
 * The format used is <code>EEE, d MMM yyyy HH:mm:ss Z</code> and
 * locale is always US-ASCII.
 *
 * @version $Rev$ $Date$
 */
public class IMAPDateFormat extends SimpleDateFormat {
    public IMAPDateFormat() {
        super("dd-MMM-yyyy HH:mm:ss Z", Locale.US);
    }
    public StringBuffer format(Date date, StringBuffer buffer, FieldPosition position) {
        StringBuffer result = super.format(date, buffer, position);
        // The RFC 2060 requires that the day in the date be formatted with either 2 digits
        // or one digit.  Our format specifies 2 digits, which pads with leading
        // zeros.  We need to check for this and whack it if it's there
        if (result.charAt(0) == '0') {
            result.deleteCharAt(0); 
        }
        return result;
    }

    /**
     * The calendar cannot be set
     * @param calendar
     * @throws UnsupportedOperationException
     */
    public void setCalendar(Calendar calendar) {
        throw new UnsupportedOperationException();
    }

    /**
     * The format cannot be set
     * @param format
     * @throws UnsupportedOperationException
     */
    public void setNumberFormat(NumberFormat format) {
        throw new UnsupportedOperationException();
    }
}

