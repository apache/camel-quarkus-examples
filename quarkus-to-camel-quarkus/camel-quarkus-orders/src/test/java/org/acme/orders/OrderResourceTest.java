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

import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class OrderResourceTest {

    @Test
    void testCreateAndListOrders() {
        given()
                .contentType("application/json")
                .body("""
                        {"id":"ORD-001","customer":"Acme Corp","type":"priority","amount":1500.00}
                        """)
                .when().post("/api/orders")
                .then()
                .statusCode(201)
                .body("id", is("ORD-001"))
                .body("customer", is("Acme Corp"));

        given()
                .when().get("/api/orders")
                .then()
                .statusCode(200)
                .body("id", hasItem("ORD-001"));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> given()
                .when().get("/api/loyalty/count")
                .then()
                .statusCode(200)
                .body(is("1")));
    }
}
