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

import javax.mail.internet.MailDateFormat;

public class IMAPFetchDataItem {
    public static final int FETCH = 0;
    public static final int ENVELOPE = 1;
    public static final int BODY = 2;
    public static final int BODYSTRUCTURE = 3;
    public static final int INTERNALDATE = 4;
    public static final int SIZE = 5;
    public static final int UID = 6;
    public static final int TEXT = 7;
    public static final int HEADER = 8;
    public static final int FLAGS = 9;

    // the type of the FETCH response item.
    protected int type;

    public IMAPFetchDataItem(int type) {
        this.type = type;
    }

    /**
     * Get the type of the FetchResponse.
     *
     * @return The type indicator.
     */
    public int getType() {
        return type;
    }

    /**
     * Test if this fetch response is of the correct type.
     *
     * @param t      The type to test against.
     *
     * @return True if the Fetch response contains the requested type information.
     */
    public boolean isType(int t) {
        return type == t;
    }
}

