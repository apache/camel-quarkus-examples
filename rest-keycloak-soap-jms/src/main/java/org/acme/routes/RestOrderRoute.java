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
import org.acme.inventory.UpdateStockRequest;
import org.acme.inventory.UpdateStockResponse;
import org.acme.model.Order;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;

@ApplicationScoped
public class RestOrderRoute extends RouteBuilder {

    @Override
    public void configure() {
        // REST endpoint: secured by Quarkus OIDC
        rest("/api/orders")
                .post("/submit")
                .consumes("application/json")
                .produces("application/json")
                .bindingMode(RestBindingMode.json)
                .type(Order.class)
                .to("direct:process-order");

        // Main route: REST → SOAP (synchronous) + async event notification
        from("direct:process-order")
                .routeId("rest-to-soap-bridge")
                .log("Received order: ${body}")
                // Async: Send event to JMS for audit/notification (fire-and-forget)
                .wireTap("direct:order-events")
                // Sync: Convert Order to SOAP UpdateStockRequest
                .process(exchange -> {
                    Order order = exchange.getIn().getBody(Order.class);
                    UpdateStockRequest soapRequest = new UpdateStockRequest();
                    soapRequest.setProductId(order.getProductId());
                    soapRequest.setQuantity(order.getQuantity());
                    exchange.getIn().setBody(soapRequest);
                })
                // Sync: Call SOAP service and wait for response
                .to("cxf:bean:inventoryServiceClient")
                // Return SOAP response to REST client
                .process(exchange -> {
                    UpdateStockResponse soapResponse = exchange.getIn().getBody(UpdateStockResponse.class);
                    String jsonResponse = String.format(
                            "{\"success\":%b,\"message\":\"%s\",\"newStock\":%d}",
                            soapResponse.isSuccess(),
                            soapResponse.getMessage(),
                            soapResponse.getNewStock());
                    exchange.getIn().setBody(jsonResponse);
                    exchange.getIn().setHeader("Content-Type", "application/json");
                });

        // Async event publisher: send to JMS topic
        from("direct:order-events")
                .routeId("order-event-publisher")
                .marshal().json()
                .to("jms:topic:order-events")
                .log("Published order event to JMS topic");
    }
}
