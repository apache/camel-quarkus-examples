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
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.acme.http.pqc.crypto.ChimeraOids;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
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
 * Utility class for generating and persisting Chimera hybrid certificates.
 * These certificates combine classical RSA with post-quantum ML-DSA-65 signatures
 * using X.509 extensions as specified in the BouncyCastle PQC Almanac.
 *
 * <p>
 * Certificates are automatically generated at application startup by
 * {@link SecurityConfiguration} if they don't already exist.
 */
public class HybridCertificateGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(HybridCertificateGenerator.class);

    // Demo keystore password - DO NOT use in production
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String KEYSTORES_DIR = "target/certs";

    /**
     * Certificate data holder for keypairs and certificates.
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
     * Generates a Chimera hybrid certificate combining RSA and ML-DSA-65.
     *
     * @param  commonName          The CN for the certificate subject
     * @param  includeAltSignature Whether to include the ML-DSA-65 alternative signature
     * @return                     CertificateData containing keypairs and certificate
     */
    public static CertificateData generateChimeraCertificate(String commonName, boolean includeAltSignature)
            throws Exception {
        LOG.debug("Generating Chimera hybrid certificate for CN={}, includeAltSignature={}",
                commonName, includeAltSignature);

        // Generate RSA keypair (classical algorithm)
        KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");
        rsaKpg.initialize(2048, new SecureRandom());
        KeyPair rsaKeyPair = rsaKpg.generateKeyPair();

        // Generate ML-DSA-65 keypair (PQC algorithm - NIST FIPS 204)
        KeyPairGenerator mlDsaKpg = KeyPairGenerator.getInstance("ML-DSA-65", "BC");
        KeyPair mlDsaKeyPair = mlDsaKpg.generateKeyPair();

        // Build certificate
        X500Name issuer = new X500Name("CN=PQC Hybrid CA,O=Apache Camel Quarkus,C=US");
        X500Name subject = new X500Name("CN=" + commonName + ",O=Apache Camel Quarkus,C=US");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date();
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000); // 1 year

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                rsaKeyPair.getPublic());

        if (includeAltSignature) {
            // Add ML-DSA-65 alternative public key (Chimera extension)
            SubjectPublicKeyInfo mlDsaPubKeyInfo = SubjectPublicKeyInfo
                    .getInstance(mlDsaKeyPair.getPublic().getEncoded());
            certBuilder.addExtension(ChimeraOids.SUBJECT_ALT_PUBLIC_KEY_INFO, false, mlDsaPubKeyInfo);

            // Add alternative signature algorithm (Chimera extension)
            AlgorithmIdentifier mlDsaSigAlg = new AlgorithmIdentifier(ChimeraOids.ML_DSA_65);
            certBuilder.addExtension(ChimeraOids.ALT_SIGNATURE_ALGORITHM, false, mlDsaSigAlg);

            // Generate ML-DSA-65 alternative signature (Chimera extension)
            Signature mlDsaSig = Signature.getInstance("ML-DSA-65", "BC");
            mlDsaSig.initSign(mlDsaKeyPair.getPrivate());
            mlDsaSig.update(subject.getEncoded()); // Sign subject DN as per Chimera spec
            byte[] mlDsaSignature = mlDsaSig.sign();
            certBuilder.addExtension(ChimeraOids.ALT_SIGNATURE_VALUE, false, new DERBitString(mlDsaSignature));

            LOG.debug("ML-DSA-65 extensions added to certificate");
        }

        // Sign with RSA (primary signature)
        ContentSigner rsaSigner = new JcaContentSignerBuilder("SHA256withRSA").build(rsaKeyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(rsaSigner);

        // Convert to X509Certificate
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

        LOG.debug("Chimera certificate generated successfully for CN={}", commonName);

        return new CertificateData(rsaKeyPair, mlDsaKeyPair, certificate);
    }

    /**
     * Generates a keystore with the specified certificate type.
     *
     * @param commonName          The CN for the certificate
     * @param includeAltSignature Whether to include ML-DSA-65 extensions
     * @param keystorePath        Path where keystore will be saved
     * @param alias               Keystore entry alias
     * @param includeTruststore   Whether to also create a truststore
     * @param savePemFiles        Whether to also save certificate and key as PEM files
     */
    private static void generateKeystore(
            String commonName,
            boolean includeAltSignature,
            String keystorePath,
            String alias,
            boolean includeTruststore,
            boolean savePemFiles) throws Exception {

        CertificateData certData = generateChimeraCertificate(commonName, includeAltSignature);

        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        keyStore.setKeyEntry(alias,
                certData.rsaKeyPair.getPrivate(),
                KEYSTORE_PASSWORD.toCharArray(),
                new X509Certificate[] { certData.certificate });

        saveKeyStore(keyStore, keystorePath, KEYSTORE_PASSWORD);
        LOG.info("Keystore created: {}", keystorePath);

        if (includeTruststore) {
            String trustPath = keystorePath.replace("-keystore.p12", "-truststore.p12");
            KeyStore trustStore = KeyStore.getInstance("PKCS12", "BC");
            trustStore.load(null, null);
            trustStore.setCertificateEntry(alias + "-ca", certData.certificate);
            saveKeyStore(trustStore, trustPath, KEYSTORE_PASSWORD);
            LOG.info("Truststore created: {}", trustPath);
        }

        if (savePemFiles) {
            // Save certificate as PEM
            String certPemPath = keystorePath.replace(".p12", "-cert.pem");
            try (FileWriter certWriter = new FileWriter(certPemPath);
                    JcaPEMWriter pemWriter = new JcaPEMWriter(certWriter)) {
                pemWriter.writeObject(certData.certificate);
            }
            LOG.info("Certificate PEM file created: {}", certPemPath);

            // Save private key as PEM
            String keyPemPath = keystorePath.replace(".p12", "-key.pem");
            try (FileWriter keyWriter = new FileWriter(keyPemPath);
                    JcaPEMWriter pemWriter = new JcaPEMWriter(keyWriter)) {
                pemWriter.writeObject(certData.rsaKeyPair.getPrivate());
            }
            LOG.info("Private key PEM file created: {}", keyPemPath);
        }
    }

    /**
     * Generates server hybrid keystore with RSA + ML-DSA-65 certificate.
     */
    public static void generateServerKeystore() throws Exception {
        generateKeystore("localhost", true, KEYSTORES_DIR + "/server-hybrid-keystore.p12",
                "server", true, false);
    }

    /**
     * Generates client hybrid keystore with RSA + ML-DSA-65 certificate.
     * Also saves PEM files for manual curl testing.
     */
    public static void generateClientHybridKeystore() throws Exception {
        generateKeystore("client-hybrid", true, KEYSTORES_DIR + "/client-hybrid-keystore.p12",
                "client", false, true);
    }

    /**
     * Generates client RSA-only keystore (no PQC extensions - for failure test).
     */
    public static void generateClientRsaOnlyKeystore() throws Exception {
        generateKeystore("client-rsa-only", false, KEYSTORES_DIR + "/client-rsa-only-keystore.p12",
                "client", false, false);
    }

    /**
     * Saves a KeyStore to disk, overwriting any existing file.
     */
    public static void saveKeyStore(KeyStore keyStore, String path, String password) throws Exception {
        Path dirPath = Paths.get(path).getParent();
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            LOG.info("Created directory: {}", dirPath);
        }

        try (FileOutputStream fos = new FileOutputStream(path)) {
            keyStore.store(fos, password.toCharArray());
        }
    }
}
