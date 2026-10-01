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

import java.security.Security;

import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

@DefaultBean
@ApplicationScoped
public class SecurityConfiguration {

    private static final Logger LOG = Logger.getLogger(SecurityConfiguration.class);

    void onStart(@Observes StartupEvent ev) {
        // Detect native mode
        boolean isNativeMode = "executable".equals(System.getProperty("org.graalvm.nativeimage.kind"));

        if (isNativeMode) {
            // In native mode, providers are already registered at build time via -H:AdditionalSecurityProviders
            // We cannot remove and re-register them. Just verify they're present.
            LOG.info("Native mode: Verifying build-time registered providers");
            LOG.info("BC provider: " + Security.getProvider("BC"));
            LOG.info("BCJSSE provider: " + Security.getProvider("BCJSSE"));
        } else {
            // Configure JSSE to enable PQC hybrid key exchange algorithms
            // X25519MLKEM768 combines classical X25519 ECDH with quantum-resistant ML-KEM-768
            String namedGroups = ConfigProvider.getConfig().getValue("jdk.tls.namedGroups",
                    String.class);
            if (namedGroups != null) {
                System.setProperty("jdk.tls.namedGroups", namedGroups);
                LOG.info("Configured TLS named groups for PQC: " + namedGroups);
            }

            // JVM mode: Remove existing providers to ensure clean state for each test
            if (Security.getProvider("BCJSSE") != null) {
                Security.removeProvider("BCJSSE");
                LOG.info("Removed existing BouncyCastleJsseProvider");
            }
            if (Security.getProvider("BC") != null) {
                Security.removeProvider("BC");
                LOG.info("Removed existing BouncyCastleProvider");
            }

            // Register BC at the end (low priority) so BCJSSE can use it
            // for key conversion, while JDK's SUN/SunJCE remain the preferred
            // providers for PKCS12 KeyStore and PBE algorithms.
            Security.addProvider(new BouncyCastleProvider());
            LOG.info("Registered BouncyCastleProvider at end of provider list");

            // Register BCJSSE at position 2 for TLS (after DefaultSecureRandom provider).
            // BCJSSE will now be able to call SecureRandom.getInstance("DEFAULT") successfully.
            Security.insertProviderAt(new BouncyCastleJsseProvider(), 2);
            LOG.info("Registered BouncyCastleJsseProvider at position 2");
        }

        // Generate certificates if they don't exist (for dev mode)
        // In test mode, CertificateTestResource handles this, but for dev/prod mode we need to generate them here
        CertificateGenerator.generateCertificatesIfNeeded();
    }

}
