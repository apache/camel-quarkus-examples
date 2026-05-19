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
import org.acme.http.pqc.profiles.PqcWithFallbackProfile;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Test Apache HTTP Client with PQC+fallback configuration.
 *
 * Expected results:
 * - BCJSSE: SUCCESS (supports X25519MLKEM768 and fallback)
 * - SunJSSE: SUCCESS (ignores X25519MLKEM768, uses secp256r1 fallback)
 *
 * Note: @Order(1) ensures this test runs BEFORE PqcOnlyTest in JVM mode.
 * This test must run first because SunJSSE can successfully initialize with the fallback
 * configuration. If the PQC-only test runs first, SunJSSE's static initialization fails
 * permanently and cannot be recovered.
 *
 * In native mode, this test also runs first but against a dedicated native binary
 * built with fallback configuration
 */
@QuarkusTest
@TestProfile(PqcWithFallbackProfile.class)
@Order(1)
class PqcWithFallbackTest extends AbstractPqcTest {

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
        testHttpClientConnection("SunJSSE", false);
    }
}
