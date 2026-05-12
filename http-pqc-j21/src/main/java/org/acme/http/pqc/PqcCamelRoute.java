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
        from(platformHttp("/pqc/secure"))
                .routeId("pqc-secure-route")
                .log("Processing request with PQC-enabled TLS connection")
                .setBody(constant(
                        "✓ PQC TLS connection established!\n\n" +
                                "Your connection is quantum-safe using Java 21 with BouncyCastle JSSE provider.\n" +
                                "This example demonstrates native PQC TLS support with hybrid cipher suites.\n\n" +
                                "TLS 1.3 with X25519MLKEM768 hybrid key exchange provides both:\n" +
                                "- Classical security via X25519 elliptic-curve cryptography\n" +
                                "- Quantum resistance via ML-KEM-768 (NIST FIPS 203)\n"))
                .to(log("pqc-secure").showExchangePattern(false).showBodyType(false));

        from(platformHttp("/pqc/info"))
                .routeId("pqc-info-route")
                .log("Providing PQC configuration information")
                .process(exchange -> {
                    String info = String.format(
                            "Post-Quantum Cryptography Configuration\n" +
                                    "======================================\n\n" +
                                    "Java Version: %s\n" +
                                    "Provider: BouncyCastle JSSE\n" +
                                    "TLS Version: 1.3\n" +
                                    "Hybrid Cipher Suite: X25519MLKEM768\n" +
                                    "Classical Algorithm: X25519\n" +
                                    "PQC Algorithm: ML-KEM-768 (NIST FIPS 203)\n\n" +
                                    "This configuration provides protection against both classical and quantum attacks.",
                            System.getProperty("java.version"));
                    exchange.getMessage().setBody(info);
                })
                .to(log("pqc-info").showExchangePattern(false).showBodyType(false));
    }
}
