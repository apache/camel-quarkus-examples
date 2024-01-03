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
package org.acme.jpa.idempotent.repository;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.camel.util.CollectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.utility.TestcontainersConfiguration;

/**
 * The derby test resource starts a derby container. It uses fixed port number 1527.
 */
public class DerbyTestResource<T extends GenericContainer> implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DerbyTestResource.class);
    private static final String DERBY_IMAGE_NAME = "az82/docker-derby:10.16";
    private static final int DERBY_PORT = 1527;

    private GenericContainer container;

    @Override
    public Map<String, String> start() {

        LOGGER.info(TestcontainersConfiguration.getInstance().toString());

        try {
            container = new GenericContainer(DERBY_IMAGE_NAME)
                    .withExposedPorts(DERBY_PORT)
                    .withCopyFileToContainer(MountableFile.forClasspathResource("init.sql"), "/init.sql")
                    .waitingFor(Wait.forListeningPort());
            container.start();

            container.execInContainer("java", "-Djdbc.drivers=org.apache.derbbc.EmbeddedDriver",
                    "org.apache.derby.tools.ij", "/init.sql");

            String url = "jdbc:derby://%s:%d/my-db".formatted(container.getHost(), container.getMappedPort(DERBY_PORT));
            return CollectionHelper.mapOf(
                    "quarkus.datasource.jdbc.url", url,
                    "timer.period", "100",
                    "timer.delay", "0",
                    "timer.repeatCount", "4");
        } catch (Exception e) {
            LOGGER.error("An error occurred while starting the derby container", e);
            throw new RuntimeException(e);
        }
    }

    protected void startContainer() {
        container.start();
    }

    @Override
    public void stop() {
        try {
            if (container != null) {
                container.stop();
            }
        } catch (Exception e) {
            // ignored
        }
    }
}
