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
package org.acme.rest.json.quarkus;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.rest.json.common.model.ErrorResponse;
import org.acme.rest.json.common.model.Order;
import org.acme.rest.json.common.model.OrderResponse;
import org.acme.rest.json.common.processing.ProcessedOrdersRegistry;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    private static final Logger log = LoggerFactory.getLogger(OrderResource.class);

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ProcessedOrdersRegistry registry;

    @POST
    public Response createOrder(@Valid @NotNull Order order) {
        // @NotNull and @Valid ensure order and its fields are validated before reaching here
        log.info("Received order request for customer: {}", order.getCustomerId());

        try {
            // Delegate to Camel route for processing
            OrderResponse response = producerTemplate.requestBody(
                    "direct:processOrder",
                    order,
                    OrderResponse.class);

            if (response == null) {
                log.error("Order processing returned null response for order: {}", order.getOrderId());
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(ErrorResponse.of("Order processing failed", order.getOrderId()))
                        .build();
            }

            log.info("Order processing completed: {}", response.getOrderId());
            return Response.status(Response.Status.CREATED).entity(response).build();

        } catch (Exception e) {
            log.error("Failed to process order: {}", order.getOrderId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ErrorResponse.of("An error occurred while processing your order", order.getOrderId()))
                    .build();
        }
    }

    /**
     * Check if a specific order has been processed.
     *
     * @param  orderId the order ID to check
     * @return         true if the order has been processed
     */
    @GET
    @Path("isProcessed/{orderId}")
    public boolean isProcessed(@PathParam("orderId") String orderId) {
        return registry.isProcessed(orderId);
    }
}
