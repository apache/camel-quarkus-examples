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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.acme.http.pqc.crypto.ChimeraOids;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Builds certificates that a reader might expect to be accepted but that must be rejected.
 * Used by {@link HttpPqcTest} and {@link CertificatesUtilTest}.
 */
public final class ForgedCertificates {

    private static final String PASSWORD = "changeit";
    private static final String CERT_DIR = "target/certs";

    private ForgedCertificates() {
    }

    /**
     * A certificate and the private key needed to present it during a TLS handshake.
     */
    public static class Forged {
        public final KeyPair rsaKeyPair;
        public final X509Certificate certificate;

        Forged(KeyPair rsaKeyPair, X509Certificate certificate) {
            this.rsaKeyPair = rsaKeyPair;
            this.certificate = certificate;
        }
    }

    /**
     * A self-signed hybrid certificate with a correct, freshly generated ML-DSA-65 alternative
     * signature. Everything about it is well formed; it simply is not in the server truststore.
     */
    public static Forged rogueHybrid() throws Exception {
        return forge(new X500Name("CN=attacker,O=Evil Corp,C=XX"), 0, null);
    }

    /**
     * A hybrid certificate whose validity period ended a year ago.
     */
    public static Forged expiredHybrid() throws Exception {
        return forge(new X500Name("CN=expired,O=Evil Corp,C=XX"), -730, null);
    }

    /**
     * A certificate built on a freshly generated RSA key, carrying the PQC extensions copied verbatim
     * off {@code victim} along with its subject DN. This is the forgery that succeeds when the
     * alternative signature covers only the subject DN instead of the certificate body: the forger
     * never holds the ML-DSA-65 private key that produced the copied signature.
     *
     * <p>
     * The result is self-signed, so at TLS level it is rejected for lacking a trust anchor. Use it
     * against {@code CertificatesUtil} directly to exercise the signature check itself.
     */
    public static Forged withLiftedExtensions(X509Certificate victim) throws Exception {
        // Rebuild the DN from its DER encoding so the bytes are identical to the victim's
        X500Name victimDn = X500Name.getInstance(victim.getSubjectX500Principal().getEncoded());
        return forge(victimDn, 0, victim);
    }

    /**
     * Writes a certificate and its key to a PKCS12 keystore that a client can present.
     */
    public static Path keystore(String alias, Forged forged) throws Exception {
        Path path = Paths.get(CERT_DIR, alias + "-keystore.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(alias, forged.rsaKeyPair.getPrivate(), PASSWORD.toCharArray(),
                new X509Certificate[] { forged.certificate });
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            keyStore.store(fos, PASSWORD.toCharArray());
        }
        return path;
    }

    private static Forged forge(X500Name name, int dayOffset, X509Certificate liftFrom) throws Exception {
        KeyPairGenerator rsaKpg = KeyPairGenerator.getInstance("RSA");
        rsaKpg.initialize(2048, new SecureRandom());
        KeyPair rsa = rsaKpg.generateKeyPair();

        long day = 24L * 60 * 60 * 1000;
        long start = System.currentTimeMillis() + dayOffset * day;

        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                new BigInteger(64, new SecureRandom()),
                new Date(start),
                new Date(start + 365 * day),
                name,
                rsa.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

        ContentSigner rsaSigner = new JcaContentSignerBuilder("SHA256withRSA").build(rsa.getPrivate());
        X509CertificateHolder holder;

        if (liftFrom != null) {
            // A certificate issued by a CA carries no subjectAltPublicKeyInfo of its own, so only copy
            // the extensions that are actually present
            copyExtensionIfPresent(builder, liftFrom, ChimeraOids.SUBJECT_ALT_PUBLIC_KEY_INFO);
            copyExtensionIfPresent(builder, liftFrom, ChimeraOids.ALT_SIGNATURE_ALGORITHM);
            copyExtensionIfPresent(builder, liftFrom, ChimeraOids.ALT_SIGNATURE_VALUE);
            holder = builder.build(rsaSigner);
        } else {
            KeyPair mlDsa = KeyPairGenerator.getInstance("ML-DSA-65", "BC").generateKeyPair();
            builder.addExtension(ChimeraOids.SUBJECT_ALT_PUBLIC_KEY_INFO, false,
                    SubjectPublicKeyInfo.getInstance(mlDsa.getPublic().getEncoded()));
            ContentSigner mlDsaSigner = new JcaContentSignerBuilder("ML-DSA-65")
                    .setProvider("BC")
                    .build(mlDsa.getPrivate());
            holder = builder.build(rsaSigner, false, mlDsaSigner);
        }

        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(holder);

        return new Forged(rsa, certificate);
    }

    private static void copyExtensionIfPresent(JcaX509v3CertificateBuilder builder, X509Certificate from,
            ASN1ObjectIdentifier oid) throws Exception {
        byte[] wrapped = from.getExtensionValue(oid.getId());
        if (wrapped == null) {
            return;
        }
        byte[] inner = ((ASN1OctetString) ASN1Primitive.fromByteArray(wrapped)).getOctets();
        builder.addExtension(new Extension(oid, false, new DEROctetString(inner)));
    }
}
