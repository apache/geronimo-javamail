/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.geronimo.javamail.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An implementation of an OutputStream just counts
 * the number of bytes written to the stream. 
 * @version $Rev$ $Date$
 */
public class CountingOutputStream extends OutputStream {
    // the counting accumulator 
    int count = 0; 

    // in order for this to work, we only need override the single character
    // form, as the others
    // funnel through this one by default.
    public void write(int ch) throws IOException {
        // just increment the count 
        count++; 
    }
    
    
    /**
     * Get the current accumulator total for this stream. 
     * 
     * @return The current count. 
     */
    public int getCount() {
        return count; 
    }
    
    
    /**
     * Reset the counter to zero. 
     */
    public void reset() {
        count = 0; 
    }
}

