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
package org.acme.timer.log;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class BearerTokenAuthenticationTest {

    KeycloakTestClient keycloakClient = new KeycloakTestClient();

    private static String clientId;

    private final int OK = 200;
    private final int UNAUTHORIZED = 401;
    private final int FORBIDDEN = 403;

    @BeforeAll
    public static void setUp() {
        clientId = ConfigProvider.getConfig().getValue("quarkus.oidc.client-id", String.class);
    }

    @Test
    public void testAuthorized() {
        RestAssured.given().auth().oauth2(getBossAccessToken())
                .when().get("/secured/authorized")
                .then()
                .statusCode(OK);

        RestAssured.given().auth().oauth2(getEmployeeAccessToken())
                .when().get("/secured/authorized")
                .then()
                .statusCode(FORBIDDEN);
    }

    @Test
    public void testAuthenticated() {
        RestAssured.given().auth().oauth2(getBossAccessToken())
                .when().get("/secured/authenticated")
                .then()
                .statusCode(OK);

        RestAssured.given().auth().oauth2(getEmployeeAccessToken())
                .when().get("/secured/authenticated")
                .then()
                .statusCode(OK);
    }

    @Test
    public void testNotAuthenticated() {
        RestAssured.given()
                .when().get("/not-secured")
                .then()
                .statusCode(OK);

        RestAssured.given()
                .when().get("/secured/authenticated")
                .then()
                .statusCode(UNAUTHORIZED);
        RestAssured.given()
                .when().get("/secured/authorized")
                .then()
                .statusCode(UNAUTHORIZED);
    }

    protected String getBossAccessToken() {
        return keycloakClient.getAccessToken("boss", "boss-pass", clientId);
    }

    protected String getEmployeeAccessToken() {
        return keycloakClient.getAccessToken("employee", "employee-pass", clientId);
    }

}
