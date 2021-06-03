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
package org.apache.camel.example;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.strimzi.StrimziKafkaContainer;
import org.apache.camel.util.CollectionHelper;

public class StrimziTestResource implements QuarkusTestResourceLifecycleManager {

    private StrimziKafkaContainer strimziKafkaContainer;
    private static final int KAFKA_PORT = 9092;
    private static final String KAFKA_STRIMZI_VERSION = "0.20.1-kafka-2.5.0";

    @Override
    public Map<String, String> start() {
        strimziKafkaContainer = new StrimziKafkaContainer(KAFKA_STRIMZI_VERSION);
        strimziKafkaContainer.start();

        String bootstrap_servers = strimziKafkaContainer.getContainerIpAddress() + ":"
                + strimziKafkaContainer.getMappedPort(KAFKA_PORT);

        return CollectionHelper.mapOf(
                "camel.component.kafka.brokers", bootstrap_servers);
    }

    @Override
    public void stop() {
        if (strimziKafkaContainer != null) {
            strimziKafkaContainer.stop();
        }
    }
}
