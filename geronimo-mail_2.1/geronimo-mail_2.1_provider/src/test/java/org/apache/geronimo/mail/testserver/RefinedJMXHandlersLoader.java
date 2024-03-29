/**
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.geronimo.mail.testserver;


import java.util.ArrayList;
import java.util.List;

import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.smtpserver.jmx.CommandHandlerResultJMXMonitor;
import org.apache.james.smtpserver.jmx.ConnectHandlerResultJMXMonitor;
import org.apache.james.smtpserver.jmx.HookResultJMXMonitor;
//import org.apache.james.smtpserver.jmx.LineHandlerResultJMXMonitor;

public class RefinedJMXHandlersLoader implements HandlersPackage {

    private final List<String> handlers = new ArrayList<String>();

    public RefinedJMXHandlersLoader() {
        handlers.add(ConnectHandlerResultJMXMonitor.class.getName());
        handlers.add(CommandHandlerResultJMXMonitor.class.getName());
        //handlers.add(LineHandlerResultJMXMonitor.class.getName());
        handlers.add(HookResultJMXMonitor.class.getName());
    }

    /**
     * @see org.apache.james.protocols.api.handler.HandlersPackage#getHandlers()
     */
    public List<String> getHandlers() {
        return handlers;
    }

}

