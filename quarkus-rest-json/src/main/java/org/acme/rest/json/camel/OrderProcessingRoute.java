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
package org.acme.rest.json.camel;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rest.json.common.model.Order;
import org.acme.rest.json.common.model.OrderResponse;
import org.acme.rest.json.common.processing.PriceCalculator;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class OrderProcessingRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Main processing route - fire and forget
        from("direct:processOrder")
                .routeId("order-processing-main")
                // Validate order is not null before processing
                .validate(body().isNotNull())
                .validate(body().isInstanceOf(Order.class))
                .log("Processing order: ${body.orderId} for customer: ${body.customerId}")

                // Calculate pricing
                .bean(PriceCalculator.class, "calculate")
                .log("Price calculated: ${body.orderId}, total: ${body.totalAmount}")

                // Save Order before sending to Kafka (body will be marshaled to JSON)
                .setProperty("originalOrder", body())

                // Send to Kafka (fire-and-forget)
                .to("direct:sendToKafka")

                // Prepare immediate response (don't wait for Kafka processing)
                .process(exchange -> {
                    Order order = exchange.getProperty("originalOrder", Order.class);
                    // After validation, this should never be null, but keeping check for safety
                    if (order == null) {
                        throw new IllegalStateException("Order property is null - cannot create response");
                    }

                    OrderResponse response = OrderResponse.success(
                            order.getOrderId(),
                            order.getTotalAmount());
                    exchange.getMessage().setBody(response);
                })
                .log("Order sent to Kafka at: ${body.timestamp}");

    }
}
