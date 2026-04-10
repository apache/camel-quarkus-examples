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

import javax.net.ssl.X509TrustManager;

import io.quarkus.vertx.http.HttpServerOptionsCustomizer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.TrustOptions;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quarkus CDI bean that customizes the Vert.x HTTP server to use a custom TrustManager.
 *
 * This customizer registers {@link HybridPqcX509TrustManager} with the HTTP server,
 * enabling TLS-layer validation of hybrid PQC certificates (RSA + ML-DSA-65) during
 * the TLS handshake.
 *
 * Uses Quarkus {@link HttpServerOptionsCustomizer} interface to integrate with Vert.x.
 */
@ApplicationScoped
public class HybridPqcTrustManagerCustomizer implements HttpServerOptionsCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(HybridPqcTrustManagerCustomizer.class);

    @Override
    public void customizeHttpsServer(HttpServerOptions options) {
        LOG.info("Registering custom hybrid PQC TrustManager for TLS-layer validation...");

        // Create custom TrustManager
        X509TrustManager customTrustManager = new HybridPqcX509TrustManager();

        // Wrap the X509TrustManager into Vert.x TrustOptions
        TrustOptions trustOptions = TrustOptions.wrap(customTrustManager);

        // Register with Vert.x HTTP server using setTrustOptions
        options.setTrustOptions(trustOptions);

        LOG.info("Custom hybrid PQC TrustManager registered successfully");
        LOG.info("  Client certificates will be validated at TLS layer (RSA + ML-DSA-65)");
    }
}
