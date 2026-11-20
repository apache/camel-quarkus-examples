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
package org.acme.partner.gateway.audit;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification while appends go on.
 * <ul>
 * <li>a record committed after the head was read is not checked, and the chain up to the head is valid</li>
 * </ul>
 * The race is made deterministic: the head is read first, then a record is appended, then the pages are checked.
 */
@QuarkusTest
@DisabledIfSystemProperty(named = "skip-testcontainers-tests", matches = ".*", disabledReason = "Requires containers")
class AuditVerifyTest {

    private static final Caller ACME = new Caller("service-account-partner-acme", ActorType.CLIENT, "acme", "10.0.0.1",
            "exchange-1");

    @Inject
    AuditLog auditLog;

    @Inject
    EntityManager em;

    @Test
    void appendDuringVerifyKeepsTheChainValid() {
        AuditChainHead head = QuarkusTransaction.requiringNew()
                .call(() -> em.find(AuditChainHead.class, AuditChainHead.ID));
        auditLog.append(ACME, "ORDER_ACCEPTED", "order/during-verify", Outcome.SUCCESS, null);

        Verification verification = QuarkusTransaction.requiringNew()
                .call(() -> auditLog.verifyUpTo(head.getSeq(), head.getHash()));

        assertTrue(verification.valid(), verification::toString);
        assertEquals(head.getSeq(), verification.checked());
    }
}
