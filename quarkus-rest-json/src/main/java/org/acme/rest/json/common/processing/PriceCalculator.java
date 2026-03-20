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

import java.math.BigDecimal;
import java.math.RoundingMode;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rest.json.common.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Calculates order pricing based on customer ID.
 *
 * Customer tiers:
 * - CUST001: GOLD (10% discount)
 * - CUST004: PLATINUM (15% discount)
 * - Others: STANDARD (no discount)
 */
@RegisterForReflection
@ApplicationScoped
public class PriceCalculator {

    private static final Logger log = LoggerFactory.getLogger(PriceCalculator.class);
    private static final BigDecimal GOLD_DISCOUNT = new BigDecimal("0.10"); // 10%
    private static final BigDecimal PLATINUM_DISCOUNT = new BigDecimal("0.15"); // 15%

    public void calculate(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        log.info("Calculating price for order: {}", order.getOrderId());

        // Calculate subtotal from items
        BigDecimal subtotal = order.calculateTotal();

        // Apply customer discount based on customer ID
        BigDecimal discount = getDiscountForCustomer(order.getCustomerId());
        BigDecimal discountAmount = subtotal.multiply(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discountAmount);

        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Applied {}% discount: {}", discount.multiply(new BigDecimal("100")), discountAmount);
        }

        order.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));

        log.info("Final total for order {}: {}", order.getOrderId(), order.getTotalAmount());
    }

    private BigDecimal getDiscountForCustomer(String customerId) {
        if (customerId == null) {
            return BigDecimal.ZERO;
        }
        switch (customerId) {
        case "CUST004":
            return PLATINUM_DISCOUNT;
        case "CUST001":
            return GOLD_DISCOUNT;
        default:
            return BigDecimal.ZERO;
        }
    }
}
