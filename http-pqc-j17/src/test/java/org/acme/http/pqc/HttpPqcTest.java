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

import java.io.IOException;
import java.nio.file.Path;
import java.security.Security;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class HttpPqcTest {

    private static final Logger log = LoggerFactory.getLogger(HttpPqcTest.class);
    private static final String PASSWORD = "changeit";
    private static final String CERT_DIR = "target/certs";

    @BeforeAll
    public static void setUpClass() {
        // Needed to build the deliberately invalid certificates used by the rejection tests
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
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
        assertRejected(withTrustStore(SSLConfig.sslConfig()), "no client certificate");
    }

    @Test
    public void testPqcSecureEndpointWithValidClientCert() {
        // Test /pqc/secure WITH valid hybrid client certificate. It chains to the CA in the server
        // truststore and carries the CA's ML-DSA-65 alternative signature, so the handshake succeeds.
        client(CERT_DIR + "/client-hybrid-keystore.p12")
                .when()
                .get("/pqc/secure")
                .then()
                .statusCode(200)
                .body(containsString("Hybrid PQC certificate validated"))
                .body(containsString("trust anchor"))
                .body(containsString("ML-DSA-65"));
    }

    @Test
    public void testPqcSecureEndpointWithRsaOnlyCertificate() {
        // Test /pqc/secure WITH RSA-only client certificate (no PQC extensions). It is issued by the
        // same CA, so it passes chain validation and the ML-DSA-65 check is the only thing rejecting it.
        assertRejected(CERT_DIR + "/client-rsa-only-keystore.p12", "RSA-only cert issued by the CA");
    }

    @Test
    public void testPqcSecureEndpointWithUntrustedHybridCertificate() throws Exception {
        // A self-signed hybrid certificate carrying a well-formed ML-DSA-65 alternative signature over
        // its own body, but issued by nobody the server trusts. Rejected because the PQC check adds to
        // the standard chain validation rather than replacing it. This is the case that a custom
        // TrustManager doing its own validation instead of delegating will wrongly accept.
        Path keystore = ForgedCertificates.keystore("rogue", ForgedCertificates.rogueHybrid());
        assertRejected(keystore.toString(), "untrusted self-signed hybrid cert");
    }

    @Test
    public void testPqcSecureEndpointWithExpiredHybridCertificate() throws Exception {
        // A hybrid certificate whose validity period ended a year ago, which the validity check picks
        // up. It is self-signed too, so it fails for want of a trust anchor as well; both checks only
        // run because validation is delegated to the platform trust manager.
        Path keystore = ForgedCertificates.keystore("expired", ForgedCertificates.expiredHybrid());
        assertRejected(keystore.toString(), "expired hybrid cert");
    }

    private void assertRejected(String keyStorePath, String description) {
        assertRejected(withTrustStore(SSLConfig.sslConfig()).keyStore(keyStorePath, PASSWORD), description);
    }

    private void assertRejected(SSLConfig sslConfig, String description) {
        Exception e = assertThrows(Exception.class,
                () -> given(sslConfig).when().get("/pqc/secure").then().statusCode(200),
                "Expected the TLS handshake to be rejected for " + description);

        // TLS-level rejections surface as an IOException subclass, most often SSLHandshakeException.
        // Asserting that keeps the test from passing because of an unrelated failure.
        assertInstanceOf(IOException.class, rootCause(e),
                "Expected a TLS failure for " + description + " but got: " + e);
        log.info("✓ Rejected as expected ({}): {}", description, e.getMessage());
    }

    /**
     * Trusts the server via the generated client truststore, and verifies its identity against the
     * subject alternative names in the server certificate. Hostname verification is deliberately left
     * enabled so that the tests fail if those names regress.
     */
    private static SSLConfig withTrustStore(SSLConfig sslConfig) {
        return sslConfig.trustStore(CERT_DIR + "/client-hybrid-truststore.p12", PASSWORD);
    }

    private static RequestSpecification client(String keyStorePath) {
        return given(withTrustStore(SSLConfig.sslConfig()).keyStore(keyStorePath, PASSWORD));
    }

    private static RequestSpecification given(SSLConfig sslConfig) {
        return RestAssured.given().config(RestAssuredConfig.config().sslConfig(sslConfig));
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
