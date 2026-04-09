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

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SecurityConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);

    void onStart(@Observes StartupEvent ev) {
        // Register BouncyCastle as the first provider to ensure PQC algorithms are available
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        LOG.info("BouncyCastle provider registered at position 1 for PQC support");

        // Generate fresh hybrid PQC keystores on every startup
        generateKeystores();
    }

    private void generateKeystores() {
        try {
            LOG.info("Generating fresh hybrid PQC keystores...");
            HybridCertificateGenerator.generateServerKeystore();
            HybridCertificateGenerator.generateClientHybridKeystore();
            HybridCertificateGenerator.generateClientRsaOnlyKeystore();
            LOG.info("Hybrid PQC keystores generated successfully");
        } catch (Exception e) {
            LOG.error("Failed to generate hybrid PQC keystores", e);
            throw new RuntimeException("Keystore generation failed", e);
        }
    }
}
