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

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.partner.gateway.audit.AuditLog;
import org.acme.partner.gateway.audit.Caller;
import org.acme.partner.gateway.audit.Outcome;
import org.acme.partner.gateway.order.OrderInProgressException;
import org.acme.partner.gateway.order.OrderNotOwnedException;
import org.apache.camel.Exchange;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteConfigurationBuilder;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Maps exceptions of all routes to RFC 9457 problem responses and audits them. Clients never see exception
 * messages, stack traces or tokens.
 */
@ApplicationScoped
public class ErrorHandling extends RouteConfigurationBuilder {

    private static final Logger LOG = Logger.getLogger(ErrorHandling.class);

    @Inject
    AuditLog auditLog;

    @ConfigProperty(name = "gateway.throttle.period")
    Duration throttlePeriod;

    @Override
    public void configuration() {
        RouteConfigurationDefinition errors = routeConfiguration();

        // schema violations, missing or invalid Idempotency-Key, invalid query parameters
        errors.onException(ValidationException.class).handled(true)
                .process(e -> problem(e, 400, "Bad Request", "The request does not match the API contract",
                        "REQUEST_REJECTED", Outcome.REJECTED));

        errors.onException(UnsupportedMediaTypeException.class).handled(true)
                .process(e -> problem(e, 415, "Unsupported Media Type", "The order must be application/json",
                        "REQUEST_REJECTED", Outcome.REJECTED));

        errors.onException(ThrottlerRejectedExecutionException.class).handled(true)
                .setHeader("Retry-After", constant(Math.max(1, throttlePeriod.toSeconds())))
                .process(e -> problem(e, 429, "Too Many Requests", "Rate limit exceeded, retry later",
                        "REQUEST_REJECTED", Outcome.REJECTED));

        errors.onException(MissingTenantException.class).handled(true)
                .process(e -> problem(e, 403, "Forbidden", "The caller is not assigned to a partner", "AUTHZ_DENIED",
                        Outcome.DENIED));

        // same answer for unknown orders and orders of other partners, so that order ids cannot be probed
        errors.onException(OrderNotOwnedException.class).handled(true)
                .process(e -> problem(e, 404, "Not Found", "Order not found", "ORDER_READ_DENIED", Outcome.DENIED));

        errors.onException(OrderInProgressException.class).handled(true)
                .process(e -> problem(e, 409, "Conflict", "A request with the same Idempotency-Key is still in progress",
                        "REQUEST_REJECTED", Outcome.REJECTED));

        errors.onException(Exception.class).handled(true)
                .process(e -> problem(e, 500, "Internal Server Error", "The request could not be processed",
                        "REQUEST_FAILED", Outcome.FAILED));
    }

    private void problem(Exchange exchange, int status, String title, String detail, String action, Outcome outcome) {
        Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        if (status == 500) {
            LOG.errorf(cause, "Exchange %s failed", exchange.getExchangeId());
        }

        Caller caller = exchange.getProperty(CallerIdentity.CALLER, Caller.class);
        String resource = cause instanceof OrderNotOwnedException notOwned
                ? "order/" + notOwned.getOrderId()
                : "route/" + exchange.getFromRouteId();
        // the messages of our own exceptions are safe, others may echo input
        String reason = cause instanceof OrderNotOwnedException || cause instanceof MissingTenantException
                ? cause.getMessage()
                : cause.getClass().getSimpleName();
        auditLog.append(caller != null ? caller : Caller.SYSTEM, action, resource, outcome, reason);

        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, status);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/problem+json");
        exchange.getMessage().setBody(Json.encode(new Problem(title, status, detail)));
    }
}
