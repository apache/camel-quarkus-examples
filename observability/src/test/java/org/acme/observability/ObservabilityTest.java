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
package org.acme.observability;

import java.util.Arrays;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class ObservabilityTest {

    // Management interface is listening on 9001
    protected String getManagementPrefix() {
        return "http://localhost:9001";
    }

    @Test
    public void greeting() {
        RestAssured.get("/greeting")
                .then()
                .statusCode(200);
    }

    @Test
    public void metrics() {
        // Verify Camel metrics are available
        String prometheusMetrics = RestAssured
                .get(getManagementPrefix() + "/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .body().asString();

        assertEquals(3,
                Arrays.stream(prometheusMetrics.split("\n")).filter(line -> line.contains("purpose=\"example\"")).count());
    }

    @Test
    public void health() {
        // Verify liveness
        RestAssured.get(getManagementPrefix() + "/q/health/live")
                .then()
                .statusCode(200)
                .body("status", is("UP"),
                        "checks.findAll { it.name == 'custom-liveness-check' }.status", Matchers.contains("UP"));

        // Verify readiness
        RestAssured.get(getManagementPrefix() + "/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", is("UP"),
                        "checks.findAll { it.name == 'custom-readiness-check' }.status", Matchers.contains("UP"),
                        "checks.findAll { it.name == 'Uptime readiness check' }.status", Matchers.contains("UP"),
                        "checks.findAll { it.name == 'context' }.status", Matchers.contains("UP"),
                        "checks.findAll { it.name == 'camel-routes' }.status", Matchers.contains("UP"),
                        "checks.findAll { it.name == 'camel-consumers' }.status", Matchers.contains("UP"));
    }
}
