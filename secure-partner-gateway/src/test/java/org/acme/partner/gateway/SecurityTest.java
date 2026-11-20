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

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the security of the gateway: token checks, roles, tenant isolation and what responses may expose.
 * <ul>
 * <li>missing, tampered, expired, foreign-issuer and wrong-audience tokens get 401</li>
 * <li>wrong role, unlisted paths and tokens without a tenant get 403</li>
 * <li>a partner cannot read the orders of another partner, the tenant comes from the token only</li>
 * <li>responses do not echo request headers, and metrics do not carry order ids or random paths</li>
 * </ul>
 * Without these checks a partner could see or change data of another partner, or learn internals of the gateway.
 */
@QuarkusTest
@DisabledIfSystemProperty(named = "skip-testcontainers-tests", matches = ".*", disabledReason = "Requires containers")
class SecurityTest extends AbstractGatewayTest {

    // Root of the management interface, set for both test modes
    @TestHTTPResource(value = "", management = true)
    URL management;

    @Test
    void requestWithoutTokenIsUnauthorized() {
        given().get("/api/orders/" + UUID.randomUUID()).then().statusCode(401);
        given().header(IDEMPOTENCY_KEY, newKey()).contentType("application/json").body(ORDER)
                .post("/api/orders").then().statusCode(401);
    }

