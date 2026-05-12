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

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jboss.logging.Logger;

@ApplicationScoped
@Startup
public class CertificateGenerator {

    private static final Logger LOG = Logger.getLogger(CertificateGenerator.class);
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String CERT_DIR = "target/certs";

    public CertificateGenerator() {
        try {
            generateCertificates();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PQC certificates", e);
        }
    }

    private void generateCertificates() throws Exception {
        Path certDir = Paths.get(CERT_DIR);
        Files.createDirectories(certDir);

        LOG.info("Generating PQC-ready certificates for Java 21...");

        KeyPair serverKeyPair = generateKeyPair();
        X509Certificate serverCert = generateCertificate(serverKeyPair, "CN=localhost,O=Camel Quarkus,C=US", true);

        KeyPair clientKeyPair = generateKeyPair();
        X509Certificate clientCert = generateCertificate(clientKeyPair, "CN=client,O=Camel Quarkus,C=US", false);

        saveKeyStore(certDir.resolve("server-keystore.p12"), serverKeyPair, serverCert);
        saveKeyStore(certDir.resolve("client-keystore.p12"), clientKeyPair, clientCert);

        saveTrustStore(certDir.resolve("server-truststore.p12"), clientCert);
        saveTrustStore(certDir.resolve("client-truststore.p12"), serverCert);

        LOG.info("PQC certificates generated successfully in " + CERT_DIR);
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }

    private X509Certificate generateCertificate(KeyPair keyPair, String dn, boolean isCA) throws Exception {
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter = new Date(now + 365L * 24 * 60 * 60 * 1000);

        X500Name dnName = new X500Name(dn);
        BigInteger serial = BigInteger.valueOf(now);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName,
                serial,
                notBefore,
                notAfter,
                dnName,
                keyPair.getPublic());

        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCA));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));
    }

    private void saveKeyStore(Path path, KeyPair keyPair, X509Certificate cert) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        keyStore.setKeyEntry("key", keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(),
                new Certificate[] { cert });

        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
    }

    private void saveTrustStore(Path path, X509Certificate cert) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("PKCS12", "BC");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("cert", cert);

        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            trustStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
    }
}
