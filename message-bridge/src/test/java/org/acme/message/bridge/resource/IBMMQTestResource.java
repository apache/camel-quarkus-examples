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
package org.acme.message.bridge.resource;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

public class IBMMQTestResource implements QuarkusTestResourceLifecycleManager {
    private static final String IMAGE_NAME = "icr.io/ibm-messaging/mq:9.4.0.5-r1";
    private static final int PORT = 1414;
    private static final String QUEUE_MANAGER_NAME = "QM1";
    private static final String USER = "app";
    private static final String PASSWORD = "passw0rd";
    private static final String MESSAGING_CHANNEL = "DEV.APP.SVRCONN";
    private GenericContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new GenericContainer<>(DockerImageName.parse(IMAGE_NAME))
                .withExposedPorts(PORT)
                .withEnv(Map.of(
                        "LICENSE", ConfigProvider.getConfig().getValue("ibm.mq.container.license", String.class),
                        "MQ_QMGR_NAME", QUEUE_MANAGER_NAME))
                .withCopyToContainer(Transferable.of(PASSWORD), "/run/secrets/mqAdminPassword")
                .withCopyToContainer(Transferable.of(PASSWORD), "/run/secrets/mqAppPassword")
                // AMQ5806I is a message code for queue manager start
                .waitingFor(Wait.forLogMessage(".*AMQ5806I.*", 1));
        container.start();

        return Map.of(
                "ibm.mq.host", container.getHost(),
                "ibm.mq.port", container.getMappedPort(PORT).toString(),
                "ibm.mq.user", USER,
                "ibm.mq.password", PASSWORD,
                "ibm.mq.queueManagerName", QUEUE_MANAGER_NAME,
                "ibm.mq.channel", MESSAGING_CHANNEL);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
