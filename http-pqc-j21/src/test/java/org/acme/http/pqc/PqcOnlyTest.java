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
 * Test Apache HTTP Client with explicit provider selection on PQC-only server.
 *
 * Expected results:
 * - BCJSSE: SUCCESS (supports X25519MLKEM768)
 * - SunJSSE: FAILURE (does not support X25519MLKEM768)
 *
 * This proves that X25519MLKEM768 requires BouncyCastle JSSE.
 *
 * Note: @Order(2) ensures this test runs AFTER PqcWithFallbackTest.
 * SunJSSE's static initialization fails with PQC-only config and permanently marks the
 * class as failed. The fallback test must run first to validate SunJSSE works with classical algorithms.
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
        testHttpClientConnection("SunJSSE", true);
    }
}
