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

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;

import org.acme.http.pqc.certificates.util.CertificateValidationException;
import org.acme.http.pqc.certificates.util.CertificatesUtil;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom {@link X509ExtendedTrustManager} that adds hybrid PQC validation on top of the standard
 * checks.
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
 * {@link X509ExtendedTrustManager} is extended rather than {@link javax.net.ssl.X509TrustManager}
 * implemented, so that the {@code Socket} and {@code SSLEngine} overloads are delegated as well.
 * Those carry the connection context the platform needs for endpoint identification, which is how
 * hostname verification happens on the client side. A plain {@code X509TrustManager} only gets the
 * two-argument methods, so a wrapper that implements it silently drops hostname verification for any
 * TLS stack that does not apply the JSSE wrapper that would otherwise compensate.
 *
 * <p>
 * Note that this example has no revocation checking (no CRL or OCSP), which a production deployment
 * would need.
 */
public class HybridPqcX509TrustManager extends X509ExtendedTrustManager {

    private static final Logger LOG = LoggerFactory.getLogger(HybridPqcX509TrustManager.class);

    private final X509ExtendedTrustManager delegate;

    /**
     * @param delegate the platform trust manager to perform chain, anchor, expiry and endpoint
     *                 identification checks
     */
    public HybridPqcX509TrustManager(X509ExtendedTrustManager delegate) {
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
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        requireChain(chain, "Client");
        delegate.checkClientTrusted(chain, authType, socket);
        validateHybridChain(chain, "Client");
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        requireChain(chain, "Client");
        delegate.checkClientTrusted(chain, authType, engine);
        validateHybridChain(chain, "Client");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        requireChain(chain, "Server");

        // Standard X.509 validation first: chain, trust anchor, validity period
        delegate.checkServerTrusted(chain, authType);

        validateHybridChain(chain, "Server");
    }

    /**
     * The {@code Socket} and {@code SSLEngine} overloads are what let the delegate perform endpoint
     * identification, so they must be passed through rather than being redirected to the two-argument
     * method.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
            throws CertificateException {
        requireChain(chain, "Server");
        delegate.checkServerTrusted(chain, authType, socket);
        validateHybridChain(chain, "Server");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
            throws CertificateException {
        requireChain(chain, "Server");
        delegate.checkServerTrusted(chain, authType, engine);
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
     * Finds the certificate that issued {@code cert}, looking in the chain the peer presented and then
     * among the configured trust anchors. Peers may send only their own certificate and leave the anchor
     * to the relying party, or send the anchor along with it, so both need checking.
     *
     * <p>
     * Candidates matching on key identifier are preferred to candidates matching on the issuer name
     * alone, because the name is the weaker claim and anything the peer sends can carry one. The chain
     * has already been validated by the delegate at this point, so a certificate found in it is one the
     * platform trust manager accepted as part of a path to an anchor — but not necessarily one it built
     * the path <em>through</em>, since it ignores any certificate that does not belong there.
     */
    private X509Certificate findIssuer(X509Certificate cert, X509Certificate[] chain) {
        X509Certificate[] anchors = delegate.getAcceptedIssuers();

        // Key identifiers first, so that a candidate which merely shares the issuer name cannot
        // displace the one the platform actually built the path through
        X509Certificate issuer = findIssuerAmong(cert, chain, anchors, true);
        if (issuer != null) {
            return issuer;
        }

        // A self-issued certificate is its own issuer. Trust anchors carry no authority key
        // identifier to match on, so without this the name fallback below would settle for any
        // same-DN sibling, and a rolled-over CA has one of those in the truststore by definition
        if (isSelfIssued(cert)) {
            return cert;
        }

        return findIssuerAmong(cert, chain, anchors, false);
    }

    private static X509Certificate findIssuerAmong(X509Certificate cert, X509Certificate[] chain,
            X509Certificate[] anchors, boolean requireKeyIdentifier) {
        for (X509Certificate candidate : chain) {
            if (candidate != cert && isIssuerOf(candidate, cert, requireKeyIdentifier)) {
                return candidate;
            }
        }

        for (X509Certificate anchor : anchors) {
            if (isIssuerOf(anchor, cert, requireKeyIdentifier)) {
                return anchor;
            }
        }

        return null;
    }

    /**
     * Decides whether {@code candidate} is the issuer of {@code cert}. Both the authority key
     * identifier and the issuer name must match when {@code requireKeyIdentifier} is set; otherwise the
     * name alone is enough.
     *
     * <p>
     * Matching on the name alone is not enough on its own. Distinguished names are not unique over time
     * or across a federation: a CA that has rolled its key is present in the truststore twice under one
     * DN with two different ML-DSA-65 keys, and cross-certified CAs share a DN by design. Picking the
     * wrong one makes this class reject a certificate the platform trust manager just accepted. Matching
     * the authority key identifier against the candidate's subject key identifier, as the platform does
     * when it builds the path, keeps the two in step. The name-only pass exists for certificates that
     * predate the extensions, RFC 5280 requiring neither of them on a self-signed anchor.
     */
    private static boolean isIssuerOf(X509Certificate candidate, X509Certificate cert,
            boolean requireKeyIdentifier) {
        X500Principal issuerName = cert.getIssuerX500Principal();
        if (!candidate.getSubjectX500Principal().equals(issuerName)) {
            return false;
        }

        if (!requireKeyIdentifier) {
            return true;
        }

        try {
            byte[] authorityKeyId = authorityKeyIdentifier(cert);
            byte[] subjectKeyId = subjectKeyIdentifier(candidate);

            return authorityKeyId != null && subjectKeyId != null && Arrays.equals(authorityKeyId, subjectKeyId);
        } catch (IllegalArgumentException e) {
            // Anything a peer sends can be malformed. A key identifier that cannot be read is treated
            // as one that is not there, leaving the issuer name as the only thing left to match on
            LOG.debug("Ignoring unparseable key identifier extension: {}", e.getMessage());
            return false;
        }
    }

    private static boolean isSelfIssued(X509Certificate cert) {
        return cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal());
    }

    private static byte[] authorityKeyIdentifier(X509Certificate cert) {
        byte[] encoded = cert.getExtensionValue(Extension.authorityKeyIdentifier.getId());
        return encoded == null
                ? null
                : AuthorityKeyIdentifier.getInstance(unwrap(encoded)).getKeyIdentifier();
    }

    private static byte[] subjectKeyIdentifier(X509Certificate cert) {
        byte[] encoded = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
        return encoded == null
                ? null
                : SubjectKeyIdentifier.getInstance(unwrap(encoded)).getKeyIdentifier();
    }

    /**
     * {@link X509Certificate#getExtensionValue(String)} returns the extension wrapped in a DER octet
     * string, which has to be unwrapped before the value inside can be parsed.
     */
    private static byte[] unwrap(byte[] encoded) {
        return ASN1OctetString.getInstance(encoded).getOctets();
    }
}
