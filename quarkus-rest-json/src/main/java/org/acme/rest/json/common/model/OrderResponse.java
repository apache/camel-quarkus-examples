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
package org.acme.rest.json.common.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class OrderResponse {

    private String orderId;
    private boolean confirmed;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp;

    public OrderResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public OrderResponse(String orderId, boolean confirmed) {
        this.orderId = orderId;
        this.confirmed = confirmed;
        this.timestamp = LocalDateTime.now();
    }

    public static OrderResponse success(String orderId, BigDecimal totalAmount) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(orderId);
        response.setConfirmed(true);
        response.setTotalAmount(totalAmount);
        return response;
    }

    public static OrderResponse error(String orderId) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(orderId);
        response.setConfirmed(false);
        return response;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "OrderResponse{" +
                "orderId='" + orderId + '\'' +
                ", confirmed=" + confirmed +
                ", totalAmount=" + totalAmount +
                ", timestamp=" + timestamp +
                '}';
    }
}
