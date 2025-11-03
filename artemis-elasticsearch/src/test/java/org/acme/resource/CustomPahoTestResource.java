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

package org.acme.resource;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class CustomPahoTestResource implements QuarkusTestResourceLifecycleManager {
    private static final String IMAGE_NAME = "quay.io/artemiscloud/activemq-artemis-broker:1.0.26";
    private static final String AMQ_USER = "admin";
    private static final String AMQ_PASSWORD = "admin";
    private static final String AMQ_EXTRA_ARGS = "--relax-jolokia --no-autotune --mapped --no-fsync --java-options=-Dbrokerconfig.maxDiskUsage=-1";
    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(DockerImageName.parse(IMAGE_NAME))
                .withExposedPorts(61616, 8161, 1883)
                .withEnv("AMQ_USER", AMQ_USER)
                .withEnv("AMQ_PASSWORD", AMQ_PASSWORD)
                .withEnv("AMQ_EXTRA_ARGS", AMQ_EXTRA_ARGS)
                .withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()));

        container.start();

        return Map.of(
                "camel.component.paho.brokerUrl", "tcp://" + container.getHost() + ":" + container.getMappedPort(1883));

    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
