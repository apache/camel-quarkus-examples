/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.partner.gateway;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.net.SocketAddress;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.partner.gateway.audit.Caller;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.vertx.VertxPlatformHttpConstants;

/**
 * Reads the identity that Quarkus verified before the request reached Camel and stores it in exchange properties.
 * Identity and tenant never come from the request body or from headers the caller can set.
 */
@ApplicationScoped
public class CallerIdentity implements Processor {

    /** Exchange property holding the {@link Caller}. */
    public static final String CALLER = "caller";
    /** Exchange property holding the tenant, used for throttling and in the logging MDC. */
    public static final String PARTNER_ID = "partnerId";

    private static final String PARTNER_ROLE = "partner";

    @Override
    public void process(Exchange exchange) {
        Message message = exchange.getMessage();
        // platform-http puts the Vert.x user of the authenticated request here
        if (!(message.getHeader(VertxPlatformHttpConstants.AUTHENTICATED_USER) instanceof QuarkusHttpUser user)) {
            throw new IllegalStateException("The request is not authenticated");
        }
        SecurityIdentity identity = user.getSecurityIdentity();
        SocketAddress remote = message.getHeader(VertxPlatformHttpConstants.REMOTE_ADDRESS, SocketAddress.class);
        Caller caller = Caller.of(identity, remote != null ? remote.hostAddress() : null, exchange.getExchangeId());
        exchange.setProperty(CALLER, caller);
        exchange.setProperty(PARTNER_ID, caller.partnerId());
        // fail closed: a partner without a tenant could not be isolated from the others
        if (caller.partnerId() == null && identity.hasRole(PARTNER_ROLE)) {
            throw new MissingTenantException();
        }
    }
}
