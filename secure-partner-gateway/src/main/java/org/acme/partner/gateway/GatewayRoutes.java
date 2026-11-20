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
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.http.QueryStringDecoder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.partner.gateway.audit.AuditLog;
import org.acme.partner.gateway.order.OrderAccepted;
import org.acme.partner.gateway.order.OrderService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static org.apache.camel.support.builder.PredicateBuilder.or;

@ApplicationScoped
public class GatewayRoutes extends RouteBuilder {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    // Inputs that reach the database are validated first: invalid ones get 400, not a database error
    static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    static final String ACTOR = "[^\\p{Cntrl}]{1,255}";
    static final String ACTION = "[A-Z_]{1,32}";

    @Inject
    CallerIdentity callerIdentity;

    @Inject
    OrderService orderService;

    @Inject
    AuditLog auditLog;

    @ConfigProperty(name = "gateway.throttle.max-requests")
    long throttleMaxRequests;

    @ConfigProperty(name = "gateway.throttle.period")
    Duration throttlePeriod;

    @ConfigProperty(name = "audit.anchor-period")
    Duration anchorPeriod;

    @Override
    public void configure() {
        // platform-http answers 415 for a wrong Content-Type and 406 for an unacceptable Accept header. Malformed JSON
        // is left to the schema validation of the route, behind the trust boundary.
        rest("/api")
                .post("/orders").consumes("application/json").produces("application/json").to("direct:order-create")
                .get("/orders/{id}").produces("application/json").to("direct:order-read")
                .get("/audit").produces("application/json").to("direct:audit-query")
                .get("/audit/verify").produces("application/json").to("direct:audit-verify");

        // Every route starts at the trust boundary: the caller comes from the verified token, then all request
        // headers are dropped except the inputs the route declares (Authorization, Cookie, Camel*, spoofed ids, ...).
        // Camel puts HTTP headers, query and path parameters into one header map, so each route also fixes where its
        // inputs come from.
        from("direct:order-create").routeId("order-create")
                .process(callerIdentity)
                // first, so that rejected requests count against the limit too
                .throttle(throttleMaxRequests).totalRequestsMode().timePeriodMillis(throttlePeriod.toMillis())
                .correlationExpression(exchangeProperty(CallerIdentity.PARTNER_ID)).rejectExecution(true)
                .process(GatewayRoutes::requireJson)
                // no query string: the Idempotency-Key can only be the HTTP header
                .validate(header(Exchange.HTTP_QUERY).isNull())
                .removeHeaders("*", IDEMPOTENCY_KEY)
                .validate(header(IDEMPOTENCY_KEY).regex("[A-Za-z0-9_-]{8,64}"))
                // the order must be the whole body, by default text after the first JSON value is ignored.
                // A duplicate key is ambiguous, by default the last value wins.
                .to("json-validator:schema/order.json"
                        + "?enabledDeserializationFeatures=FAIL_ON_TRAILING_TOKENS,FAIL_ON_READING_DUP_TREE_KEY")
                .process(Json::readOrder)
                // a new order or a retry, decided by the unique (partner_id, idempotency_key) of the order table
                .bean(orderService, "submit(${exchangeProperty.caller}, ${header.Idempotency-Key}, ${body})")
                // the MDC adds partnerId and camel.exchangeId to this log record
                .log("Order ${body.orderId} accepted, created: ${body.created}")
                .process(e -> e.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,
                        e.getMessage().getBody(OrderAccepted.class).created() ? 202 : 200))
                .process(Json::write);

        from("direct:order-read").routeId("order-read")
                .process(callerIdentity)
                // the path parameter wins over a query parameter or HTTP header named id
                .removeHeaders("*", "id")
                .validate(header("id").regex(UUID))
                .bean(orderService, "read(${exchangeProperty.caller}, ${header.id})")
                .process(Json::write);

        from("direct:audit-query").routeId("audit-query")
                .process(callerIdentity)
                .process(queryParameters("actor", "action", "limit"))
                .validate(simple("${header.limit} == null || ${header.limit} regex '[1-9][0-9]{0,2}'"))
                .validate(or(header("actor").isNull(), header("actor").regex(ACTOR)))
                .validate(or(header("action").isNull(), header("action").regex(ACTION)))
                .bean(auditLog, "query(${exchangeProperty.caller}, ${header.actor}, ${header.action}, ${header.limit})")
                .process(Json::write);

        from("direct:audit-verify").routeId("audit-verify")
                .process(callerIdentity)
                .removeHeaders("*")
                .bean(auditLog, "verify(${exchangeProperty.caller})")
                .process(Json::write);

        // The chain head written to the logs is an anchor: once shipped to a system the database administrators
        // cannot change, a rewritten chain no longer matches it
        from("timer:audit-anchor?period=" + anchorPeriod.toMillis()).routeId("audit-anchor")
                .bean(auditLog, "head")
                .log("Audit chain head: ${body}");
    }

    // Replaces all headers with the given query parameters, read from the query string: HTTP headers with the same
    // names are ignored. A repeated parameter is rejected.
    private static Processor queryParameters(String... names) {
        return exchange -> {
            Message message = exchange.getMessage();
            String query = message.getHeader(Exchange.HTTP_QUERY, String.class);
            message.removeHeaders("*");
            if (query != null) {
                Map<String, List<String>> parameters = new QueryStringDecoder(query, false).parameters();
                for (String name : names) {
                    List<String> values = parameters.get(name);
                    if (values != null && values.size() > 1) {
                        throw new ValidationException(exchange, "Repeated query parameter " + name);
                    }
                    if (values != null) {
                        message.setHeader(name, values.get(0));
                    }
                }
            }
        };
    }

    // platform-http answers 415 for other media types, but lets wildcards such as application/* through
    private static void requireJson(Exchange exchange) {
        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, "", String.class);
        if (!"application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim())) {
            throw new UnsupportedMediaTypeException();
        }
    }
}
