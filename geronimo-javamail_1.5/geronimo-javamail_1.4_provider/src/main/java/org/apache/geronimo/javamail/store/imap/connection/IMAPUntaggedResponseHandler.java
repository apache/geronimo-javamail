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

public interface IMAPUntaggedResponseHandler {
    /**
     * Handle an unsolicited untagged response receive back from a command.  This 
     * will be any responses left over after the command has cherry picked the 
     * bits that are relevent to the command just issued.  It is important 
     * that the unsolicited response be reacted to in order to keep the message 
     * caches in sync. 
     * 
     * @param response The response to handle.
     * 
     * @return true if the handle took care of the response and it should not be sent 
     *         to additional handlers.  false if broadcasting of the response should continue.
     */
    public boolean handleResponse(IMAPUntaggedResponse response);
}
