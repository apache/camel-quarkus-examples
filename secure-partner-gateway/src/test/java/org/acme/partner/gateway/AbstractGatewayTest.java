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
package org.acme.partner.gateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Base class of the gateway tests with the helpers they share: Keycloak tokens, order requests, parallel
 * requests and audit queries. The clients, secrets and users come from config/gateway-realm.json.
 * Each partner token carries the tenant of its client, the auditor token carries the audit role.
 */
abstract class AbstractGatewayTest {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String ORDER = "{\"sku\":\"WIDGET-1\",\"quantity\":2}";

    // An instance field, so that @QuarkusIntegrationTest can hand it the Dev Services configuration
    final KeycloakTestClient keycloak = new KeycloakTestClient();

    String acmeToken() {
        return keycloak.getClientAccessToken("partner-acme", "acme-test-secret");
    }

    String globexToken() {
        return keycloak.getClientAccessToken("partner-globex", "globex-test-secret");
    }

    String auditorToken() {
        return keycloak.getAccessToken("aaron", "aaron-test-password", "audit-console", null);
    }

    static String newKey() {
        return UUID.randomUUID().toString();
    }

    static Response postOrder(String token, String idempotencyKey, String body) {
        RequestSpecification request = given().auth().oauth2(token).contentType(ContentType.JSON).body(body);
        if (idempotencyKey != null) {
            request.header(IDEMPOTENCY_KEY, idempotencyKey);
        }
        return request.post("/api/orders");
    }

    static String createOrder(String token) {
        return postOrder(token, newKey(), ORDER).then().statusCode(202).extract().path("orderId");
    }

    /**
     * Sends {@code count} requests at the same time.
     */
    static List<Response> concurrently(int count, Supplier<Response> request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.get();
                }));
            }
            start.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Audit records, newest first, for example {@code audit("?action=ORDER_ACCEPTED")}.
     */
    List<Map<String, Object>> audit(String query) {
        return given().auth().oauth2(auditorToken())
                .get("/api/audit" + query)
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
    }

    /**
     * The seq of the newest audit record, to look only at records written after it.
     */
    long lastSeq() {
        return ((Number) audit("?limit=1").get(0).get("seq")).longValue();
    }
}
