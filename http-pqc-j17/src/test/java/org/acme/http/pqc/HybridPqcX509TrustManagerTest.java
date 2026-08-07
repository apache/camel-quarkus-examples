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

import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.acme.http.pqc.certificates.HybridCertificateGenerator;
import org.acme.http.pqc.certificates.HybridCertificateGenerator.CertificateData;
import org.acme.http.pqc.trustmanager.HybridPqcX509TrustManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link HybridPqcX509TrustManager} implements the whole {@link X509TrustManager}
 * contract rather than leaving parts of it permissive. An empty {@code checkServerTrusted} or an
 * empty {@code getAcceptedIssuers} compiles and passes any happy-path test, so each is asserted here
 * explicitly.
 */
public class HybridPqcX509TrustManagerTest {

    private static CertificateData ca;
    private static X509Certificate leaf;
    private static HybridPqcX509TrustManager trustManager;

    @BeforeAll
    public static void setUpClass() throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }

        ca = HybridCertificateGenerator.generateCertificateAuthority();
        leaf = HybridCertificateGenerator.issueCertificate(ca, "trusted-leaf", true, null).certificate;

        // A truststore holding the CA, which is the trust anchor
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("anchor", ca.certificate);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        X509TrustManager platform = null;
        for (TrustManager candidate : tmf.getTrustManagers()) {
            if (candidate instanceof X509TrustManager) {
                platform = (X509TrustManager) candidate;
                break;
            }
        }
        trustManager = new HybridPqcX509TrustManager(platform);
    }

    /**
     * A peer that sends only its own certificate, leaving the relying party to supply the anchor. The
     * issuer's ML-DSA-65 key has to be found in the truststore for this to pass.
     */
    @Test
    public void leafAloneIsAccepted() {
        assertDoesNotThrow(() -> trustManager.checkClientTrusted(new X509Certificate[] { leaf }, "RSA"));
    }

    @Test
    public void leafWithCaInChainIsAccepted() {
        assertDoesNotThrow(
                () -> trustManager.checkClientTrusted(new X509Certificate[] { leaf, ca.certificate }, "RSA"));
    }

    @Test
    public void untrustedHybridClientIsRejected() throws Exception {
        X509Certificate rogue = ForgedCertificates.rogueHybrid().certificate;
        assertThrows(CertificateException.class,
                () -> trustManager.checkClientTrusted(new X509Certificate[] { rogue }, "RSA"));
    }

    /**
     * Issued by the trusted CA, so it passes chain validation, but carries no ML-DSA-65 signature. This
     * isolates the post-quantum check as the sole reason for rejection.
     */
    @Test
    public void certificateFromTheCaWithoutAltSignatureIsRejected() throws Exception {
        X509Certificate rsaOnly = HybridCertificateGenerator
                .issueCertificate(ca, "rsa-only-leaf", false, null).certificate;

        CertificateException e = assertThrows(CertificateException.class,
                () -> trustManager.checkClientTrusted(new X509Certificate[] { rsaOnly }, "RSA"));
        assertEquals(true, e.getMessage().contains("2.5.29.73"), "Unexpected message: " + e.getMessage());
    }

    @Test
    public void untrustedHybridServerIsRejected() throws Exception {
        X509Certificate rogue = ForgedCertificates.rogueHybrid().certificate;
        assertThrows(CertificateException.class,
                () -> trustManager.checkServerTrusted(new X509Certificate[] { rogue }, "RSA"),
                "checkServerTrusted must validate the chain, not accept anything");
    }

    @Test
    public void emptyChainIsRejected() {
        assertThrows(CertificateException.class, () -> trustManager.checkClientTrusted(null, "RSA"));
        assertThrows(CertificateException.class,
                () -> trustManager.checkClientTrusted(new X509Certificate[0], "RSA"));
    }

    @Test
    public void acceptedIssuersComeFromTheTruststore() {
        X509Certificate[] issuers = trustManager.getAcceptedIssuers();
        assertEquals(1, issuers.length, "Expected the configured trust anchor to be advertised");
        assertEquals(ca.certificate, issuers[0]);
    }
}
