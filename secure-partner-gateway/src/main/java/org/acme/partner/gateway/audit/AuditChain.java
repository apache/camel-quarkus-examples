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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Tamper evidence for the audit trail: {@code hash(n) = HMAC-SHA256(key, canonical(record n) || hash(n-1))}.
 * Without the key, records cannot be altered, removed or reordered without breaking the chain, except at its end:
 * deleting the newest records and rewinding {@code audit_chain_head} keeps the chain valid. Only external anchors
 * reveal that.
 */
final class AuditChain {

    static final String GENESIS = "0".repeat(64);

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    AuditChain(String key) {
        this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    void link(AuditEvent event, String prevHash) {
        event.link(prevHash, hash(event, prevHash));
    }

    /**
     * Checks events ordered by seq: no gap from seq 1, each record points to the hash of the previous one, each hash
     * matches the record, and the last record is the chain head.
     */
    Verification verify(List<AuditEvent> events, long headSeq, String headHash) {
        long expectedSeq = 1;
        String prevHash = GENESIS;
        for (AuditEvent event : events) {
            if (event.getSeq() != expectedSeq
                    || !prevHash.equals(event.getPrevHash())
                    || !hash(event, prevHash).equals(event.getHash())) {
                return Verification.brokenAt(expectedSeq);
            }
            prevHash = event.getHash();
            expectedSeq++;
        }
        // records cut from the end leave a consistent chain, the head reveals them unless it was rewound as well
        if (expectedSeq - 1 != headSeq || !prevHash.equals(headHash)) {
            return Verification.brokenAt(expectedSeq);
        }
        return Verification.valid(headSeq);
    }

    private String hash(AuditEvent event, String prevHash) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            update(mac, String.valueOf(event.getSeq()));
            update(mac, String.valueOf(event.getTs().toEpochMilli()));
            update(mac, event.getActor());
            update(mac, event.getActorType().name());
            update(mac, event.getPartnerId());
            update(mac, event.getAction());
            update(mac, event.getResource());
            update(mac, event.getOutcome().name());
            update(mac, event.getReason());
            update(mac, event.getExchangeId());
            update(mac, event.getRemoteAddr());
            update(mac, prevHash);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // Length-prefixed fields, so that moving characters from one field to another changes the hash
    private static void update(Mac mac, String value) {
        if (value == null) {
            mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        mac.update(bytes);
    }
}
