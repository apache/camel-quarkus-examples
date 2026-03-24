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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic integration tests for Saga example.
 * Tests verify saga orchestration, service participation, and routing.
 */
@QuarkusTest
@QuarkusTestResource(SagaTestResource.class)
public class SagaBasicTest {

    private static final String LOG_FILE = "target/quarkus.log";

    /**
     * Test that saga orchestration starts successfully with LRA.
     */
    @Test
    public void testSagaOrchestrationStarts() throws Exception {
        String lraId = RestAssured.given()
                .queryParam("id", 1)
                .post("/api/saga")
                .then()
                .extract()
                .body()
                .asString();

        assertNotNull(lraId, "LRA ID should be returned");
        assertTrue(lraId.contains("lra-coordinator"), "LRA ID should contain coordinator URL");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String log = new String(Files.readAllBytes(Paths.get(LOG_FILE)), StandardCharsets.UTF_8);
            assertTrue(log.contains("Executing saga #1 with LRA"), "Saga should start with LRA");
        });
    }

    /**
     * Test that train service participates in the saga.
     */
    @Test
    public void testTrainServiceParticipation() throws Exception {
        RestAssured.given()
                .queryParam("id", 2)
                .post("/api/saga");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String log = new String(Files.readAllBytes(Paths.get(LOG_FILE)), StandardCharsets.UTF_8);
            assertTrue(log.contains("Buying train #2"), "Train service should participate");
            assertTrue(log.contains("Paying train for order #2"), "Payment should process train");
        });
    }

    /**
     * Test that flight service participates in the saga.
     */
    @Test
    public void testFlightServiceParticipation() throws Exception {
        RestAssured.given()
                .queryParam("id", 3)
                .post("/api/saga");

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            String log = new String(Files.readAllBytes(Paths.get(LOG_FILE)), StandardCharsets.UTF_8);
            assertTrue(log.contains("Buying flight #3"), "Flight service should participate");
            assertTrue(log.contains("Paying flight for order #3"), "Payment should process flight");
        });
    }

    /**
     * Test complete saga flow with all services.
     */
    @Test
    public void testCompleteSagaFlow() throws Exception {
        RestAssured.given()
                .queryParam("id", 4)
                .post("/api/saga");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            String log = new String(Files.readAllBytes(Paths.get(LOG_FILE)), StandardCharsets.UTF_8);

            assertTrue(log.contains("Executing saga #4"), "Saga should start");
            assertTrue(log.contains("Buying train #4"), "Train booking should occur");
            assertTrue(log.contains("Buying flight #4"), "Flight booking should occur");
            assertTrue(log.contains("Paying train for order #4"), "Train payment should process");
            assertTrue(log.contains("Paying flight for order #4"), "Flight payment should process");
        });
    }
}
