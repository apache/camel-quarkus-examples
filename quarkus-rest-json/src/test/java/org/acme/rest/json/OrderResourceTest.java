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
package org.acme.rest.json;

import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.*;

@QuarkusTest
public class OrderResourceTest {

    private static final String ORDER_ENDPOINT = "/api/orders";

    /**
     * Checks if an order has been processed by querying the test endpoint.
     * Works in both JVM and native modes.
     *
     * @param  orderId the order ID to check
     * @return         true if the order has been processed
     */
    protected boolean isOrderProcessed(String orderId) {
        return given()
                .log().ifValidationFails() // Only log on failure
                .when()
                .get(ORDER_ENDPOINT + "/isProcessed/" + orderId)
                .then()
                .statusCode(200)
                .extract()
                .as(Boolean.class);
    }

    private static String createOrderJson(String customerId) {
        return "{" +
                "\"customerId\": \"" + customerId + "\"," +
                "\"items\": [" +
                "{" +
                "\"productId\": \"PROD001\"," +
                "\"productName\": \"Widget A\"," +
                "\"quantity\": 1," +
                "\"price\": 50.00" +
                "}," +
                "{" +
                "\"productId\": \"PROD002\"," +
                "\"productName\": \"Widget B\"," +
                "\"quantity\": 2," +
                "\"price\": 25.00" +
                "}" +
                "]" +
                "}";
    }

    /**
     * Test order creation with GOLD customer (10% discount).
     * Subtotal: $100.00
     * Discount: 10% = $10.00
     * Expected Total: $90.00
     */
    @Test
    public void testGoldCustomerDiscount() {
        String orderJson = createOrderJson("CUST001");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(orderJson)
                .when()
                .post(ORDER_ENDPOINT)
                .then()
                .statusCode(201)
                .body("confirmed", equalTo(true))
                .body("orderId", notNullValue())
                .body("totalAmount", equalTo(90.00f))
                .extract()
                .response();

        String orderId = response.jsonPath().getString("orderId");

        // Verify order was processed via Kafka (works in both JVM and native modes)
        await()
                .atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> isOrderProcessed(orderId));
    }

    /**
     * Test order creation with PLATINUM customer (15% discount).
     * Subtotal: $100.00
     * Discount: 15% = $15.00
     * Expected Total: $85.00
     */
    @Test
    public void testPlatinumCustomerDiscount() {
        String orderJson = createOrderJson("CUST004");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(orderJson)
                .when()
                .post(ORDER_ENDPOINT)
                .then()
                .statusCode(201)
                .body("confirmed", equalTo(true))
                .body("orderId", notNullValue())
                .body("totalAmount", equalTo(85.00f))
                .extract()
                .response();

        String orderId = response.jsonPath().getString("orderId");

        // Verify order was processed via Kafka (works in both JVM and native modes)
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> isOrderProcessed(orderId));
    }

    /**
     * Test order creation with STANDARD customer (no discount).
     * Subtotal: $100.00
     * Discount: 0%
     * Expected Total: $100.00
     */
    @Test
    public void testStandardCustomerNoDiscount() {
        String orderJson = createOrderJson("CUST999");

        Response response = given()
                .contentType(ContentType.JSON)
                .body(orderJson)
                .when()
                .post(ORDER_ENDPOINT)
                .then()
                .statusCode(201)
                .body("confirmed", equalTo(true))
                .body("orderId", notNullValue())
                .body("totalAmount", equalTo(100.00f))
                .extract()
                .response();

        String orderId = response.jsonPath().getString("orderId");

        // Verify order was processed via Kafka (works in both JVM and native modes)
        await()
                .atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> isOrderProcessed(orderId));
    }
}
