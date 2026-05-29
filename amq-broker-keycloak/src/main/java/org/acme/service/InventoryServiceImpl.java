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
package org.acme.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.acme.inventory.UpdateStockRequest;
import org.acme.inventory.UpdateStockResponse;
import org.acme.model.InventoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
@Named("inventoryServiceImpl")
@RegisterForReflection(methods = true)
public class InventoryServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();

    public InventoryServiceImpl() {
        // Initialize with some sample inventory
        inventory.put("PRODUCT-001", 100);
        inventory.put("PRODUCT-002", 50);
        inventory.put("PRODUCT-003", 75);
    }

    public UpdateStockResponse updateStock(UpdateStockRequest request) {
        // Null validation for request and productId
        if (request == null || request.getProductId() == null) {
            UpdateStockResponse response = new UpdateStockResponse();
            response.setSuccess(false);
            response.setMessage("Invalid request: request or productId is null");
            response.setNewStock(0);
            LOG.warn("Invalid request: request or productId is null");
            return response;
        }

        LOG.info("Updating stock for product: {}, quantity: {}", request.getProductId(), request.getQuantity());

        UpdateStockResponse response = new UpdateStockResponse();

        // Use compute() for atomic check-and-update operation
        Integer resultStock = inventory.compute(request.getProductId(), (productId, currentStock) -> {
            // Product not found case
            if (currentStock == null) {
                return null;
            }

            // Calculate new stock
            int newStock = currentStock - request.getQuantity();

            // Insufficient stock case - return current stock unchanged
            if (newStock < 0) {
                return currentStock;
            }

            // Success case - return new stock
            return newStock;
        });

        // Handle the result from compute()
        if (resultStock == null) {
            // Product not found
            response.setSuccess(false);
            response.setMessage("Product not found: " + request.getProductId());
            response.setNewStock(0);
            LOG.warn("Product not found: {}", request.getProductId());
        } else {
            // Need to check if stock actually changed
            Integer currentStock = resultStock;
            int expectedNewStock = currentStock - request.getQuantity();

            if (expectedNewStock < 0) {
                // Insufficient stock - compute returned unchanged value
                response.setSuccess(false);
                response.setMessage("Insufficient stock for product: " + request.getProductId());
                response.setNewStock(currentStock);
                LOG.warn("Insufficient stock for product: {}, requested: {}, available: {}",
                        request.getProductId(), request.getQuantity(), currentStock);
            } else {
                // Success - stock was updated
                response.setSuccess(true);
                response.setMessage("Stock updated successfully");
                response.setNewStock(resultStock);
                LOG.info("Stock updated successfully for product: {}, new stock: {}",
                        request.getProductId(), resultStock);
            }
        }

        return response;
    }

    public InventoryItem getInventoryItem(String productId) {
        Integer stock = inventory.get(productId);
        if (stock != null) {
            return new InventoryItem(productId, stock);
        }
        return null;
    }
}
