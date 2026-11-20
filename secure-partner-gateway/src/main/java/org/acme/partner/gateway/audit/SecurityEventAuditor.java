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

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.spi.runtime.AuthenticationFailureEvent;
import io.quarkus.security.spi.runtime.AuthorizationFailureEvent;
import io.quarkus.security.spi.runtime.SecurityEvent;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

/**
 * Records the 401 and 403 answers of Quarkus security, which Camel never sees. Requests without a valid token are only
 * counted: anyone can send them, so they must not make the gateway write to the database.
 */
@ApplicationScoped
public class SecurityEventAuditor {

    /** Requests rejected without an audit record, tagged with the HTTP status. */
    public static final String REJECTED_REQUESTS = "gateway.rejected.requests";

    @Inject
    AuditLog auditLog;

    @Inject
    MeterRegistry registry;

    void onAuthenticationFailure(@Observes AuthenticationFailureEvent event) {
        registry.counter(REJECTED_REQUESTS, "status", "401").increment();
    }

    void onAnonymousAuthorizationFailure(@Observes AuthorizationFailureEvent event) {
        if (isAnonymous(event.getSecurityIdentity())) {
            registry.counter(REJECTED_REQUESTS, "status", "401").increment();
        }
    }

    // Asynchronous, as Quarkus may fire the event on an I/O thread: the record can be lost if the gateway stops first
    void onAuthorizationFailure(@ObservesAsync AuthorizationFailureEvent event) {
        SecurityIdentity identity = event.getSecurityIdentity();
        if (isAnonymous(identity)) {
            return;
        }
        auditLog.append(caller(identity, event), "AUTHZ_DENIED", resource(event), Outcome.DENIED, "forbidden");
    }

    private static boolean isAnonymous(SecurityIdentity identity) {
        return identity == null || identity.isAnonymous();
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
