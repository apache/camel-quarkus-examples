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
package org.acme.orders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@QuarkusTest
class FileIngestionTest {

    static final Path INCOMING = Path.of("target/test-orders/incoming");
    static final Path DONE = Path.of("target/test-orders/done");

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(INCOMING);
    }

    @Test
    void testCsvFileIngestion() throws IOException {
        int initialCount = given()
                .when().get("/api/orders")
                .then().statusCode(200)
                .extract().body().as(new TypeRef<List<?>>() {
                }).size();

        Files.writeString(INCOMING.resolve("test-orders.csv"),
                """
                        id,customer,type,amount
                        ORD-100,Acme Corp,priority,1500.00
                        ORD-101,Wayne Enterprises,standard,250.00
                        """);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int count = given()
                    .when().get("/api/orders")
                    .then().statusCode(200)
                    .extract().body().as(new TypeRef<List<?>>() {
                    }).size();
            assertThat(count, greaterThanOrEqualTo(initialCount + 2));
        });

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> Files.exists(DONE.resolve("test-orders.csv")));
    }
}
