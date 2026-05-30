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
package org.acme.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.acme.inventory.UpdateStockRequest;
import org.acme.model.Order;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Named("orderToInventoryProcessor")
public class OrderToInventoryProcessor implements Processor {

    private static final Logger logger = LoggerFactory.getLogger(OrderToInventoryProcessor.class);

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        String orderJson = exchange.getIn().getBody(String.class);

        // Validate input
        if (orderJson == null || orderJson.trim().isEmpty()) {
            logger.error("Order JSON is null or empty");
            throw new IllegalArgumentException("Order JSON cannot be null or empty");
        }

        logger.debug("Processing order JSON: {}", orderJson);

        // Parse JSON with specific error handling
        Order order;
        try {
            order = objectMapper.readValue(orderJson, Order.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse order JSON: {}", orderJson, e);
            throw new IllegalArgumentException("Invalid order JSON format", e);
        }

        // Validate parsed order
        if (order == null) {
            logger.error("Parsed order is null");
            throw new IllegalArgumentException("Parsed order cannot be null");
        }

        // Validate order fields
        if (order.getProductId() == null) {
            logger.error("Order productId is null");
            throw new IllegalArgumentException("Order productId cannot be null");
        }

        if (order.getQuantity() == null) {
            logger.error("Order quantity is null");
            throw new IllegalArgumentException("Order quantity cannot be null");
        }

        logger.info("Transforming order to inventory update request - productId: {}, quantity: {}",
                order.getProductId(), order.getQuantity());

        UpdateStockRequest request = new UpdateStockRequest();
        request.setProductId(order.getProductId());
        request.setQuantity(order.getQuantity());

        exchange.getIn().setBody(request);

        logger.debug("Successfully transformed order to inventory update request");
    }
}
