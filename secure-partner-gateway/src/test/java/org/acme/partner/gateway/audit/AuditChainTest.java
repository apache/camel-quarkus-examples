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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plain unit test of the audit hash chain, no Quarkus and no database needed.
 * <ul>
 * <li>an intact chain of five records is valid</li>
 * <li>a modified, deleted, reordered or removed newest record, and a wrong key, are detected</li>
 * </ul>
 * The result names the first broken record, so tampering can be located.
 */
class AuditChainTest {

    private static final String KEY = "unit-test-audit-hmac-key-0123456789";
    private static final Caller ACME = new Caller("service-account-partner-acme", ActorType.CLIENT, "acme", "10.0.0.1",
            "exchange-1");

    private final AuditChain chain = new AuditChain(KEY);
    private final List<AuditEvent> events = new ArrayList<>();
    private final String headHash;

    AuditChainTest() {
        String prevHash = AuditChain.GENESIS;
        for (long seq = 1; seq <= 5; seq++) {
            AuditEvent event = new AuditEvent(seq, Instant.ofEpochMilli(1_700_000_000_000L + seq), ACME,
                    "ORDER_ACCEPTED", "order/" + seq, Outcome.SUCCESS, null);
            chain.link(event, prevHash);
            prevHash = event.getHash();
            events.add(event);
        }
        headHash = prevHash;
    }

    @Test
    void intactChainIsValid() {
        assertEquals(new Verification(true, 5, null), verify(chain));
    }

    @Test
    void modifiedRecordIsDetected() {
        AuditEvent original = events.get(2);
        AuditEvent modified = new AuditEvent(original.getSeq(), original.getTs(), ACME, original.getAction(),
                "order/forged", original.getOutcome(), original.getReason());
        modified.link(original.getPrevHash(), original.getHash());
        events.set(2, modified);

        assertEquals(new Verification(false, 2, 3L), verify(chain));
    }

    @Test
    void deletedRecordIsDetected() {
        events.remove(2);

        assertEquals(new Verification(false, 2, 3L), verify(chain));
    }

    @Test
    void reorderedRecordsAreDetected() {
        Collections.swap(events, 1, 2);

        assertEquals(new Verification(false, 1, 2L), verify(chain));
    }

    @Test
    void removedNewestRecordIsDetected() {
        events.remove(4);

        assertEquals(new Verification(false, 4, 5L), verify(chain));
    }

    @Test
    void chainCanBeVerifiedPageByPage() {
        assertEquals(new Verification(true, 5, null), verifyInPagesOfTwo());

        // a gap at the start of the second page
        events.remove(2);
        assertEquals(new Verification(false, 2, 3L), verifyInPagesOfTwo());
    }

    @Test
    void wrongKeyIsDetected() {
        assertEquals(new Verification(false, 0, 1L), verify(new AuditChain("another-audit-hmac-key-0123456789")));
    }

    private Verification verifyInPagesOfTwo() {
        AuditChain.Verifier verifier = chain.verifier();
        for (int from = 0; from < events.size(); from += 2) {
            verifier.check(events.subList(from, Math.min(from + 2, events.size())));
        }
        return verifier.result(5, headHash);
    }

    // The chain head keeps naming the last record that was appended
    private Verification verify(AuditChain verifier) {
        return verifier.verify(events, 5, headHash);
    }
}
