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
package org.apache.geronimo.javamail.testserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import javax.mail.Flags;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.imap.encode.main.DefaultImapEncoderFactory;
import org.apache.james.imap.encode.main.DefaultLocalizer;
import org.apache.james.imap.main.DefaultImapDecoderFactory;
import org.apache.james.imap.processor.main.DefaultImapProcessorFactory;
import org.apache.james.imapserver.netty.IMAPServer;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.GroupMembershipResolver;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.Authenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailrepository.mock.MockMailRepositoryStore;
import org.apache.james.pop3server.netty.POP3Server;
import org.apache.james.protocols.lib.PortUtil;
import org.apache.james.protocols.lib.mock.MockProtocolHandlerLoader;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.file.FileMailQueueFactory;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.smtpserver.netty.SMTPServer;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.mock.MockUsersRepository;
import org.apache.mailet.HostAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//James based POP3 or IMAP or SMTP server (for unittesting only)
public class MailServer {

    private POP3Server pop3Server;
    private IMAPServer imapServer;
    private SMTPServer smtpServer;
    private AlterableDNSServer dnsServer;
    private final MockUsersRepository usersRepository = new MockUsersRepository();
    private final MockFileSystem fileSystem = new MockFileSystem();
    private MockProtocolHandlerLoader protocolHandlerChain;
    private StoreMailboxManager<Long> mailboxManager;

    private MockMailRepositoryStore store;
    private DNSService dnsService;
    private MailQueueFactory queueFactory;
    private MailQueue queue;
    private final Semaphore sem = new Semaphore(0);

    public void ensureMsgCount(final int count) throws InterruptedException {
        sem.acquire(count);
    }

    private class Fetcher extends Thread {

        private final MailQueue queue;
        private final MessageManager mailbox;
        private final MailboxSession session;

        Fetcher(final MailQueue queue, final MessageManager mailbox, final MailboxSession session) {
            super();
            this.queue = queue;
            this.mailbox = mailbox;
            this.session = session;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    System.out.println("Await new mail ...");
                    final MailQueueItem item = queue.deQueue();
                    System.out.println("got it");
                    final MimeMessage msg = item.getMail().getMessage();
                    final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    msg.writeTo(bout);
                    mailbox.appendMessage(new ByteArrayInputStream(bout.toByteArray()), new Date(), session, true, new Flags());
                    item.done(true);
                    sem.release();
                    System.out.println("mail copied over");
                } catch (final Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

    }

    public MailServer() {
        super();
        try {
            usersRepository.addUser("serveruser", "serverpass");
        } catch (final UsersRepositoryException e) {
            throw new RuntimeException(e);
        }

    }

    public void start(final SmtpTestConfiguration smtpConfig, final Pop3TestConfiguration pop3Config, final ImapTestConfiguration imapConfig)
            throws Exception {
        setUpServiceManager();

        imapServer = new IMAPServer();

        imapServer.setImapEncoder(DefaultImapEncoderFactory.createDefaultEncoder(new DefaultLocalizer(), false));
        imapServer.setImapDecoder(DefaultImapDecoderFactory.createDecoder());

        pop3Server = new POP3Server();
        pop3Server.setProtocolHandlerLoader(protocolHandlerChain);

        smtpServer = new SMTPServer() {
            @Override
            protected java.lang.Class<? extends org.apache.james.protocols.lib.handler.HandlersPackage> getJMXHandlersPackage() {
                return RefinedJMXHandlersLoader.class;
            };

        };
        smtpServer.setProtocolHandlerLoader(protocolHandlerChain);
        smtpServer.setDNSService(dnsServer);

        imapServer.setFileSystem(fileSystem);
        pop3Server.setFileSystem(fileSystem);
        smtpServer.setFileSystem(fileSystem);

        final Logger log = LoggerFactory.getLogger("Mock");

        imapServer.setLog(log);
        pop3Server.setLog(log);
        smtpServer.setLog(log);

        final MailboxPath mailboxPath = new MailboxPath(MailboxConstants.USER_NAMESPACE, "serveruser", "INBOX");
        final MailboxSession session = mailboxManager.login("serveruser", "serverpass", LoggerFactory.getLogger("Test"));

        if (!mailboxManager.mailboxExists(mailboxPath, session)) {
            mailboxManager.createMailbox(mailboxPath, session);
        }

        imapServer.setImapProcessor(DefaultImapProcessorFactory.createXListSupportingProcessor(mailboxManager, null, null));//new StoreSubscriptionManager(new InMemoryMailboxSessionMapperFactory()), null));

        //setupTestMails(session, mailboxManager.getMailbox(mailboxPath, session));

        new Fetcher(queue, mailboxManager.getMailbox(mailboxPath, session), session).start();

        smtpConfig.init();
        pop3Config.init();
        imapConfig.init();

        smtpServer.configure(smtpConfig);
        pop3Server.configure(pop3Config);
        imapServer.configure(imapConfig);

        smtpServer.init();
        pop3Server.init();
        imapServer.init();

    }

