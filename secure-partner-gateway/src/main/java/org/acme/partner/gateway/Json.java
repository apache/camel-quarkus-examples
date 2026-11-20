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

import java.util.List;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.acme.partner.gateway.audit.AuditEvent;
import org.acme.partner.gateway.audit.Verification;
import org.acme.partner.gateway.order.OrderAccepted;
import org.acme.partner.gateway.order.OrderRequest;
import org.acme.partner.gateway.order.OrderView;
import org.apache.camel.Exchange;

/**
 * JSON bodies of the API, built with Vert.x JSON. Instants are ISO-8601 strings.
 */
final class Json {

    private Json() {
    }

    /** Reads an order that already passed the schema validation. */
    static void readOrder(Exchange exchange) {
        // the bytes, as the schema validation read them: the encoding is detected from the body, not the Content-Type
        JsonObject order = new JsonObject(Buffer.buffer(exchange.getMessage().getBody(byte[].class)));
        exchange.getMessage().setBody(new OrderRequest(order.getString("sku"), order.getInteger("quantity")));
    }

    /** Replaces the body with its JSON. */
    static void write(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
        exchange.getMessage().setBody(encode(exchange.getMessage().getBody()));
    }

    static String encode(Object body) {
        return switch (body) {
        case OrderAccepted accepted -> new JsonObject().put("orderId", accepted.orderId()).encode();
        case OrderView order -> new JsonObject()
                .put("orderId", order.orderId())
                .put("sku", order.sku())
                .put("quantity", order.quantity())
                .put("createdAt", string(order.createdAt()))
                .encode();
        case Verification verification -> {
            JsonObject json = new JsonObject()
                    .put("valid", verification.valid())
                    .put("checked", verification.checked());
            if (verification.brokenAtSeq() != null) {
                json.put("brokenAtSeq", verification.brokenAtSeq());
            }
            yield json.encode();
        }
        case Problem problem -> new JsonObject()
                .put("type", problem.type())
                .put("title", problem.title())
                .put("status", problem.status())
                .put("detail", problem.detail())
                .encode();
        case List<?> events -> new JsonArray(events.stream().map(e -> auditEvent((AuditEvent) e)).toList()).encode();
        default -> throw new IllegalArgumentException("No JSON form for " + body.getClass());
        };
    }

    private static JsonObject auditEvent(AuditEvent event) {
        return new JsonObject()
                .put("seq", event.getSeq())
                .put("ts", string(event.getTs()))
                .put("actor", event.getActor())
                .put("actorType", string(event.getActorType()))
                .put("partnerId", event.getPartnerId())
                .put("action", event.getAction())
                .put("resource", event.getResource())
                .put("outcome", string(event.getOutcome()))
                .put("reason", event.getReason())
                .put("exchangeId", event.getExchangeId())
                .put("remoteAddr", event.getRemoteAddr())
                .put("prevHash", event.getPrevHash())
                .put("hash", event.getHash());
    }

    private static String string(Object value) {
        // Instant.toString() is ISO-8601, for example 2026-01-01T10:00:00.123Z
        return value != null ? value.toString() : null;
    }
}
