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
package org.apache.camel.example.saga;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Testcontainers resource for Saga integration tests.
 * Manages the lifecycle of LRA Coordinator and Artemis broker.
 */
public class SagaTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String LRA_IMAGE = "quay.io/jbosstm/lra-coordinator:latest";
    private static final String ARTEMIS_IMAGE = "quay.io/artemiscloud/activemq-artemis-broker:latest";
    private static final int LRA_PORT = 8080;
    private static final int ARTEMIS_PORT = 61616;

    private GenericContainer<?> lraContainer;
    private GenericContainer<?> artemisContainer;
    private Network network;

    @Override
    public Map<String, String> start() {
        network = Network.newNetwork();

        // Start Artemis broker
        artemisContainer = new GenericContainer<>(ARTEMIS_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("artemis")
                .withEnv("AMQ_USER", "admin")
                .withEnv("AMQ_PASSWORD", "admin")
                .withExposedPorts(ARTEMIS_PORT)
                .waitingFor(Wait.forListeningPort()
                        .withStartupTimeout(Duration.ofSeconds(60)));

        artemisContainer.start();

        // Start LRA Coordinator
        lraContainer = new GenericContainer<>(LRA_IMAGE)
                .withNetwork(network)
                .withNetworkAliases("lra-coordinator")
                .withEnv("QUARKUS_HTTP_PORT", String.valueOf(LRA_PORT))
                .withExposedPorts(LRA_PORT)
                .withExtraHost("host.testcontainers.internal", "host-gateway")
                .waitingFor(Wait.forHttp("/lra-coordinator")
                        .forPort(LRA_PORT)
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(90)));

        lraContainer.start();

        Map<String, String> config = new HashMap<>();

        // Artemis configuration
        String artemisUrl = String.format("tcp://%s:%d",
                artemisContainer.getHost(),
                artemisContainer.getMappedPort(ARTEMIS_PORT));
        config.put("quarkus.artemis.url", artemisUrl);
        config.put("quarkus.artemis.username", "admin");
        config.put("quarkus.artemis.password", "admin");

        // LRA configuration
        String lraCoordinatorUrl = String.format("http://%s:%d",
                lraContainer.getHost(),
                lraContainer.getMappedPort(LRA_PORT));
        config.put("camel.lra.coordinator-url", lraCoordinatorUrl);

        // Set local participant URL - use host.testcontainers.internal for coordinator callbacks
        config.put("camel.lra.local-participant-url", "http://host.testcontainers.internal:8084/api");

        // Allow external connections
        config.put("quarkus.http.host", "0.0.0.0");

        return config;
    }

    @Override
    public void stop() {
        if (artemisContainer != null) {
            artemisContainer.stop();
        }
        if (lraContainer != null) {
            lraContainer.stop();
        }
        if (network != null) {
            network.close();
        }
    }
}
