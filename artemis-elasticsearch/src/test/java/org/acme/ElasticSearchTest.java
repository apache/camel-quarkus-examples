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

package org.acme;

import java.io.IOException;
import java.time.Duration;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.acme.resource.CustomPahoTestResource;
import org.acme.resource.ElasticSearchTestResource;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(CustomPahoTestResource.class)
@QuarkusTestResource(ElasticSearchTestResource.class)
class ElasticSearchTest {

    @Test
    void testPahoToElasticSearch() throws IOException, InterruptedException {
        // Index some data
        for (int i = 1; i <= 5; i++) {
            given().body("device-" + i)
                    .when()
                    .post("/devices")
                    .then()
                    .statusCode(200);
        }

        // Retrieve data
        Awaitility.await().pollInterval(Duration.ofMillis(250)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            given().get("/devices")
                    .then()
                    .statusCode(200)
                    .body("size() ", is(5));
        });
    }
}
