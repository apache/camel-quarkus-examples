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
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class JmsOrderProcessorRoute extends RouteBuilder {

    @Override
    public void configure() {
        // Error handling for JMS, processing, and SOAP failures
        onException(Exception.class)
                .handled(true)
                .log("Error processing order: ${exception.message}")
                .to("jms:queue:order-dead-letter");

        from("jms:queue:order-queue")
                .routeId("jms-order-processor")
                .log("Processing order from JMS queue: ${body}")
                .process("orderToInventoryProcessor")
                .log("Transformed to UpdateStockRequest - productId: ${body.productId}, quantity: ${body.quantity}")
                .to("cxf:bean:inventoryServiceClient")
                .log("SOAP response - success: ${body[0].success}, message: ${body[0].message}, newStock: ${body[0].newStock}")
                .setBody(simple("${body[0]}")) // Extract UpdateStockResponse from CXF MessageContentsList
                .marshal().json(); // Convert SOAP response to JSON for JMS reply
    }
}
