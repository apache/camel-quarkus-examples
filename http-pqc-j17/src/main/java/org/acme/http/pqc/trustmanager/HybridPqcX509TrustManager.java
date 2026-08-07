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
package org.acme.http.pqc.trustmanager;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.acme.http.pqc.certificates.util.CertificateValidationException;
import org.acme.http.pqc.certificates.util.CertificatesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom {@link X509TrustManager} that adds hybrid PQC validation on top of the standard checks.
 *
 * <p>
 * The important part of this class is what it does <em>not</em> do itself. Chain building, trust
 * anchor lookup and validity-period checking are delegated to the platform trust manager that
 * Quarkus builds from the configured truststore; only once that has passed is the ML-DSA-65
 * alternative signature verified on top. Getting this ordering wrong is the classic way to write a
 * trust manager that accepts anything: a custom check on its own replaces the platform checks rather
 * than adding to them, because the JSSE handshake asks this class and nothing else.
 *
 * <p>
 * The effect is that a client certificate must chain to a trust anchor, be inside its validity
 * period, <em>and</em> carry a valid ML-DSA-65 alternative signature made by its issuer. A
 * self-signed certificate carrying well-formed PQC extensions is rejected, because no anchor vouches
 * for it, and a certificate issued by the trusted CA without an alternative signature is rejected
 * too.
 *
 * <p>
 * Note that this example has no revocation checking (no CRL or OCSP), which a production deployment
 * would need.
 */
public class HybridPqcX509TrustManager implements X509TrustManager {

    private static final Logger LOG = LoggerFactory.getLogger(HybridPqcX509TrustManager.class);

    private final X509TrustManager delegate;

    /**
     * @param delegate the platform trust manager to perform chain, anchor and expiry validation
     */
    public HybridPqcX509TrustManager(X509TrustManager delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate trust manager is required");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        requireChain(chain, "Client");

        // Standard X.509 validation first: chain, trust anchor, validity period
        delegate.checkClientTrusted(chain, authType);

        validateHybridChain(chain, "Client");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        requireChain(chain, "Server");

        // Standard X.509 validation first: chain, trust anchor, validity period, hostname material
        delegate.checkServerTrusted(chain, authType);

        validateHybridChain(chain, "Server");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        // Delegate, so that the certificate authorities advertised during the handshake are the ones
        // in the configured truststore. Returning an empty array here tells peers nothing about which
        // issuers are acceptable.
        return delegate.getAcceptedIssuers();
    }

    private static void requireChain(X509Certificate[] chain, String peer) throws CertificateException {
        if (chain == null || chain.length == 0) {
            throw new CertificateException(peer + " certificate chain is empty");
        }
    }

    /**
     * Verifies the ML-DSA-65 alternative signature on every certificate in the chain, each against the
     * ML-DSA-65 public key published by its issuer.
     */
    private void validateHybridChain(X509Certificate[] chain, String peer) throws CertificateException {
        for (X509Certificate cert : chain) {
            LOG.debug("Validating {} certificate hybrid PQC extensions: {}", peer, cert.getSubjectX500Principal());

            X509Certificate issuer = findIssuer(cert, chain);
            if (issuer == null) {
                throw new CertificateException("Could not find the issuer of " + cert.getSubjectX500Principal()
                        + ", so its ML-DSA-65 signature cannot be verified");
            }

            try {
                CertificatesUtil.validateHybridCertificate(cert, issuer);
            } catch (CertificateValidationException e) {
                LOG.error("Hybrid PQC certificate validation failed: {}", e.getMessage());
                throw new CertificateException("Validation failed: " + e.getMessage(), e);
            }
        }

        LOG.debug("{} certificate chain validated successfully (RSA chain + ML-DSA-65)", peer);
    }

    /**
     * Finds the certificate that issued {@code cert}, looking first in the chain the peer presented and
     * then among the configured trust anchors. Peers commonly send only their own certificate and leave
     * the anchor to the relying party, so both need checking.
     *
     * <p>
     * The chain has already been validated by the delegate at this point, so a certificate found in it
     * is one the platform trust manager accepted as part of a path to an anchor.
     */
    private X509Certificate findIssuer(X509Certificate cert, X509Certificate[] chain) {
        X500Principal issuerName = cert.getIssuerX500Principal();

        for (X509Certificate candidate : chain) {
            if (candidate != cert && candidate.getSubjectX500Principal().equals(issuerName)) {
                return candidate;
            }
        }

        for (X509Certificate anchor : delegate.getAcceptedIssuers()) {
            if (anchor.getSubjectX500Principal().equals(issuerName)) {
                return anchor;
            }
        }

        // A self-signed anchor is its own issuer
        if (cert.getSubjectX500Principal().equals(issuerName)) {
            return cert;
        }

        return null;
    }
}