    public void stop() throws Exception {

        if (protocolHandlerChain != null) {
            protocolHandlerChain.dispose();
        }

        if (imapServer != null) {
            imapServer.destroy();
        }

        if (pop3Server != null) {
            pop3Server.destroy();
        }

        if (smtpServer != null) {
            smtpServer.destroy();
        }

    }

    /* protected void setupTestMailsx(MailboxSession session, MessageManager mailbox) throws MailboxException {
         mailbox.appendMessage(new ByteArrayInputStream(content), new Date(), session, true, new Flags());
         byte[] content2 = ("EMPTY").getBytes();
         mailbox.appendMessage(new ByteArrayInputStream(content2), new Date(), session, true, new Flags());
     }*/

    protected void setUpServiceManager() throws Exception {
        protocolHandlerChain = new MockProtocolHandlerLoader();
        protocolHandlerChain.put("usersrepository", usersRepository);

        final InMemoryMailboxSessionMapperFactory factory = new InMemoryMailboxSessionMapperFactory();
        final MailboxACLResolver aclResolver = new UnionMailboxACLResolver();
        final GroupMembershipResolver groupMembershipResolver = new SimpleGroupMembershipResolver();
        mailboxManager = new StoreMailboxManager<Long>(factory, new Authenticator() {

            public boolean isAuthentic(final String userid, final CharSequence passwd) {
                try {
                    return usersRepository.test(userid, passwd.toString());
                } catch (final UsersRepositoryException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }, aclResolver, groupMembershipResolver);
        mailboxManager.init();

        protocolHandlerChain.put("mailboxmanager", mailboxManager);

        protocolHandlerChain.put("fileSystem", fileSystem);

        //smtp
        dnsServer = new AlterableDNSServer();
        store = new MockMailRepositoryStore();
        protocolHandlerChain.put("mailStore", store);
        protocolHandlerChain.put("dnsservice", dnsServer);
        protocolHandlerChain.put("org.apache.james.smtpserver.protocol.DNSService", dnsService);

        protocolHandlerChain.put("recipientrewritetable", new RecipientRewriteTable() {

            public void addRegexMapping(final String user, final String domain, final String regex) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void removeRegexMapping(final String user, final String domain, final String regex)
                    throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void addAddressMapping(final String user, final String domain, final String address)
                    throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void removeAddressMapping(final String user, final String domain, final String address)
                    throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void addErrorMapping(final String user, final String domain, final String error) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void removeErrorMapping(final String user, final String domain, final String error)
                    throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public Collection<String> getUserDomainMappings(final String user, final String domain) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void addMapping(final String user, final String domain, final String mapping) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void removeMapping(final String user, final String domain, final String mapping) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public Map<String, Collection<String>> getAllMappings() throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void addAliasDomainMapping(final String aliasDomain, final String realDomain) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public void removeAliasDomainMapping(final String aliasDomain, final String realDomain) throws RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }

            public Collection<String> getMappings(final String user, final String domain) throws ErrorMappingException,
            RecipientRewriteTableException {
                throw new UnsupportedOperationException("Not implemented");
            }
        });

        protocolHandlerChain.put("org.apache.james.smtpserver.protocol.DNSService", dnsService);

        final FileMailQueueFactory ff = new FileMailQueueFactory();// MockMailQueueFactory();
        ff.setFileSystem(fileSystem);
        queueFactory = ff;

        queue = queueFactory.getQueue(MailQueueFactory.SPOOL);
        protocolHandlerChain.put("mailqueuefactory", queueFactory);
        protocolHandlerChain.put("domainlist", new SimpleDomainList() {

            @Override
            public String getDefaultDomain() {
                return "localhost";
            }

            @Override
            public String[] getDomains() throws DomainListException {
                return new String[] { "localhost" };
            }

            @Override
            public boolean containsDomain(final String serverName) {
                return "localhost".equals(serverName);
            }
        });

    }

    /**
     * @return the queue
     */
    public MailQueue getQueue() {
        return queue;
    }

    public static File getAbsoluteFilePathFromClassPath(final String fileNameFromClasspath) {

        File configFile = null;
        final URL configURL = MailServer.class.getClassLoader().getResource(fileNameFromClasspath);
        if (configURL != null) {
            try {
                configFile = new File(URLDecoder.decode(configURL.getFile(), "UTF-8"));
            } catch (final UnsupportedEncodingException e) {
                return null;
            }

            if (configFile.exists() && configFile.canRead()) {
                return configFile;
            } else {

                System.out.println("Cannot read from {}, maybe the file does not exists? " + configFile.getAbsolutePath());
            }

        } else {
            System.out.println("Failed to load " + fileNameFromClasspath);
        }

        return null;

    }

    public static abstract class AbstractTestConfiguration extends DefaultConfigurationBuilder {

        private final int listenerPort = PortUtil.getNonPrivilegedPort();

        /**
         * @return the listenerPort
         */
        public int getListenerPort() {
            return listenerPort;
        }

        public AbstractTestConfiguration enableSSL(final boolean enableStartTLS, final boolean enableSSL) {
            addProperty("tls.[@startTLS]", enableStartTLS);
            addProperty("tls.[@socketTLS]", enableSSL);
            addProperty("tls.keystore", "file://" + getAbsoluteFilePathFromClassPath("dummykeystore.jks").getAbsolutePath());
            addProperty("tls.secret", "123456");
            addProperty("tls.provider", "org.bouncycastle.jce.provider.BouncyCastleProvider");
            return this;
        }

        public void init() {
            addProperty("[@enabled]", true);
            addProperty("bind", "127.0.0.1:" + this.listenerPort);
            addProperty("connectiontimeout", "360000");
            //addProperty("jmxName", getServertype().name()+"on"+this.listenerPort);

        }

    }

    public static class Pop3TestConfiguration extends AbstractTestConfiguration {

        @Override
        public void init() {
            super.init();

            addProperty("helloName", "pop3 on port " + getListenerPort());

            addProperty("handlerchain.[@coreHandlersPackage]", RefinedCoreCmdHandlerLoader.class.getName());

        }

    }

    public static class ImapTestConfiguration extends AbstractTestConfiguration {

        @Override
        public void init() {
            super.init();

            addProperty("helloName", "imap on port " + getListenerPort());

        }

    }

    public static class SmtpTestConfiguration extends AbstractTestConfiguration {

        @Override
        public void init() {
            super.init();
            addProperty("handlerchain.handler[@class]", RefinedSmtpCoreCmdHandlerLoader.class.getName());

        }

        public SmtpTestConfiguration setRequireAuth(final boolean requireAuth) {

            addProperty("authRequired", requireAuth);
            return this;
        }

        public SmtpTestConfiguration setHeloEhloEnforcement(final boolean heloEhloEnforcement) {

            addProperty("heloEhloEnforcement", heloEhloEnforcement);
            return this;
        }

    }

    public static class DummySocketFactory extends SSLSocketFactory {

        @Override
        public Socket createSocket(final String host, final int port) throws IOException, UnknownHostException {
            throw new IOException("dummy socket factory");
        }

        @Override
        public Socket createSocket(final InetAddress host, final int port) throws IOException {
            throw new IOException("dummy socket factory");
        }

        @Override
        public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException,
                UnknownHostException {
            throw new IOException("dummy socket factory");
        }

        @Override
        public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddress, final int localPort)
                throws IOException {
            throw new IOException("dummy socket factory");
        }

        @Override
        public Socket createSocket(final Socket arg0, final String arg1, final int arg2, final boolean arg3) throws IOException {
            throw new IOException("dummy socket factory");
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return new String[0];
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[0];
        }

    }

