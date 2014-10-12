/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.localserver;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.apache.http.ExceptionLogger;
import org.apache.http.HttpHost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.nio.bootstrap.HttpServer;
import org.apache.http.impl.nio.bootstrap.ServerBootstrap;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.junit.After;
import org.junit.Before;

public abstract class AbstractAsyncTest {

    public enum ProtocolScheme { http, https };

    protected final ProtocolScheme scheme;

    protected ServerBootstrap serverBootstrap;
    protected HttpServer server;
    protected PoolingNHttpClientConnectionManager connMgr;

    public AbstractAsyncTest(final ProtocolScheme scheme) {
        this.scheme = scheme;
    }

    public AbstractAsyncTest() {
        this(ProtocolScheme.http);
    }

    public String getSchemeName() {
        return this.scheme.name();
    }

    public HttpHost startServer() throws Exception {
        this.server = this.serverBootstrap.create();
        this.server.start();

        final ListenerEndpoint endpoint = this.server.getEndpoint();
        endpoint.waitFor();

        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        return new HttpHost("localhost", address.getPort(), this.scheme.name());
    }

    @Before
    public void setUp() throws Exception {
        this.serverBootstrap = ServerBootstrap.bootstrap();
        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(15000)
                .build();
        this.serverBootstrap.setServerInfo("TEST/1.1");
        this.serverBootstrap.setIOReactorConfig(ioReactorConfig);
        this.serverBootstrap.setExceptionLogger(ExceptionLogger.STD_ERR);
        if (this.scheme.equals(ProtocolScheme.https)) {
            this.serverBootstrap.setSslContext(SSLTestContexts.createServerSSLContext());
        }

        final RegistryBuilder<SchemeIOSessionStrategy> builder = RegistryBuilder.create();
        builder.register("http", NoopIOSessionStrategy.INSTANCE);
        if (this.scheme.equals(ProtocolScheme.https)) {
            builder.register("https", new SSLIOSessionStrategy(
                    SSLTestContexts.createClientSSLContext(),
                    // TODO: replace with the default hostname verifier
                    // TODO: after upgrade to HttpCore 4.4-beta2 or newer
                    NoopHostnameVerifier.INSTANCE));
        }
        final Registry<SchemeIOSessionStrategy> registry =  builder.build();
        final DefaultConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
        this.connMgr = new PoolingNHttpClientConnectionManager(ioReactor, registry);
    }

    @After
    public void shutDown() throws Exception {
        if (this.server != null) {
            this.server.shutdown(10, TimeUnit.SECONDS);
        }
    }

}