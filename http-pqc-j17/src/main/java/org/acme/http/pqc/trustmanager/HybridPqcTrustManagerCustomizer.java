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

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.quarkus.vertx.http.HttpServerOptionsCustomizer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.TrustOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quarkus CDI bean that customizes the Vert.x HTTP server to use a custom TrustManager.
 *
 * <p>
 * Setting trust options replaces whatever the server would otherwise have used, so the trust manager
 * Quarkus built from {@code quarkus.http.ssl.certificate.trust-store-file} is taken out of the
 * handshake. To keep the standard chain and expiry checks, that trust manager is retrieved here and
 * passed to {@link HybridPqcX509TrustManager} as its delegate rather than being discarded.
 */
@ApplicationScoped
public class HybridPqcTrustManagerCustomizer implements HttpServerOptionsCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(HybridPqcTrustManagerCustomizer.class);

    @Inject
    Vertx vertx;

    @Override
    public void customizeHttpsServer(HttpServerOptions options) {
        LOG.info("Registering custom hybrid PQC TrustManager for TLS-layer validation...");

        X509TrustManager platformTrustManager = platformTrustManager(options);
        X509TrustManager customTrustManager = new HybridPqcX509TrustManager(platformTrustManager);

        // Wrap the X509TrustManager into Vert.x TrustOptions and register it with the HTTP server
        options.setTrustOptions(TrustOptions.wrap(customTrustManager));

        LOG.info("Custom hybrid PQC TrustManager registered successfully");
        LOG.info("  Client certificates must chain to a configured trust anchor and carry a valid ML-DSA-65 signature");
    }

    /**
     * Returns the trust manager Quarkus built from the configured truststore, which performs the
     * standard chain, trust anchor and validity-period checks.
     */
    private X509TrustManager platformTrustManager(HttpServerOptions options) {
        TrustOptions trustOptions = options.getTrustOptions();
        if (trustOptions == null) {
            // Without a truststore there is nothing to validate certificate chains against. Failing
            // here is deliberate: continuing would leave the PQC check as the only barrier, and that
            // check cannot establish trust on its own.
            throw new IllegalStateException(
                    "No truststore configured. Set quarkus.http.ssl.certificate.trust-store-file so that "
                            + "client certificate chains can be validated against a trust anchor.");
        }

        try {
            TrustManagerFactory trustManagerFactory = trustOptions.getTrustManagerFactory(vertx);
            for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    return (X509TrustManager) trustManager;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not obtain a trust manager from the configured truststore", e);
        }

        throw new IllegalStateException("The configured truststore yielded no X509TrustManager");
    }
}
