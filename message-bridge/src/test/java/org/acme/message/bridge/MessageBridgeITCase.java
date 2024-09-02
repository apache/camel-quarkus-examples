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
package org.acme.message.bridge;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import org.acme.message.bridge.resource.IBMMQTestResource;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusIntegrationTest
@QuarkusTestResource(IBMMQTestResource.class)
// The crash test will kill the app, so it must be executed last as there is no way to restart the application
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MessageBridgeITCase {
    private static final File LOG_FILE = new File("target/quarkus.log");

    @Test
    @Order(1)
    public void shouldSendMessageToActiveMQTest() {
        final String message = RandomStringUtils.randomAlphabetic(8);
        RestAssured
                .given()
                .body(message)
                .post("/message");

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(Files.readString(LOG_FILE.toPath())).contains("ActiveMQ received: " + message));
    }

    @Test
    @Order(2)
    public void shouldRollbackMessageTest() {
        final String message = RandomStringUtils.randomAlphabetic(8) + " rollback";
        RestAssured
                .given()
                .body(message)
                .post("/message");

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(Files.readString(LOG_FILE.toPath()))
                        .containsSubsequence(
                                "Sending message from IBMMQ to ActiveMQ: " + message,
                                "Simulated rollback",
                                "Redelivering message after rollback to ActiveMQ: " + message,
                                "ActiveMQ received: " + message));
    }

    @Test
    @Order(3)
    public void shouldCrashTest() {
        final String message = RandomStringUtils.randomAlphabetic(8) + " crash";
        RestAssured
                .given()
                .body(message)
                .post("/message");

        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(Files.readString(LOG_FILE.toPath())).contains("Crashing the system"));
        File tmDir = Paths
                .get(ConfigProvider.getConfig().getValue("quarkus.transaction-manager.object-store.directory", String.class),
                        "ShadowNoFileLockStore", "defaultStore", "StateManager", "BasicAction", "TwoPhaseCoordinator",
                        "AtomicAction")
                .toFile();
        File dummyXaDir = new File(ConfigProvider.getConfig().getValue("dummy.resource.directory", String.class));

        assertThat(tmDir.list()).hasSize(1);
        assertThat(dummyXaDir.list()).hasSize(1);
    }
}