    private final class AlterableDNSServer implements DNSService {

        private InetAddress localhostByName = null;

        public Collection<String> findMXRecords(final String hostname) {
            final List<String> res = new ArrayList<String>();
            if (hostname == null) {
                return res;
            }
            if ("james.apache.org".equals(hostname)) {
                res.add("nagoya.apache.org");
            }
            return res;
        }

        public Iterator<HostAddress> getSMTPHostAddresses(final String domainName) {
            throw new UnsupportedOperationException("Unimplemented mock service");
        }

        public InetAddress[] getAllByName(final String host) throws UnknownHostException {
            return new InetAddress[] { getByName(host) };
        }

        public InetAddress getByName(final String host) throws UnknownHostException {
            if (getLocalhostByName() != null) {
                if ("127.0.0.1".equals(host)) {
                    return getLocalhostByName();
                }
            }

            if ("0.0.0.0".equals(host)) {
                return InetAddress.getByName("0.0.0.0");
            }

            if ("james.apache.org".equals(host)) {
                return InetAddress.getByName("james.apache.org");
            }

            if ("abgsfe3rsf.de".equals(host)) {
                throw new UnknownHostException();
            }

            if ("128.0.0.1".equals(host) || "192.168.0.1".equals(host) || "127.0.0.1".equals(host) || "127.0.0.0".equals(host)
                    || "255.0.0.0".equals(host) || "255.255.255.255".equals(host)) {
                return InetAddress.getByName(host);
            }

            throw new UnsupportedOperationException("getByName not implemented in mock for host: " + host);
        }

        public Collection<String> findTXTRecords(final String hostname) {
            final List<String> res = new ArrayList<String>();
            if (hostname == null) {
                return res;
            }

            if ("2.0.0.127.bl.spamcop.net.".equals(hostname)) {
                res.add("Blocked - see http://www.spamcop.net/bl.shtml?127.0.0.2");
            }
            return res;
        }

        public InetAddress getLocalhostByName() {
            return localhostByName;
        }

        public void setLocalhostByName(final InetAddress localhostByName) {
            this.localhostByName = localhostByName;
        }

        public String getHostName(final InetAddress addr) {
            return addr.getHostName();
        }

        public InetAddress getLocalHost() throws UnknownHostException {
            return InetAddress.getLocalHost();
        }
    }

}
