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

 /****************************************************************
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

import java.util.LinkedList;
import java.util.List;

import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.smtp.core.ExpnCmdHandler;
import org.apache.james.protocols.smtp.core.HeloCmdHandler;
import org.apache.james.protocols.smtp.core.HelpCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.PostmasterAbuseRcptHook;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.ReceivedDataLineFilter;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.VrfyCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.AuthCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.EhloCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;
import org.apache.james.protocols.smtp.core.esmtp.StartTlsCmdHandler;
import org.apache.james.protocols.smtp.core.log.HookResultLogger;
import org.apache.james.smtpserver.AddDefaultAttributesMessageHook;
import org.apache.james.smtpserver.AuthRequiredToRelayRcptHook;
import org.apache.james.smtpserver.DataLineJamesMessageHookHandler;
import org.apache.james.smtpserver.JamesDataCmdHandler;
import org.apache.james.smtpserver.JamesMailCmdHandler;
import org.apache.james.smtpserver.JamesRcptCmdHandler;
import org.apache.james.smtpserver.JamesWelcomeMessageHandler;
import org.apache.james.smtpserver.SendMailHandler;
import org.apache.james.smtpserver.SenderAuthIdentifyVerificationRcptHook;
import org.apache.james.smtpserver.UsersRepositoryAuthHook;

/**
 * This class represent the base command handlers which are shipped with james.
 */
public class RefinedSmtpCoreCmdHandlerLoader implements HandlersPackage {

    private final String COMMANDDISPATCHER = CommandDispatcher.class.getName();
    private final String AUTHCMDHANDLER = AuthCmdHandler.class.getName();
    private final String DATACMDHANDLER = JamesDataCmdHandler.class.getName();
    private final String EHLOCMDHANDLER = EhloCmdHandler.class.getName();
    private final String EXPNCMDHANDLER = ExpnCmdHandler.class.getName();
    private final String HELOCMDHANDLER = HeloCmdHandler.class.getName();
    private final String HELPCMDHANDLER = HelpCmdHandler.class.getName();
    private final String MAILCMDHANDLER = JamesMailCmdHandler.class.getName();
    private final String NOOPCMDHANDLER = NoopCmdHandler.class.getName();
    private final String QUITCMDHANDLER = QuitCmdHandler.class.getName();
    private final String RCPTCMDHANDLER = JamesRcptCmdHandler.class.getName();
    private final String RSETCMDHANDLER = RsetCmdHandler.class.getName();
    private final String VRFYCMDHANDLER = VrfyCmdHandler.class.getName();
    private final String MAILSIZEHOOK = MailSizeEsmtpExtension.class.getName();
    private final String WELCOMEMESSAGEHANDLER = JamesWelcomeMessageHandler.class.getName();
    private final String USERSREPOSITORYAUTHHANDLER = UsersRepositoryAuthHook.class.getName();
    private final String POSTMASTERABUSEHOOK = PostmasterAbuseRcptHook.class.getName();
    private final String AUTHREQUIREDTORELAY = AuthRequiredToRelayRcptHook.class.getName();
    private final String SENDERAUTHIDENTITYVERIFICATION = SenderAuthIdentifyVerificationRcptHook.class.getName();
    private final String RECEIVEDDATALINEFILTER = ReceivedDataLineFilter.class.getName();
    private final String DATALINEMESSAGEHOOKHANDLER = DataLineJamesMessageHookHandler.class.getName();
    private final String STARTTLSHANDLER = StartTlsCmdHandler.class.getName();

    // MessageHooks
    private final String ADDDEFAULTATTRIBUTESHANDLER = AddDefaultAttributesMessageHook.class.getName();
    private final String SENDMAILHANDLER = SendMailHandler.class.getName();

    // logging stuff
    private final String COMMANDHANDLERRESULTLOGGER = CommandHandlerResultLogger.class.getName();
    private final String HOOKRESULTLOGGER = HookResultLogger.class.getName();

    private final List<String> commands = new LinkedList<String>();

    public RefinedSmtpCoreCmdHandlerLoader() {
        // Insert the base commands in the Map
        commands.add(WELCOMEMESSAGEHANDLER);
        commands.add(COMMANDDISPATCHER);
        commands.add(AUTHCMDHANDLER);
        commands.add(DATACMDHANDLER);
        commands.add(EHLOCMDHANDLER);
        commands.add(EXPNCMDHANDLER);
        commands.add(HELOCMDHANDLER);
        commands.add(HELPCMDHANDLER);
        commands.add(MAILCMDHANDLER);
        commands.add(NOOPCMDHANDLER);
        commands.add(QUITCMDHANDLER);
        commands.add(RCPTCMDHANDLER);
        commands.add(RSETCMDHANDLER);
        commands.add(VRFYCMDHANDLER);
        commands.add(MAILSIZEHOOK);
        commands.add(USERSREPOSITORYAUTHHANDLER);
        commands.add(AUTHREQUIREDTORELAY);
        commands.add(SENDERAUTHIDENTITYVERIFICATION);
        commands.add(POSTMASTERABUSEHOOK);
        commands.add(RECEIVEDDATALINEFILTER);
        commands.add(DATALINEMESSAGEHOOKHANDLER);
        commands.add(STARTTLSHANDLER);
        // Add the default messageHooks
        commands.add(ADDDEFAULTATTRIBUTESHANDLER);
        commands.add(SENDMAILHANDLER);

        // Add logging stuff
        commands.add(COMMANDHANDLERRESULTLOGGER);
        commands.add(HOOKRESULTLOGGER);
    }

    /**
     * @see org.apache.james.protocols.api.handler.HandlersPackage#getHandlers()
     */
    public List<String> getHandlers() {
        return commands;
    }
}

