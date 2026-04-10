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
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
public class HttpPqcTest {

    private static final Logger log = LoggerFactory.getLogger(HttpPqcTest.class);

    @BeforeAll
    public static void setUpClass() {
        // Use relaxed HTTPS validation for self-signed certificates in tests
        RestAssured.useRelaxedHTTPSValidation();
    }

    @BeforeEach
    public void setUp() {
        // Configure base URI and port for each test
        // Read the port dynamically from config - works in both JVM and native tests
        int sslPort = ConfigProvider.getConfig()
                .getValue("quarkus.http.test-ssl-port", Integer.class);
        RestAssured.baseURI = "https://localhost";
        RestAssured.port = sslPort;
    }

    @Test
    public void testPqcSecureEndpointWithoutClientCert() {
        // With client-auth=required, TLS handshake fails before reaching the route
        try {
            RestAssured.given()
                    .when()
                    .get("/pqc/secure")
                    .then()
                    .statusCode(200);

            fail("Expected SSL exception but connection succeeded");
        } catch (Exception e) {
            // Expected: SSL handshake failure (certificate_required)
            log.info("✓ Expected SSL exception without client cert: {}", e.getMessage());
        }
    }

    @Test
    public void testPqcSecureEndpointWithValidClientCert() {
        // Test /pqc/secure WITH valid hybrid client certificate
        // TLS handshake should succeed, and custom TrustManager validates both RSA + ML-DSA-65
        // Configure RestAssured with client certificate keystore and server truststore

        RestAssured.given()
                .config(RestAssuredConfig.config().sslConfig(
                        SSLConfig.sslConfig()
                                .keyStore("target/certs/client-hybrid-keystore.p12", "changeit")
                                .trustStore("target/certs/server-hybrid-truststore.p12", "changeit")
                                .allowAllHostnames() // Accept localhost with self-signed cert
                ))
                .when()
                .get("/pqc/secure")
                .then()
                .statusCode(200)
                .body(containsString("Hybrid PQC certificate validated"))
                .body(containsString("quantum-safe"))
                .body(containsString("TLS layer"));
    }

    @Test
    public void testPqcSecureEndpointWithRsaOnlyCertificate() {
        // Test /pqc/secure WITH RSA-only client certificate (no PQC extensions)
        // TLS handshake should FAIL because custom TrustManager requires hybrid cert
        try {
            RestAssured.given()
                    .config(RestAssuredConfig.config().sslConfig(
                            SSLConfig.sslConfig()
                                    .keyStore("target/certs/client-rsa-only-keystore.p12", "changeit")
                                    .trustStore("target/certs/server-hybrid-truststore.p12", "changeit")
                                    .allowAllHostnames()))
                    .when()
                    .get("/pqc/secure")
                    .then()
                    .statusCode(200); // Should NOT reach here

            fail("Expected SSL exception for RSA-only certificate, but connection succeeded");
        } catch (Exception e) {
            // Expected: TLS handshake failure due to missing PQC extensions
            log.info("✓ Expected SSL exception for RSA-only cert: {}", e.getMessage());
        }
    }
}
