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
package org.acme.http.pqc.certificates;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.acme.http.pqc.crypto.ChimeraOids;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a small Chimera hybrid certificate hierarchy: a certificate authority holding both an RSA
 * and an ML-DSA-65 keypair, which issues the server and client certificates.
 *
 * <p>
 * Every certificate the CA issues is signed twice. The RSA signature goes in the standard signature
 * field, and the ML-DSA-65 signature goes in the {@code altSignatureValue} extension, produced by
 * BouncyCastle's {@link X509v3CertificateBuilder#build(ContentSigner, boolean, ContentSigner)} which
 * computes it over the {@code TBSCertificate}. Do not hand-roll that signature over a single field
 * such as the subject DN: a signature that does not cover the body proves nothing about the rest of
 * the certificate.
 *
 * <p>
 * The CA publishes its ML-DSA-65 public key in its own {@code subjectAltPublicKeyInfo} extension,
 * which is what relying parties use to verify the alternative signature on the certificates it
 * issued. Because the CA certificate is the trust anchor, an attacker who can forge RSA signatures
 * still cannot mint a certificate that validates: they would also need the CA's ML-DSA-65 private
 * key. That is the property this example exists to demonstrate.
 *
 * <p>
 * Certificates are generated at application startup by {@link SecurityConfiguration}.
 */
