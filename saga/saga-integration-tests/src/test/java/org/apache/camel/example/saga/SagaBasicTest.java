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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.awaitility.Awaitility;
import org.jboss.logmanager.Logger;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic integration tests for Saga example.
 * Tests verify saga orchestration, service participation, routing, and compensation.
 * Note: Payment service has 15% random failure rate to test compensation scenarios.
 */
@QuarkusTest
@QuarkusTestResource(SagaTestResource.class)
public class SagaBasicTest {
    private static final String LOG_FILE = "target/quarkus.log";
    private static final Logger LOG = Logger.getLogger(SagaBasicTest.class.getName());

    /**
     * Test saga orchestration with LRA - accepts both success and compensation outcomes.
     * Payment service has 15% random failure rate, so either scenario is valid.
     */
    @Test
    public void testSagaWithLRAAndRandomOutcomes() throws Exception {
        // Trigger saga asynchronously (may timeout on payment failure, which is expected)
        CompletableFuture.runAsync(() -> {
            try {
                RestAssured.given()
                        .queryParam("id", 1)
                        .post("/api/saga");
            } catch (Exception e) {
                // Expected - request may timeout on payment failure
            }
        });

        // Wait for saga to start and process fully
        // In native mode, we need to wait longer for all messages to be logged
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
            String log = Files.readString(Paths.get(LOG_FILE));
            assertTrue(log.contains("Executing saga #1"), "Saga should start with LRA");

            // Wait until we see evidence of completion (success or failure)
            boolean completed = log.contains("done for order #1")
                    || log.contains("fails!")
                    || log.contains("cancelled");
            assertTrue(completed, "Saga should complete with either success or compensation");
        });

        Awaitility.await()
                .pollDelay(Duration.ofSeconds(1))
                .pollInterval(Duration.ofMillis(250))
                .atMost(Duration.ofMinutes(1))
                .untilAsserted(() -> {
                    String log = Files.readString(Paths.get(LOG_FILE));

                    // Verify LRA coordinator is used
                    assertTrue(log.contains("lra-coordinator"), "Should use LRA coordinator");

                    // Verify services participated
                    assertTrue(log.contains("Buying train") || log.contains("Buying flight"),
                            "Services should participate");

                    // Check outcome - either success or compensation is valid
                    boolean hasSuccess = log.contains("done for order #1");
                    boolean hasFailure = log.contains("fails!");
                    boolean hasCompensation = log.contains("cancelled");

                    if (hasFailure || hasCompensation) {
                        LOG.info("Saga #1: Compensation scenario tested (payment failed, saga rolled back)");
                    } else if (hasSuccess) {
                        LOG.info("Saga #1: Success scenario tested (all payments completed)");
                    }

                    // Either outcome is valid because the trigger for saga compensation is triggered at random
                    assertTrue(hasSuccess || hasFailure,
                            "Saga should complete with either success or compensation");
                });
    }
}
