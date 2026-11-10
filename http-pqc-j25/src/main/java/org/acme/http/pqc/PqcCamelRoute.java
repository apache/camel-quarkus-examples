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

/**
 * Simple route demonstrating Post-Quantum Cryptography (PQC) with TLS 1.3.
 */
@ApplicationScoped
public class PqcCamelRoute extends EndpointRouteBuilder {

    private static final String SECURE_RESPONSE = "Secure data delivered over mutual TLS.\n\n" +
            "Connection details:\n" +
            "- TLS key exchange: negotiated according to the server's PQC policy\n" +
            "- Provider: SunJSSE (JDK 25, no Bouncy Castle)\n\n" +
            "When X25519MLKEM768 is negotiated, it protects the session key against\n" +
            "harvest-now-decrypt-later attacks. Authentication is classical (RSA).";

    @Override
    public void configure() throws Exception {
        // API endpoint for secure data delivery over PQC-enabled TLS
        // Client certificates are validated at TLS layer (quarkus.http.ssl.client-auth=required)
        from(platformHttp("/pqc/secure"))
                .routeId("pqc-secure")
                .log("Serving secure data via PQC-enabled TLS connection")
                .setBody(constant(SECURE_RESPONSE))
                .setHeader("Content-Type", constant("text/plain; charset=utf-8"));
    }
}
