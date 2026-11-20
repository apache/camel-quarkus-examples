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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.DisabledOnIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.apache.camel.CamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the audit trail written by the gateway and its query API.
 * <ul>
 * <li>order actions, denied reads and rejected requests are recorded with actor, tenant and outcome</li>
 * <li>failures answered by Quarkus security (401, 403) and the Camel lifecycle are recorded too</li>
 * <li>invalid audit queries get 400, and the hash chain stays valid, also with concurrent appends</li>
 * </ul>
 * The audit trail is the evidence of who did what, so every relevant event must be in it.
 * <p>
 * One test (route stop and start) runs in JVM mode only: it stops a route through the CamelContext bean, and a
 * {@code @QuarkusIntegrationTest} (native or packaged mode) cannot inject beans, so the integration test class
 * reports it as skipped.
 */
@QuarkusTest
@DisabledIfSystemProperty(named = "skip-testcontainers-tests", matches = ".*", disabledReason = "Requires containers")
class AuditTrailTest extends AbstractGatewayTest {

    private static final String ACME = "service-account-partner-acme";
    private static final String GLOBEX = "service-account-partner-globex";

    /**
     * Create, read, denied read, rejected request and the audit query itself each leave one record.
     */
    @Test
    void ordersAreAudited() {
        long start = lastSeq();
        String orderId = createOrder(acmeToken());
        given().auth().oauth2(acmeToken()).get("/api/orders/" + orderId).then().statusCode(200);
        given().auth().oauth2(globexToken()).get("/api/orders/" + orderId).then().statusCode(404);
        postOrder(acmeToken(), newKey(), "{\"sku\":\"WIDGET-1\",\"quantity\":0}").then().statusCode(400);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Map<String, Object>> events = since(start);
            assertAudited(events, "ORDER_ACCEPTED", "order/" + orderId, ACME, "CLIENT", "acme", "SUCCESS");
            assertAudited(events, "ORDER_READ", "order/" + orderId, ACME, "CLIENT", "acme", "SUCCESS");
            assertAudited(events, "ORDER_READ_DENIED", "order/" + orderId, GLOBEX, "CLIENT", "globex", "DENIED");
            assertAudited(events, "REQUEST_REJECTED", "route/order-create", ACME, "CLIENT", "acme", "REJECTED");
            assertAudited(events, "AUDIT_QUERIED", "audit", "aaron", "USER", null, "SUCCESS");
        });
    }

    /**
     * A bad token and a missing token give AUTHN_FAILED and AUTHZ_DENIED, a wrong role gives AUTHZ_DENIED.
     */
    @Test
    void requestsRejectedByQuarkusAreAudited() {
        long start = lastSeq();
        String path = "/api/orders/" + UUID.randomUUID();
        given().auth().oauth2("garbage").get(path).then().statusCode(401);
        given().get(path).then().statusCode(401);
        given().auth().oauth2(acmeToken()).get("/api/audit/verify").then().statusCode(403);

        // Quarkus security events are observed asynchronously
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Map<String, Object>> events = since(start);
            assertAudited(events, "AUTHN_FAILED", "GET " + path, "anonymous", "ANONYMOUS", null, "DENIED");
            assertAudited(events, "AUTHZ_DENIED", "GET " + path, "anonymous", "ANONYMOUS", null, "DENIED");
            assertAudited(events, "AUTHZ_DENIED", "GET /api/audit/verify", ACME, "CLIENT", "acme", "DENIED");
        });
    }

    /**
     * The start of the context and of the order route are recorded as SYSTEM events.
     */
    @Test
    void camelLifecycleIsAudited() {
        List<Map<String, Object>> started = audit("?action=CONTEXT_STARTED");
        assertAudited(started, "CONTEXT_STARTED", "camel-context/secure-partner-gateway", "gateway", "SYSTEM", null,
                "SUCCESS");
        assertAudited(audit("?action=ROUTE_STARTED&limit=100"), "ROUTE_STARTED", "route/order-create", "gateway",
                "SYSTEM", null, "SUCCESS");
    }

    /**
     * Stopping and starting a route while the application runs is audited as ROUTE_STOPPED and ROUTE_STARTED.
     * Skipped in native and integration mode, which cannot reach the CamelContext bean of the application.
     */
    @Test
    @DisabledOnIntegrationTest("Stops a route through the CamelContext bean")
    void routeStoppedWhileRunningIsAudited() throws Exception {
        long start = lastSeq();
        CamelContext context = Arc.container().instance(CamelContext.class).get();
        context.getRouteController().stopRoute("audit-anchor");
        context.getRouteController().startRoute("audit-anchor");

        List<Map<String, Object>> events = since(start);
        assertAudited(events, "ROUTE_STOPPED", "route/audit-anchor", "gateway", "SYSTEM", null, "SUCCESS");
        assertAudited(events, "ROUTE_STARTED", "route/audit-anchor", "gateway", "SYSTEM", null, "SUCCESS");
    }

    /**
     * A limit that is not a number or is zero, an actor or action with control characters, and a repeated parameter
     * get 400.
     */
    @Test
    void invalidQueryIsRejected() {
        for (String query : new String[] { "limit=all", "limit=0", "actor=a%00b", "action=a%00b", "action=order",
                "actor=a&actor=b", "limit=1&limit=2" }) {
            given().auth().oauth2(auditorToken()).urlEncodingEnabled(false).get("/api/audit?" + query)
                    .then().statusCode(400)
                    .contentType("application/problem+json");
        }
    }

    /**
     * Filters are query parameters: HTTP headers with the same names are ignored.
     */
    @Test
    void filtersComeFromTheQueryOnly() {
        List<Map<String, Object>> events = given().auth().oauth2(auditorToken())
                .header("limit", "1")
                .header("action", "NO_SUCH_ACTION")
                .get("/api/audit")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertTrue(events.size() > 1, events::toString);
    }

    /**
     * Eight parallel orders all get 202 and /api/audit/verify still reports a valid chain.
     */
    @Test
    void concurrentAppendsKeepTheChainValid() throws Exception {
        String token = acmeToken();

        List<Response> responses = concurrently(8, () -> postOrder(token, newKey(), ORDER));

        assertTrue(responses.stream().allMatch(response -> response.statusCode() == 202),
                () -> responses.stream().map(Response::statusCode).toList().toString());
        given().auth().oauth2(auditorToken()).get("/api/audit/verify")
                .then().statusCode(200)
                .body("valid", is(true));
    }

    /**
     * /api/audit/verify reports a valid chain with checked records and no broken sequence number.
     */
    @Test
    void auditChainIsValid() {
        given().auth().oauth2(auditorToken()).get("/api/audit/verify")
                .then().statusCode(200)
                .body("valid", is(true))
                .body("checked", greaterThan(0))
                .body("$", not(hasKey("brokenAtSeq")));
    }

    private List<Map<String, Object>> since(long seq) {
        return audit("?limit=500").stream()
                .filter(event -> ((Number) event.get("seq")).longValue() > seq)
                .toList();
    }

    private static void assertAudited(List<Map<String, Object>> events, String action, String resource, String actor,
            String actorType, String partnerId, String outcome) {
        assertTrue(events.stream().anyMatch(event -> action.equals(event.get("action"))
                && resource.equals(event.get("resource"))
                && actor.equals(event.get("actor"))
                && actorType.equals(event.get("actorType"))
                && Objects.equals(partnerId, event.get("partnerId"))
                && outcome.equals(event.get("outcome"))),
                () -> "No " + action + " " + outcome + " record of " + actor + " for " + resource + " in " + events);
    }
}
