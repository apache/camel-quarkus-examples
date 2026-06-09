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
package org.acme.routes;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.model.Order;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class JmsOrderProcessorRoute extends RouteBuilder {

    @Override
    public void configure() {
        // JMS Consumer 1: Audit logging
        // Uses durable subscription to survive application restarts
        from("jms:topic:order-events?clientId=audit-consumer&durableSubscriptionName=audit")
                .routeId("order-audit-logger")
                .log("AUDIT: Order event received - ${body}")
                .to("log:org.acme.audit?level=INFO&showHeaders=false");

        // JMS Consumer 2: Email notification (simulated)
        // Demonstrates how to add independent consumers without changing main flow
        from("jms:topic:order-events?clientId=email-consumer&durableSubscriptionName=email")
                .routeId("order-email-notification")
                .unmarshal().json(Order.class)
                .process(exchange -> {
                    Order order = exchange.getIn().getBody(Order.class);
                    String emailBody = String.format(
                            "Order Notification:\n- Product: %s\n- Quantity: %d\n- Status: Processing",
                            order.getProductId(), order.getQuantity());
                    exchange.getIn().setBody(emailBody);
                })
                .to("log:org.acme.notification.email?level=INFO")
                .log("EMAIL: Notification sent for product ${header.productId}");

        // JMS Consumer 3: Cache invalidation (simulated)
        // Shows pattern for updating cache when inventory changes
        from("jms:topic:order-events?clientId=cache-consumer&durableSubscriptionName=cache")
                .routeId("order-cache-invalidation")
                .unmarshal().json(Order.class)
                .process(exchange -> {
                    Order order = exchange.getIn().getBody(Order.class);
                    // this product will be removed/invalidated from cache in real scenario (cache-aside)
                    String cacheKey = "inventory:" + order.getProductId();
                    exchange.getIn().setHeader("cacheKey", cacheKey);
                })
                .to("log:org.acme.cache?level=INFO")
                .log("CACHE: Invalidated cache for key ${header.cacheKey}");
    }
}
