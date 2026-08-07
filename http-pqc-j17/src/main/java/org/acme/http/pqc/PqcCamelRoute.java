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

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

@ApplicationScoped
public class PqcCamelRoute extends EndpointRouteBuilder {

    @Override
    public void configure() throws Exception {
        // Secure endpoint with TLS-layer hybrid PQC certificate validation
        // Note: Certificate validation happens during TLS handshake via custom TrustManager
        // Invalid certificates are rejected before this route executes
        // With client-auth=required, only valid hybrid PQC certificates can reach this endpoint
        from(platformHttp("/pqc/secure"))
                .routeId("pqc-secure-route")
                .log("Processing request with validated hybrid PQC certificate")
                .setBody(constant(
                        "✓ Hybrid PQC certificate validated at TLS layer!\n\n" +
                                "Your certificate chained to a configured trust anchor and carried a valid\n" +
                                "ML-DSA-65 alternative signature. Both were checked during the TLS handshake.\n\n" +
                                "This demonstrates TLS-layer validation using a custom X509TrustManager that\n" +
                                "adds a post-quantum signature check on top of the standard X.509 checks.\n\n" +
                                "Note: the connection itself is protected by classical cryptography. Java 17\n" +
                                "cannot negotiate a post-quantum key exchange, so only the certificate\n" +
                                "authentication is hybrid here. See http-pqc-j21 for quantum-safe transport.\n"))
                .to(log("pqc-secure").showExchangePattern(false).showBodyType(false));
    }
}