public class HybridCertificateGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(HybridCertificateGenerator.class);

    // WARNING: This password is hardcoded for DEMONSTRATION purposes only.
    // In production, use environment variables, secrets management, or secure configuration.
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String CERT_DIR = "target/certs";
    private static final String CA_DN = "CN=PQC Hybrid CA,O=Apache Camel Quarkus,C=US";

    /**
     * A certificate together with the keypairs that belong to its subject. For the CA, the ML-DSA-65
     * keypair is the one used to sign the certificates it issues.
     */
    public static class CertificateData {
        public final KeyPair rsaKeyPair;
        public final KeyPair mlDsaKeyPair;
        public final X509Certificate certificate;

        public CertificateData(KeyPair rsaKeyPair, KeyPair mlDsaKeyPair, X509Certificate certificate) {
            this.rsaKeyPair = rsaKeyPair;
            this.mlDsaKeyPair = mlDsaKeyPair;
            this.certificate = certificate;
        }
    }

    /**
     * Generates the CA, the certificates it issues, and the keystores and truststores holding them.
     */
    public static void generateKeystores() throws Exception {
        CertificateData ca = generateCertificateAuthority();
        LOG.info("Hybrid CA created: {}", ca.certificate.getSubjectX500Principal());

        // The server certificate carries subject alternative names for localhost and 127.0.0.1 so that
        // clients can verify the server identity instead of having to disable verification
        GeneralNames serverAltNames = new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1") });

        CertificateData server = issueCertificate(ca, "localhost", true, serverAltNames);
        CertificateData clientHybrid = issueCertificate(ca, "client-hybrid", true, null);

        // Issued by the same CA, but without the ML-DSA-65 alternative signature. It therefore passes
        // chain validation and is rejected solely by the post-quantum check.
        CertificateData clientRsaOnly = issueCertificate(ca, "client-rsa-only", false, null);

        saveKeyStore(Paths.get(CERT_DIR, "server-hybrid-keystore.p12"), server, ca, "server");
        saveKeyStore(Paths.get(CERT_DIR, "client-hybrid-keystore.p12"), clientHybrid, ca, "client");
        saveKeyStore(Paths.get(CERT_DIR, "client-rsa-only-keystore.p12"), clientRsaOnly, ca, "client");

        // Both sides validate the peer's certificate chain against the CA, which is the trust anchor
        saveTrustStore(Paths.get(CERT_DIR, "server-hybrid-truststore.p12"), ca.certificate, "pqc-hybrid-ca");
        saveTrustStore(Paths.get(CERT_DIR, "client-hybrid-truststore.p12"), ca.certificate, "pqc-hybrid-ca");

        // PEM files for tools that cannot read PKCS12, such as curl
        savePem(Paths.get(CERT_DIR, "ca-cert.pem"), ca.certificate);
        savePem(Paths.get(CERT_DIR, "client-hybrid-cert.pem"), clientHybrid.certificate);
        savePem(Paths.get(CERT_DIR, "client-hybrid-key.pem"), clientHybrid.rsaKeyPair.getPrivate());
    }

    /**
     * Generates the self-signed hybrid certificate authority.
     *
     * <p>
     * The CA publishes its ML-DSA-65 public key in the {@code subjectAltPublicKeyInfo} extension so
     * that verifiers can check the alternative signature on the certificates it issues.
     *
     * @return CertificateData whose keypairs are the CA's signing keys
     */
    public static CertificateData generateCertificateAuthority() throws Exception {
        LOG.debug("Generating hybrid certificate authority");

        KeyPair rsaKeyPair = generateRsaKeyPair();
        KeyPair mlDsaKeyPair = generateMlDsaKeyPair();
        X500Name caName = new X500Name(CA_DN);

        X509v3CertificateBuilder certBuilder = certificateBuilder(caName, caName, rsaKeyPair.getPublic());
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

        // Publish the CA's ML-DSA-65 public key: this is the key that verifies the alternative
        // signature on every certificate the CA issues
        certBuilder.addExtension(ChimeraOids.SUBJECT_ALT_PUBLIC_KEY_INFO, false,
                SubjectPublicKeyInfo.getInstance(mlDsaKeyPair.getPublic().getEncoded()));

        // Self-signed with both of its own keys
        X509CertificateHolder certHolder = certBuilder.build(
                rsaSigner(rsaKeyPair), false, mlDsaSigner(mlDsaKeyPair));

        return new CertificateData(rsaKeyPair, mlDsaKeyPair, toX509Certificate(certHolder));
    }

    /**
     * Issues a certificate signed by the given authority.
     *
     * @param  issuer              The CA whose RSA and ML-DSA-65 keys sign the certificate
     * @param  commonName          The CN for the certificate subject
     * @param  includeAltSignature Whether to add the CA's ML-DSA-65 alternative signature. Passing
     *                             {@code false} produces a classical, RSA-only certificate.
     * @param  subjectAltNames     Subject alternative names to add, or {@code null} for none
     * @return                     CertificateData holding the subject's own RSA keypair
     */
    public static CertificateData issueCertificate(CertificateData issuer, String commonName,
            boolean includeAltSignature, GeneralNames subjectAltNames) throws Exception {
        LOG.debug("Issuing certificate for CN={}, includeAltSignature={}", commonName, includeAltSignature);

        KeyPair rsaKeyPair = generateRsaKeyPair();

        X500Name subject = new X500Name("CN=" + commonName + ",O=Apache Camel Quarkus,C=US");
        X500Name issuerName = X500Name.getInstance(issuer.certificate.getSubjectX500Principal().getEncoded());

        X509v3CertificateBuilder certBuilder = certificateBuilder(issuerName, subject, rsaKeyPair.getPublic());
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        if (subjectAltNames != null) {
            certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
        }

        X509CertificateHolder certHolder;
        if (includeAltSignature) {
            // BouncyCastle adds the altSignatureAlgorithm and altSignatureValue extensions itself,
            // signing the TBSCertificate with the CA's ML-DSA-65 key so that the signature covers the
            // whole certificate body
            certHolder = certBuilder.build(
                    rsaSigner(issuer.rsaKeyPair), false, mlDsaSigner(issuer.mlDsaKeyPair));
        } else {
            certHolder = certBuilder.build(rsaSigner(issuer.rsaKeyPair));
        }

        return new CertificateData(rsaKeyPair, null, toX509Certificate(certHolder));
    }

    private static X509v3CertificateBuilder certificateBuilder(X500Name issuer, X500Name subject,
            java.security.PublicKey subjectPublicKey) {
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000); // 1 year

        return new JcaX509v3CertificateBuilder(
                issuer,
                new BigInteger(64, new SecureRandom()),
                notBefore,
                notAfter,
                subject,
                subjectPublicKey);
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");
        rsaKpg.initialize(2048, new SecureRandom());
        return rsaKpg.generateKeyPair();
    }

    private static KeyPair generateMlDsaKeyPair() throws Exception {
        // ML-DSA-65 - NIST FIPS 204. Java 17 has no built-in implementation, so BouncyCastle provides it
        return KeyPairGenerator.getInstance("ML-DSA-65", "BC").generateKeyPair();
    }

    private static ContentSigner rsaSigner(KeyPair keyPair) throws Exception {
        return new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    }

    private static ContentSigner mlDsaSigner(KeyPair keyPair) throws Exception {
        return new JcaContentSignerBuilder("ML-DSA-65").setProvider("BC").build(keyPair.getPrivate());
    }

    private static X509Certificate toX509Certificate(X509CertificateHolder holder) throws Exception {
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }

    /**
     * Saves a private key with its certificate chain. The CA certificate is included in the chain so
     * that the peer receives the full path during the handshake.
     */
    private static void saveKeyStore(Path path, CertificateData certData, CertificateData ca, String alias)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        keyStore.setKeyEntry(alias,
                certData.rsaKeyPair.getPrivate(),
                KEYSTORE_PASSWORD.toCharArray(),
                new X509Certificate[] { certData.certificate, ca.certificate });

        store(keyStore, path);
        LOG.info("Keystore created: {}", path);
    }

    private static void saveTrustStore(Path path, X509Certificate cert, String alias) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12", "BC");
        trustStore.load(null, null);
        trustStore.setCertificateEntry(alias, cert);

        store(trustStore, path);
        LOG.info("Truststore created: {}", path);
    }

    /**
     * Saves a KeyStore to disk, overwriting any existing file.
     */
    public static void store(KeyStore keyStore, Path path) throws Exception {
        createParentDirectory(path);
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    private static void savePem(Path path, Object object) throws Exception {
        createParentDirectory(path);
        try (FileWriter writer = new FileWriter(path.toFile());
                JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(object);
        }
        LOG.info("PEM file created: {}", path);
    }

    private static void createParentDirectory(Path path) throws Exception {
        Path dirPath = path.getParent();
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            LOG.info("Created directory: {}", dirPath);
        }
    }
}
