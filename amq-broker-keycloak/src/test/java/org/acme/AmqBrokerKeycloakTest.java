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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class AmqBrokerKeycloakTest {

    private static final String SAMPLE_ORDER_JSON = "{\"orderId\":\"ORDER-001\",\"customerId\":\"CUST-001\",\"productId\":\"PRODUCT-001\",\"quantity\":5}";

    KeycloakTestClient keycloakClient = new KeycloakTestClient();
    private static String clientId;

    @BeforeAll
    public static void setUp() {
        clientId = ConfigProvider.getConfig().getValue("quarkus.oidc.client-id", String.class);
    }

    @Test
    public void orderSubmissionShouldRequireAuthentication() {
        // Without authentication token, should get 401 Unauthorized
        given()
                .header("Content-Type", "application/json")
                .body(SAMPLE_ORDER_JSON)
                .when().post("/api/orders/submit")
                .then()
                .statusCode(Response.Status.UNAUTHORIZED.getStatusCode());
    }

    @Test
    public void orderSubmissionWithAuthenticationShouldSucceed() {
        // With valid customer token, order submission should succeed
        // This validates the complete flow: REST → Keycloak auth → JMS → Processor → SOAP → Inventory update
        given()
                .auth().oauth2(getCustomerAccessToken())
                .header("Content-Type", "application/json")
                .body(SAMPLE_ORDER_JSON)
                .when().post("/api/orders/submit")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body("success", equalTo(true))
                .body("newStock", equalTo(95));
    }

    @Test
    public void adminEndpointShouldRequireAdminRole() {
        // Customer user should get 403 Forbidden (authenticated but not authorized)
        given()
                .auth().oauth2(getCustomerAccessToken())
                .when().get("/api/admin/users")
                .then()
                .statusCode(Response.Status.FORBIDDEN.getStatusCode());
    }

    @Test
    public void adminEndpointShouldWorkForAdminUser() {
        // Admin user should get 200 OK with user list
        String response = given()
                .auth().oauth2(getAdminAccessToken())
                .when().get("/api/admin/users")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .extract().asString();

        // Verify the response contains user data (should be JSON array)
        assertTrue(
                response.contains("customer") || response.contains("admin"),
                "Response should contain user list: " + response);
    }

    private String getCustomerAccessToken() {
        return keycloakClient.getAccessToken("customer", "customer-pass", clientId);
    }

    private String getAdminAccessToken() {
        return keycloakClient.getAccessToken("admin", "admin-pass", clientId);
    }
}
