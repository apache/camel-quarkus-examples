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

import java.net.Socket;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

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
 * Verifies that {@link HybridPqcX509TrustManager} implements the whole
 * {@link X509ExtendedTrustManager} contract rather than leaving parts of it permissive. An empty
 * {@code checkServerTrusted} or an empty {@code getAcceptedIssuers} compiles and passes any
 * happy-path test, so each is asserted here explicitly.
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
        trustManager = trustManagerAnchoredOn(ca.certificate);
    }

    /**
     * Builds the trust manager under test on top of a platform one seeded with the given trust anchors,
     * which is the arrangement {@code HybridPqcTrustManagerCustomizer} sets up at runtime.
     */
    private static HybridPqcX509TrustManager trustManagerAnchoredOn(X509Certificate... anchors) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        for (int i = 0; i < anchors.length; i++) {
            trustStore.setCertificateEntry("anchor-" + i, anchors[i]);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        for (TrustManager candidate : tmf.getTrustManagers()) {
            if (candidate instanceof X509ExtendedTrustManager) {
                return new HybridPqcX509TrustManager((X509ExtendedTrustManager) candidate);
            }
        }
        throw new IllegalStateException("No X509ExtendedTrustManager available");
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

    /**
     * The {@code Socket} and {@code SSLEngine} overloads are the ones a TLS stack actually calls, so
     * they have to run the same two checks as the two-argument methods.
     */
    @Test
    public void extendedOverloadsValidateToo() throws Exception {
        X509Certificate rogue = ForgedCertificates.rogueHybrid().certificate;

        assertDoesNotThrow(() -> trustManager.checkClientTrusted(new X509Certificate[] { leaf }, "RSA", (Socket) null));
        assertDoesNotThrow(
                () -> trustManager.checkClientTrusted(new X509Certificate[] { leaf }, "RSA", (SSLEngine) null));

        assertThrows(CertificateException.class,
                () -> trustManager.checkClientTrusted(new X509Certificate[] { rogue }, "RSA", (Socket) null));
        assertThrows(CertificateException.class,
                () -> trustManager.checkServerTrusted(new X509Certificate[] { rogue }, "RSA", (SSLEngine) null));
    }

    /**
     * The connection context has to reach the delegate, because that is what the platform uses for
     * endpoint identification. Forwarding the three-argument overloads to the two-argument method
     * validates the certificate just as well and would satisfy the test above, while dropping the
     * hostname check on the way through.
     */
    @Test
    public void connectionContextIsHandedToTheDelegate() throws Exception {
        RecordingTrustManager recorder = new RecordingTrustManager(ca.certificate);
        HybridPqcX509TrustManager wrapper = new HybridPqcX509TrustManager(recorder);
        X509Certificate[] chain = new X509Certificate[] { leaf };

        wrapper.checkServerTrusted(chain, "RSA", (SSLEngine) null);
        assertEquals("checkServerTrusted/SSLEngine", recorder.lastCall);

        wrapper.checkServerTrusted(chain, "RSA", (Socket) null);
        assertEquals("checkServerTrusted/Socket", recorder.lastCall);

        wrapper.checkClientTrusted(chain, "RSA", (SSLEngine) null);
        assertEquals("checkClientTrusted/SSLEngine", recorder.lastCall);

        wrapper.checkClientTrusted(chain, "RSA", (Socket) null);
        assertEquals("checkClientTrusted/Socket", recorder.lastCall);
    }

    /**
     * Two CAs sharing a distinguished name, which is what a key rollover looks like to a relying party
     * holding both. Resolving the issuer by DN alone picks whichever anchor happens to come first, so
     * one of these two certificates would fail its ML-DSA-65 check despite the platform trust manager
     * having just accepted its chain.
     *
     * <p>
     * Both chain shapes are exercised. The CA is the interesting one: it carries no authority key
     * identifier of its own, being self-signed, so matching it needs more than the key identifiers the
     * leaf supplies — and the keystores this example generates do put the CA in the chain.
     */
    @Test
    public void certificatesFromTwoCasSharingADnAreBothAccepted() throws Exception {
        CertificateData firstCa = HybridCertificateGenerator.generateCertificateAuthority();
        CertificateData secondCa = HybridCertificateGenerator.generateCertificateAuthority();
        assertEquals(firstCa.certificate.getSubjectX500Principal(), secondCa.certificate.getSubjectX500Principal(),
                "The two CAs are only interesting to this test if they share a DN");

        X509Certificate firstLeaf = HybridCertificateGenerator
                .issueCertificate(firstCa, "rollover-leaf-1", true, null).certificate;
        X509Certificate secondLeaf = HybridCertificateGenerator
                .issueCertificate(secondCa, "rollover-leaf-2", true, null).certificate;

        HybridPqcX509TrustManager bothAnchors = trustManagerAnchoredOn(firstCa.certificate, secondCa.certificate);

        assertDoesNotThrow(() -> bothAnchors.checkClientTrusted(new X509Certificate[] { firstLeaf }, "RSA"));
        assertDoesNotThrow(() -> bothAnchors.checkClientTrusted(new X509Certificate[] { secondLeaf }, "RSA"));

        assertDoesNotThrow(() -> bothAnchors
                .checkClientTrusted(new X509Certificate[] { firstLeaf, firstCa.certificate }, "RSA"));
        assertDoesNotThrow(() -> bothAnchors
                .checkClientTrusted(new X509Certificate[] { secondLeaf, secondCa.certificate }, "RSA"));
    }

    @Test
    public void acceptedIssuersComeFromTheTruststore() {
        X509Certificate[] issuers = trustManager.getAcceptedIssuers();
        assertEquals(1, issuers.length, "Expected the configured trust anchor to be advertised");
        assertEquals(ca.certificate, issuers[0]);
    }

    /**
     * A delegate that accepts everything and records which of the six methods it was asked, so that the
     * overload the wrapper chose can be asserted.
     */
    private static final class RecordingTrustManager extends X509ExtendedTrustManager {

        private final X509Certificate anchor;
        private String lastCall;

        private RecordingTrustManager(X509Certificate anchor) {
            this.anchor = anchor;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            lastCall = "checkClientTrusted/2";
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            lastCall = "checkClientTrusted/Socket";
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            lastCall = "checkClientTrusted/SSLEngine";
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            lastCall = "checkServerTrusted/2";
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            lastCall = "checkServerTrusted/Socket";
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            lastCall = "checkServerTrusted/SSLEngine";
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] { anchor };
        }
    }
}
