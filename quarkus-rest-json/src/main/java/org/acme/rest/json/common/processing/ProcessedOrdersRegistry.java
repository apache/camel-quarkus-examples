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
package org.acme.rest.json.common.processing;

import java.util.concurrent.CopyOnWriteArraySet;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Thread-safe registry of processed order IDs.
 * Used for testing to verify that orders are processed via Kafka.
 */
@ApplicationScoped
@RegisterForReflection
public class ProcessedOrdersRegistry {

    private final CopyOnWriteArraySet<String> processedOrderIds = new CopyOnWriteArraySet<>();

    /**
     * Registers an order as processed.
     *
     * @param orderId the order ID
     */
    public void registerProcessed(String orderId) {
        processedOrderIds.add(orderId);
    }

    /**
     * Checks if an order has been processed.
     *
     * @param  orderId the order ID
     * @return         true if the order has been processed
     */
    public boolean isProcessed(String orderId) {
        return processedOrderIds.contains(orderId);
    }

}
