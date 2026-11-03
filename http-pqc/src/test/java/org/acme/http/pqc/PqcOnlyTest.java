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
package org.acme.http.pqc;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.acme.http.pqc.profiles.PqcOnlyProfile;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Test with explicit JSSE provider selection on PQC-only server.
 *
 * Expected results:
 * - BCJSSE: SUCCESS (supports X25519MLKEM768)
 * - SunJSSE restricted to classical named groups: FAILURE (no key exchange group in common with the server)
 *
 * This proves that the server rejects clients that do not offer X25519MLKEM768. The client is restricted explicitly
 * because SunJSSE supports X25519MLKEM768 on recent JDK builds (e.g. 25.0.5+).
 *
 * Note: @Order(2) ensures this test runs AFTER PqcWithFallbackTest.
 * On JDK builds where SunJSSE does not support X25519MLKEM768, its static initialization fails with PQC-only config
 * and permanently marks the class as failed. The fallback test must run first to validate SunJSSE works with
 * classical algorithms.
 *
 * This test doesn't have a native *IT child, because the native executable can be build only with 1 configuration.
 */
@QuarkusTest
@TestProfile(PqcOnlyProfile.class)
@Order(2)
class PqcOnlyTest extends AbstractPqcTest {

    @Test
    void testRestAssured() throws Exception {
        testRestAssuredConnection();
    }

    @Test
    void testHttpClientWithBCJSSE() throws Exception {
        testHttpClientConnection("BCJSSE", false);
    }

    @Test
    void testHttpClientWithSunJSSE() throws Exception {
        testHttpClientConnection("SunJSSE", true, "x25519", "secp256r1");
    }
}
