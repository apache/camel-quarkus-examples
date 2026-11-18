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
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Who made a request, from where and in which Camel exchange. Built only from the verified token and the
 * connection, never from the request body or headers.
 */
public record Caller(String name, ActorType type, String partnerId, String remoteAddr, String exchangeId) {

    /** Signed claim carrying the tenant, added by the identity provider to partner tokens. */
    public static final String PARTNER_ID_CLAIM = "partner_id";

    public static final Caller SYSTEM = new Caller("gateway", ActorType.SYSTEM, null, null, null);

    public static Caller of(SecurityIdentity identity, String remoteAddr, String exchangeId) {
        if (identity == null || identity.isAnonymous()) {
            return new Caller("anonymous", ActorType.ANONYMOUS, null, remoteAddr, exchangeId);
        }
        // a blank claim counts as none: all clients with a blank claim would share one tenant
        String partnerId = identity.getPrincipal() instanceof JsonWebToken jwt
                && jwt.getClaim(PARTNER_ID_CLAIM) instanceof String claim && !claim.isBlank() ? claim : null;
        ActorType type = partnerId != null ? ActorType.CLIENT : ActorType.USER;
        return new Caller(identity.getPrincipal().getName(), type, partnerId, remoteAddr, exchangeId);
    }
}
