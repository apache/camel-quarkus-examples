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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.specification.RequestSpecification;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Calls the server with the JDK TLS stack (SunJSSE, no Bouncy Castle), restricting the TLS named groups the client
 * offers. The certificates for mutual TLS are generated before the tests (Bouncy Castle is used for that in test
 * scope only).
 * <p>
 * Runs on JDK 25+ only, and only when it supports X25519MLKEM768 (JDK 25.0.5+, see {@link PqcSupportedCondition}).
 */
@EnabledForJreRange(min = JRE.JAVA_25)
@ExtendWith(PqcSupportedCondition.class)
@Certificates(baseDir = "target/test-certs", certificates = @Certificate(name = "pqc", password = "changeit", formats = Format.PKCS12, client = true, subjectAlternativeNames = {
        "DNS:localhost", "IP:127.0.0.1" }))
abstract class AbstractPqcTest {

    /** Hybrid post-quantum key exchange only. */
    static final String[] HYBRID_GROUPS = { "X25519MLKEM768" };
    /** Classical key exchange only, as offered by a client without post-quantum support. */
    static final String[] CLASSICAL_GROUPS = { "x25519", "secp256r1" };

    private static final char[] PASSWORD = "changeit".toCharArray();

    /**
     * @param expectSuccess whether the secure endpoint must answer, otherwise the TLS handshake must fail
     * @param namedGroups   the TLS named groups offered by the client, or {@code null} for the JDK defaults
     */
    void assertRequest(boolean expectSuccess, String[] namedGroups) throws Exception {
        if (expectSuccess) {
            request(namedGroups, true)
                    .get("/pqc/secure")
                    .then()
                    .statusCode(200)
                    .body(containsString("Secure data delivered"));
        } else {
            assertThrows(SSLException.class, () -> request(namedGroups, true).get("/pqc/secure"));
        }
    }

    /** Mutual TLS: a client without a certificate is rejected, even when it offers the hybrid group. */
    void assertRejectedWithoutClientCertificate() {
        assertThrows(SSLException.class, () -> request(HYBRID_GROUPS, false).get("/pqc/secure"));
    }

    /**
     * @param namedGroups       the TLS named groups offered by the client, or {@code null} for the JDK defaults
     * @param clientCertificate whether the client presents its certificate
     */
    private static RequestSpecification request(String[] namedGroups, boolean clientCertificate) throws Exception {
        SSLSocketFactory socketFactory = new SSLSocketFactory(createSslContext(clientCertificate),
                SSLSocketFactory.STRICT_HOSTNAME_VERIFIER) {
            @Override
            protected void prepareSocket(SSLSocket socket) {
                SSLParameters parameters = socket.getSSLParameters();
                parameters.setProtocols(new String[] { "TLSv1.3" });
                if (namedGroups != null) {
                    NamedGroups.set(parameters, namedGroups);
                }
                socket.setSSLParameters(parameters);
            }
        };
        return given()
                .config(RestAssuredConfig.config().sslConfig(SSLConfig.sslConfig().sslSocketFactory(socketFactory)))
                .baseUri("https://localhost:" + RestAssured.port);
    }

    private static SSLContext createSslContext(boolean clientCertificate) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(loadKeyStore("target/test-certs/pqc-client-keystore.p12"), PASSWORD);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(loadKeyStore("target/test-certs/pqc-client-truststore.p12"));

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(clientCertificate ? kmf.getKeyManagers() : null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    private static KeyStore loadKeyStore(String path) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            keyStore.load(in, PASSWORD);
        }
        return keyStore;
    }
}
