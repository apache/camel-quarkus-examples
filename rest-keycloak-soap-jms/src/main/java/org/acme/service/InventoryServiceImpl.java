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
        if (request == null || request.getProductId() == null) {
            UpdateStockResponse response = new UpdateStockResponse();
            response.setSuccess(false);
            response.setMessage("Invalid request");
            response.setNewStock(0);
            return response;
        }

        LOG.info("SOAP: Updating stock for product: {}, quantity: {}", request.getProductId(), request.getQuantity());

        Integer newStock = inventory.compute(request.getProductId(), (productId, currentStock) -> {
            if (currentStock == null || currentStock - request.getQuantity() < 0) {
                return currentStock;
            }
            return currentStock - request.getQuantity();
        });

        UpdateStockResponse response = new UpdateStockResponse();
        if (newStock != null) {
            response.setSuccess(true);
            response.setMessage("Stock updated successfully");
            response.setNewStock(newStock);
        } else {
            response.setSuccess(false);
            response.setMessage("Product not found or insufficient stock");
            response.setNewStock(0);
        }
        return response;
    }
}
