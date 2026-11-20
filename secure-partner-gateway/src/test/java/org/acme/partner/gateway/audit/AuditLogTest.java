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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plain unit test of the HMAC key check that runs at startup, no Quarkus needed.
 * <ul>
 * <li>short, repeated or well-known keys stop the startup</li>
 * <li>random base64 and hex keys are accepted</li>
 * </ul>
 * A weak key would make the audit chain easy to forge.
 */
class AuditLogTest {

    @Test
    void weakKeysStopTheStartup() {
        for (String key : new String[] { "too-short-audit-hmac-key", "a".repeat(32), "changeme".repeat(4) }) {
            assertThrows(IllegalStateException.class, () -> init(key), key);
        }
    }

    @Test
    void randomKeysAreAccepted() {
        String base64 = "9rUQWyJt2WQ/cpN6FmPbIDeL1du0ffoyLvMlBCH+x+4="; // openssl rand -base64 32
        String hex = "28439445e522128d5dd5a6c061decb84"; // openssl rand -hex 16, the shortest hex key
        for (String key : new String[] { base64, hex }) {
            assertDoesNotThrow(() -> init(key), key);
        }
    }

    private static void init(String key) {
        AuditLog auditLog = new AuditLog();
        auditLog.hmacKey = key;
        auditLog.init();
    }
}
