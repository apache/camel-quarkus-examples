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
package org.acme.http.pqc.certificates.util;

import java.security.cert.X509Certificate;

import org.acme.http.pqc.crypto.ChimeraOids;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for validating the post-quantum half of a Chimera hybrid certificate.
 *
 * <p>
 * This covers <em>only</em> the ML-DSA-65 alternative signature. Chain building, trust anchors and
 * validity periods are the job of the platform trust manager, which
 * {@code org.acme.http.pqc.trustmanager.HybridPqcX509TrustManager} runs first.
 *
 * <p>
 * The alternative signature is verified with the <em>issuer's</em> ML-DSA-65 public key, taken from
 * the issuer's {@code subjectAltPublicKeyInfo} extension. Verifying it against a key carried by the
 * certificate under test would authenticate nothing, since whoever produced the certificate chose
 * that key.
 */
public final class CertificatesUtil {

    private static final Logger LOG = LoggerFactory.getLogger(CertificatesUtil.class);

    private CertificatesUtil() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Verifies that a certificate carries a valid ML-DSA-65 alternative signature made by its issuer.
     *
     * @param  cert                           The certificate to validate
     * @param  issuer                         The certificate of the issuing authority, which must
     *                                        publish its ML-DSA-65 public key in
     *                                        {@code subjectAltPublicKeyInfo}
     * @throws CertificateValidationException if the PQC extensions are missing, name a different
     *                                        algorithm, or the alternative signature does not verify
     */
    public static void validateHybridCertificate(X509Certificate cert, X509Certificate issuer)
            throws CertificateValidationException {
        LOG.debug("Validating hybrid certificate for subject: {} issued by: {}",
                cert.getSubjectX500Principal(), issuer.getSubjectX500Principal());

        try {
            X509CertificateHolder holder = new X509CertificateHolder(cert.getEncoded());

            // Verify the alternative signature algorithm extension exists and is ML-DSA-65
            Extension altSigAlg = holder.getExtension(ChimeraOids.ALT_SIGNATURE_ALGORITHM);
            if (altSigAlg == null) {
                throw new CertificateValidationException(
                        "PQC signature algorithm extension missing (OID 2.5.29.73)");
            }

            AlgorithmIdentifier algId = AlgorithmIdentifier.getInstance(altSigAlg.getParsedValue());
            if (!ChimeraOids.ML_DSA_65.equals(algId.getAlgorithm())) {
                throw new CertificateValidationException(
                        "Expected ML-DSA-65 algorithm OID, found: " + algId.getAlgorithm());
            }

            LOG.debug("ML-DSA-65 algorithm OID validated");

            if (holder.getExtension(ChimeraOids.ALT_SIGNATURE_VALUE) == null) {
                throw new CertificateValidationException(
                        "PQC signature extension missing (OID 2.5.29.74)");
            }

            // The verification key comes from the issuer, not from the certificate being checked
            SubjectPublicKeyInfo mlDsaPublicKey = issuerAltPublicKey(issuer);

            // isAlternativeSignatureValid checks the signature against the DER-encoded TBSCertificate
            // with the altSignatureValue extension removed, so it covers the whole certificate body
            ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
                    .setProvider("BC")
                    .build(mlDsaPublicKey);

            if (!holder.isAlternativeSignatureValid(verifier)) {
                throw new CertificateValidationException("ML-DSA-65 signature validation failed");
            }

            LOG.debug("ML-DSA-65 signature verified - hybrid certificate valid");

        } catch (CertificateValidationException e) {
            LOG.warn("Certificate validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            String message = "Unexpected error during certificate validation: " + e.getMessage();
            LOG.error(message, e);
            throw new CertificateValidationException(message, e);
        }
    }

    /**
     * Extracts the issuer's ML-DSA-65 public key from its {@code subjectAltPublicKeyInfo} extension.
     */
    private static SubjectPublicKeyInfo issuerAltPublicKey(X509Certificate issuer) throws Exception {
        Extension altPublicKey = new X509CertificateHolder(issuer.getEncoded())
                .getExtension(ChimeraOids.SUBJECT_ALT_PUBLIC_KEY_INFO);

        if (altPublicKey == null) {
            throw new CertificateValidationException(
                    "Issuer " + issuer.getSubjectX500Principal()
                            + " publishes no PQC public key (OID 2.5.29.72), so the alternative signature "
                            + "cannot be verified");
        }

        return SubjectPublicKeyInfo.getInstance(altPublicKey.getParsedValue());
    }
}
