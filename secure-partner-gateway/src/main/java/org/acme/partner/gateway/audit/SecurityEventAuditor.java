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
package org.acme.partner.gateway.audit;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.spi.runtime.AuthenticationFailureEvent;
import io.quarkus.security.spi.runtime.AuthorizationFailureEvent;
import io.quarkus.security.spi.runtime.SecurityEvent;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/**
 * Audits the requests Quarkus rejects with 401 or 403 before they reach Camel. Async observers keep the database
 * write off the HTTP event loop.
 */
@ApplicationScoped
public class SecurityEventAuditor {

    @Inject
    AuditLog auditLog;

    void onAuthenticationFailure(@ObservesAsync AuthenticationFailureEvent event) {
        // only the exception type: messages of token validation errors may contain token claims
        auditLog.append(caller(null, event), "AUTHN_FAILED", resource(event), Outcome.DENIED,
                event.getAuthenticationFailure().getClass().getSimpleName());
    }

    void onAuthorizationFailure(@ObservesAsync AuthorizationFailureEvent event) {
        SecurityIdentity identity = event.getSecurityIdentity();
        String reason = identity == null || identity.isAnonymous() ? "no credentials" : "forbidden";
        auditLog.append(caller(identity, event), "AUTHZ_DENIED", resource(event), Outcome.DENIED, reason);
    }

    private static Caller caller(SecurityIdentity identity, SecurityEvent event) {
        RoutingContext context = routingContext(event);
        String remoteAddr = context != null && context.request().remoteAddress() != null
                ? context.request().remoteAddress().hostAddress()
                : null;
        return Caller.of(identity, remoteAddr, null);
    }

    private static String resource(SecurityEvent event) {
        RoutingContext context = routingContext(event);
        return context != null ? context.request().method() + " " + context.normalizedPath() : null;
    }

    private static RoutingContext routingContext(SecurityEvent event) {
        return (RoutingContext) event.getEventProperties().get(RoutingContext.class.getName());
    }
}
