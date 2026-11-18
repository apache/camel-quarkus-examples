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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.acme.partner.gateway.audit.AuditLog;
import org.acme.partner.gateway.audit.Caller;
import org.acme.partner.gateway.audit.Outcome;

/**
 * Each method writes its audit record in the same transaction as the business change.
 */
@ApplicationScoped
public class OrderService {

    @Inject
    EntityManager em;

    @Inject
    AuditLog auditLog;

    @Transactional
    public OrderAccepted accept(Caller caller, String idempotencyKey, OrderRequest request) {
        PartnerOrder order = new PartnerOrder(caller.partnerId(), idempotencyKey, request);
        em.persist(order);
        auditLog.append(caller, "ORDER_ACCEPTED", "order/" + order.getId(), Outcome.SUCCESS, null);
        return new OrderAccepted(order.getId());
    }

    /**
     * Answers a retried request with the order created by the first one.
     */
    @Transactional
    public OrderAccepted replay(Caller caller, String idempotencyKey) {
        PartnerOrder order = em
                .createQuery("SELECT o FROM PartnerOrder o WHERE o.partnerId = :partnerId AND o.idempotencyKey = :key",
                        PartnerOrder.class)
                .setParameter("partnerId", caller.partnerId())
                .setParameter("key", idempotencyKey)
                .getResultList().stream().findFirst()
                .orElseThrow(OrderInProgressException::new);
        auditLog.append(caller, "ORDER_REPLAYED", "order/" + order.getId(), Outcome.SUCCESS, null);
        return new OrderAccepted(order.getId());
    }

    /**
     * Returns the order to the partner that owns it, and to nobody else.
     */
    @Transactional
    public OrderView read(Caller caller, String orderId) {
        PartnerOrder order = em.find(PartnerOrder.class, orderId);
        if (order == null) {
            throw new OrderNotOwnedException(orderId, "unknown order");
        }
        if (!order.getPartnerId().equals(caller.partnerId())) {
            throw new OrderNotOwnedException(orderId, "order of another partner");
        }
        auditLog.append(caller, "ORDER_READ", "order/" + orderId, Outcome.SUCCESS, null);
        return new OrderView(order.getId(), order.getSku(), order.getQuantity(), order.getCreatedAt());
    }
}
