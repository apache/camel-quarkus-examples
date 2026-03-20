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
import jakarta.inject.Inject;
import org.acme.rest.json.common.model.Order;
import org.acme.rest.json.common.processing.ProcessedOrdersRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class OrderMessagingRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(OrderMessagingRoute.class);

    @Inject
    ProcessedOrdersRegistry processedOrdersRegistry;

    @Override
    public void configure() throws Exception {

        // Producer: Send order to Kafka with error handling
        from("direct:sendToKafka")
                .routeId("send-to-kafka")
                .onException(Exception.class)
                .log("ERROR: Failed to send message to Kafka: ${exception.message}")
                .handled(true)
                .end()
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .marshal().json(JsonLibrary.Jackson)
                .to("kafka:{{kafka.topic.orders.requests}}")
                .log("Message sent to Kafka topic! Order ID: ${header." + KafkaConstants.KEY + "}");

        // Consumer: Process orders from Kafka with error handling
        from("kafka:{{kafka.topic.orders.requests}}"
                + "?groupId={{kafka.consumer.group.id}}"
                + "&autoOffsetReset=earliest"
                + "&keyDeserializer=org.apache.kafka.common.serialization.StringDeserializer"
                + "&valueDeserializer=org.apache.kafka.common.serialization.StringDeserializer")
                .routeId("kafka-order-consumer")
                .onException(Exception.class)
                .log("ERROR: Failed to process Kafka message: ${exception.message}")
                .handled(true)
                .end()
                .log("Received from Kafka: \"${body}\"")
                .unmarshal().json(JsonLibrary.Jackson, Order.class)
                .delay(1000) // Simulate processing time
                .process(exchange -> {
                    Order order = exchange.getIn().getBody(Order.class);
                    if (order == null) {
                        log.error("Received null order from Kafka");
                        return;
                    }
                    log.info("✓ Order {} was processed via Kafka consumer for customer: {}",
                            order.getOrderId(), order.getCustomerId());

                    // Register order as processed (for testing verification)
                    processedOrdersRegistry.registerProcessed(order.getOrderId());
                });
    }
}