    @Test
    void garbageAndTamperedTokensAreUnauthorized() {
        // Changing a claim, for example the tenant, breaks the signature
        String[] parts = acmeToken().split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"acme\"", "\"globex\"");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        for (String token : new String[] { "garbage", tampered }) {
            given().auth().oauth2(token).get("/api/orders/" + UUID.randomUUID()).then().statusCode(401);
        }
    }

    /**
     * A valid token is first refused with 403 (no role), then with 401 after it expired.
     */
    @Test
    void expiredTokenIsUnauthorized() {
        // short-lived-client tokens expire after 3 seconds and carry no role
        String token = keycloak.getClientAccessToken("short-lived-client", "short-lived-test-secret");
        given().auth().oauth2(token).get("/api/audit").then().statusCode(403);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> given().auth().oauth2(token).get("/api/audit").then().statusCode(401));
    }

    @Test
    void tokenFromAnotherIssuerIsUnauthorized() {
        // Keycloak in dev mode takes the issuer from the request URL: the token is signed by the right key,
        // but its issuer is not the configured one
        URI authServer = URI.create(keycloak.getAuthServerUrl());
        String otherHost = "localhost".equals(authServer.getHost()) ? "127.0.0.1" : "localhost";
        String otherIssuer = authServer.toString().replace("//" + authServer.getHost() + ":", "//" + otherHost + ":");
        String token = new KeycloakTestClient(otherIssuer).getClientAccessToken("partner-acme", "acme-test-secret");

        given().auth().oauth2(token).get("/api/orders/" + UUID.randomUUID()).then().statusCode(401);
    }

    @Test
    void tokenForAnotherAudienceIsUnauthorized() {
        // admin-cli is a built-in Keycloak client, its tokens are not issued for gateway-api
        String token = keycloak.getAccessToken("aaron", "aaron-test-password", "admin-cli", null);

        given().auth().oauth2(token).get("/api/audit").then().statusCode(401);
    }

    /**
     * A partner cannot read the audit log, and the auditor cannot create orders: 403.
     */
    @Test
    void wrongRoleIsForbidden() {
        given().auth().oauth2(acmeToken()).get("/api/audit").then().statusCode(403);
        given().auth().oauth2(acmeToken()).get("/api/audit/verify").then().statusCode(403);
        postOrder(auditorToken(), newKey(), ORDER).then().statusCode(403);
    }

    /**
     * Anything that is not an explicitly allowed path and method is answered with 403.
     */
    @Test
    void unlistedPathsAndMethodsAreDenied() {
        String token = acmeToken();
        given().auth().oauth2(token).get("/api/orders").then().statusCode(403);
        given().auth().oauth2(token).delete("/api/orders/" + UUID.randomUUID()).then().statusCode(403);
        given().auth().oauth2(token).get("/api/unknown").then().statusCode(403);
    }

    @Test
    void healthAndMetricsAreOnTheManagementPortOnly() throws Exception {
        URI managementRoot = management.toURI();
        given().get(managementRoot.resolve("/observe/health")).then().statusCode(200);
        given().get(managementRoot.resolve("/observe/metrics"))
                .then().statusCode(200)
                .body(containsString("audit_events_total"));

        given().auth().oauth2(acmeToken()).get("/observe/health").then().statusCode(403);
    }

    /**
     * Roles come from realm_access/roles only: a groups claim naming the auditor role grants nothing.
     */
    @Test
    void rolesAreNotReadFromTheGroupsClaim() {
        String token = keycloak.getClientAccessToken("groups-claim-client", "groups-claim-test-secret");

        given().auth().oauth2(token).get("/api/audit").then().statusCode(403);
    }

    /**
     * Requests without a valid token are counted, not appended to the audit chain: anonymous callers cannot make the
     * gateway write to the database.
     */
    @Test
    void anonymousRequestsAreNotAudited() throws Exception {
        String path = "/api/orders/" + UUID.randomUUID();
        for (int i = 0; i < 25; i++) {
            given().get(path).then().statusCode(401);
            given().auth().oauth2("garbage").get(path).then().statusCode(401);
        }

        // Quarkus security events are observed asynchronously, records would show up within this time
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5))
                .until(() -> audit("?actor=anonymous").isEmpty());
        given().get(management.toURI().resolve("/observe/metrics"))
                .then().statusCode(200)
                .body(containsString("gateway_rejected_requests_total{status=\"401\"}"));
    }

    /**
     * A partner token without a usable tenant gets 403 and an AUTHZ_DENIED audit record.
     */
    @Test
    void partnerTokenWithoutTenantIsForbidden() {
        // no partner_id claim, or a blank one that all such clients would share as their tenant
        String[][] clients = { { "partner-without-tenant", "without-tenant-test-secret" },
                { "partner-blank-tenant", "blank-tenant-test-secret" } };
        for (String[] client : clients) {
            String token = keycloak.getClientAccessToken(client[0], client[1]);
            postOrder(token, newKey(), ORDER).then().statusCode(403).contentType("application/problem+json");
            given().auth().oauth2(token).get("/api/orders/" + UUID.randomUUID()).then().statusCode(403);

            assertTrue(audit("?action=AUTHZ_DENIED&limit=500").stream()
                    .anyMatch(event -> ("service-account-" + client[0]).equals(event.get("actor"))
                            && "partner token without partner_id claim".equals(event.get("reason"))));
        }
    }

    /**
     * The order of another partner gives the same 404 as an unknown order, so its existence is not revealed.
     */
    @Test
    void partnerCannotSeeOrdersOfAnotherPartner() {
        String orderId = createOrder(acmeToken());

        String otherPartners = given().auth().oauth2(globexToken()).get("/api/orders/" + orderId)
                .then().statusCode(404).extract().asString();
        String unknown = given().auth().oauth2(globexToken()).get("/api/orders/" + UUID.randomUUID())
                .then().statusCode(404).extract().asString();
        // Nothing tells an existing order of another partner from an order that does not exist
        assertEquals(unknown, otherPartners);

        given().auth().oauth2(acmeToken()).get("/api/orders/" + orderId).then().statusCode(200);
    }

    /**
     * Tenant headers sent by the client are ignored: the order belongs to the partner of the token.
     */
    @Test
    void tenantComesFromTheTokenOnly() {
        String orderId = given().auth().oauth2(acmeToken())
                .header("X-Partner-Id", "globex")
                .header("partnerId", "globex")
                .header(IDEMPOTENCY_KEY, newKey())
                .contentType("application/json")
                .body(ORDER)
                .post("/api/orders")
                .then().statusCode(202).extract().path("orderId");

        given().auth().oauth2(globexToken()).get("/api/orders/" + orderId).then().statusCode(404);
        given().auth().oauth2(acmeToken()).get("/api/orders/" + orderId).then().statusCode(200);
    }

    /**
     * Accepted, rejected and read responses contain none of the request headers, cookies or the token.
     */
    @Test
    void responsesDoNotEchoRequestHeaders() {
        String token = acmeToken();
        Response accepted = given().auth().oauth2(token)
                .cookie("session", "secret-cookie")
                .header("X-Probe", "probe")
                .header(IDEMPOTENCY_KEY, newKey())
                .contentType("application/json")
                .body(ORDER)
                .post("/api/orders");
        Response rejected = given().auth().oauth2(token)
                .header("X-Probe", "probe")
                .header(IDEMPOTENCY_KEY, newKey())
                .contentType("application/json")
                .body("{\"sku\":\"WIDGET-1\"}")
                .post("/api/orders");
        Response read = given().auth().oauth2(token)
                .header("X-Probe", "probe")
                .get("/api/orders/" + accepted.path("orderId"));

        // Camel copies the remaining headers of the exchange into the response, the allow-list keeps them out
        for (Response response : new Response[] { accepted, rejected, read }) {
            response.then()
                    .header("X-Probe", nullValue())
                    .header("Authorization", nullValue())
                    .header("Cookie", nullValue())
                    .header(IDEMPOTENCY_KEY, nullValue())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(not(containsString(token)));
        }
        accepted.then().statusCode(202);
        rejected.then().statusCode(400);
        read.then().statusCode(200);
    }

    /**
     * Injected header values and query parameters do not reach the response headers, also on error responses.
     */
    @Test
    void injectedHeadersAreNotEchoed() {
        // Behind nginx, an echoed X-Accel-Redirect would make nginx answer with that internal path
        Map<String, String> injected = Map.of(
                "X-Accel-Redirect", "/internal/secret",
                "Set-Cookie", "injected=1",
                "Location", "https://evil.example",
                "Retry-After", "999");
        String withoutTenant = keycloak.getClientAccessToken("partner-without-tenant", "without-tenant-test-secret");

        // answered before the removeHeaders of the route
        Response noTenant = given().auth().oauth2(withoutTenant).headers(injected)
                .header(IDEMPOTENCY_KEY, newKey())
                .contentType("application/json")
                .body(ORDER)
                .post("/api/orders");
        Response malformed = given().auth().oauth2(acmeToken()).headers(injected)
                .header(IDEMPOTENCY_KEY, newKey())
                .contentType("application/json; injected=1")
                .body("{\"sku\":")
                .post("/api/orders");
        // inputs of the route, one with a line break that no response header can hold
        Response audit = given().auth().oauth2(auditorToken()).headers(injected)
                .queryParam("actor", "x\r\nSet-Cookie: injected=1")
                .queryParam("action", "Set-Cookie")
                .queryParam("limit", "5")
                .get("/api/audit");

        noTenant.then().statusCode(403).contentType("application/problem+json");
        malformed.then().statusCode(400).contentType("application/problem+json");
        // control characters are not valid in actor
        audit.then().statusCode(400).contentType("application/problem+json");
        for (Response response : new Response[] { noTenant, malformed, audit }) {
            for (String header : new String[] { "X-Accel-Redirect", "Set-Cookie", "Location", "Retry-After",
                    IDEMPOTENCY_KEY, "actor", "action", "limit" }) {
                response.then().header(header, nullValue());
            }
            response.then().header("Content-Type", not(containsString("injected")));
        }
    }

    /**
     * Metric tags use the path template or UNKNOWN, so ids and random paths cannot grow the metrics.
     */
    @Test
    void metricsDoNotTagOrderIdsOrRandomPaths() throws Exception {
        String orderId = createOrder(acmeToken());
        given().auth().oauth2(acmeToken()).get("/api/orders/" + orderId).then().statusCode(200);
        given().get("/api/orders/" + orderId).then().statusCode(401);
        String randomPath = "/api/" + UUID.randomUUID();
        given().get(randomPath).then().statusCode(401);
        given().auth().oauth2(acmeToken()).get(randomPath).then().statusCode(403);

        // the uri tag is the path template, or UNKNOWN for a 4xx on another path
        URI metrics = management.toURI().resolve("/observe/metrics");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> given().get(metrics)
                .then().statusCode(200)
                .body(containsString("status=\"401\",uri=\"/api/orders/{id}\""))
                .body(containsString("status=\"403\",uri=\"UNKNOWN\""))
                .body(not(containsString(orderId)))
                .body(not(containsString(randomPath))));
    }
}
