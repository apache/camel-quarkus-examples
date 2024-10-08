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
package org.acme.fhir;

import java.time.Duration;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class FHIRTestResource implements QuarkusTestResourceLifecycleManager {
    private static final int CONTAINER_PORT = 8080;
    private GenericContainer container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>("docker.io/hapiproject/hapi:v7.4.0")
                .withExposedPorts(CONTAINER_PORT)
                .withEnv("hapi.fhir.fhir_version", "R4")
                .withEnv("hapi.fhir.allow_multiple_delete", "true")
                .withEnv("hapi.fhir.reuse_cached_search_results_millis", "-1")
                .waitingFor(Wait.forHttp("/fhir/metadata").withStartupTimeout(Duration.ofMinutes(5)));

        container.start();

        return Map.of("camel.component.fhir.server-url",
                "http://%s:%s/fhir".formatted(container.getHost(), container.getMappedPort(CONTAINER_PORT)));
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
