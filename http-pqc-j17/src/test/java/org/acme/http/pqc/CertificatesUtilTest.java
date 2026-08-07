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

import java.security.Security;
import java.security.cert.X509Certificate;

import org.acme.http.pqc.certificates.HybridCertificateGenerator;
import org.acme.http.pqc.certificates.HybridCertificateGenerator.CertificateData;
import org.acme.http.pqc.certificates.util.CertificateValidationException;
import org.acme.http.pqc.certificates.util.CertificatesUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the ML-DSA-65 half of the validation in isolation.
 *
 * <p>
 * Some of these cases cannot be reached through the TLS handshake: the standard chain validation that
 * {@code HybridPqcX509TrustManager} runs first rejects an untrusted certificate before the PQC check
 * is consulted, so an end-to-end test would pass even if this check were broken.
 */
public class CertificatesUtilTest {

    private static CertificateData ca;
    private static X509Certificate leaf;

    @BeforeAll
    public static void setUpClass() throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }

        ca = HybridCertificateGenerator.generateCertificateAuthority();
        leaf = HybridCertificateGenerator.issueCertificate(ca, "unit-test-leaf", true, null).certificate;
    }

    @Test
    public void certificateIssuedByTheCaIsAccepted() {
        assertDoesNotThrow(() -> CertificatesUtil.validateHybridCertificate(leaf, ca.certificate));
    }

    @Test
    public void selfSignedCaIsAcceptedAgainstItself() {
        assertDoesNotThrow(() -> CertificatesUtil.validateHybridCertificate(ca.certificate, ca.certificate));
    }

    @Test
    public void certificateWithoutAltSignatureIsRejected() throws Exception {
        X509Certificate rsaOnly = HybridCertificateGenerator
                .issueCertificate(ca, "unit-test-rsa-only", false, null).certificate;

        CertificateValidationException e = assertThrows(CertificateValidationException.class,
                () -> CertificatesUtil.validateHybridCertificate(rsaOnly, ca.certificate));
        assertTrue(e.getMessage().contains("2.5.29.73"), "Unexpected message: " + e.getMessage());
    }

    /**
     * The signature must be verified with the issuer's key, so a certificate issued by one CA must not
     * validate against another.
     */
    @Test
    public void certificateFromAnotherCaIsRejected() throws Exception {
        CertificateData otherCa = HybridCertificateGenerator.generateCertificateAuthority();

        CertificateValidationException e = assertThrows(CertificateValidationException.class,
                () -> CertificatesUtil.validateHybridCertificate(leaf, otherCa.certificate));
        assertEquals("ML-DSA-65 signature validation failed", e.getMessage());
    }

    /**
     * The alternative signature must cover the certificate body, so copying the PQC extensions onto a
     * different certificate must not produce one that validates. A signature computed over only the
     * subject DN would pass this, because the DN is copied along with the extensions.
     */
    @Test
    public void certificateWithLiftedExtensionsIsRejected() throws Exception {
        X509Certificate forged = ForgedCertificates.withLiftedExtensions(leaf).certificate;

        assertEquals(leaf.getSubjectX500Principal(), forged.getSubjectX500Principal(),
                "The forgery should reuse the victim's subject DN");

        CertificateValidationException e = assertThrows(CertificateValidationException.class,
                () -> CertificatesUtil.validateHybridCertificate(forged, ca.certificate));
        assertEquals("ML-DSA-65 signature validation failed", e.getMessage());
    }

    /**
     * Without the issuer's published ML-DSA-65 key there is nothing to verify against, so validation
     * must fail rather than skip the check.
     */
    @Test
    public void issuerWithoutPublishedAltKeyIsRejected() throws Exception {
        X509Certificate issuerWithoutAltKey = HybridCertificateGenerator
                .issueCertificate(ca, "unit-test-no-alt-key", true, null).certificate;

        CertificateValidationException e = assertThrows(CertificateValidationException.class,
                () -> CertificatesUtil.validateHybridCertificate(leaf, issuerWithoutAltKey));
        assertTrue(e.getMessage().contains("2.5.29.72"), "Unexpected message: " + e.getMessage());
    }
}
