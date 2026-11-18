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
package org.acme.partner.gateway.order;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "partner_order")
public class PartnerOrder {

    @Id
    private String id;
    @Column(name = "partner_id")
    private String partnerId;
    @Column(name = "idempotency_key")
    private String idempotencyKey;
    private String sku;
    private int quantity;
    @Column(name = "created_at")
    private Instant createdAt;

    protected PartnerOrder() {
    }

    PartnerOrder(String partnerId, String idempotencyKey, OrderRequest request) {
        this.id = UUID.randomUUID().toString();
        this.partnerId = partnerId;
        this.idempotencyKey = idempotencyKey;
        this.sku = request.sku();
        this.quantity = request.quantity();
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
