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
package org.acme.ai.tool.support.desk;

import java.util.List;
import java.util.Map;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Reads the state of the support desk through its HTTP API, as the tests cannot access its database directly when they
 * run against the packaged application.
 */
final class SupportDesk {

    private SupportDesk() {
    }

    /**
     * A request authenticated as one of the users of the tests, whose passwords are their user names, see
     * {@link SupportDeskTestResource}.
     */
    static RequestSpecification as(String user) {
        return given().auth().preemptive().basic(user, user);
    }

    /**
     * The return of an order of the customer, or {@code null} if the order has no return.
     */
    static Map<String, Object> returnOf(String customer, int orderId) {
        List<Map<String, Object>> returns = as(customer).get("/support/returns")
                .then()
                .statusCode(200)
                .extract().jsonPath().getList("$");
        return returns.stream()
                .filter(r -> ((Number) r.get("orderId")).intValue() == orderId)
                .findFirst()
                .orElse(null);
    }

    static String returnStatus(String customer, int orderId) {
        Map<String, Object> r = returnOf(customer, orderId);
        return r == null ? null : (String) r.get("status");
    }

    static int returnId(String customer, int orderId) {
        return ((Number) returnOf(customer, orderId).get("returnId")).intValue();
    }

    static double returnAmount(String customer, int orderId) {
        return ((Number) returnOf(customer, orderId).get("amount")).doubleValue();
    }

    /**
     * Switches a tool off or on again, the action being {@code disable} or {@code enable}.
     */
    static ValidatableResponse killSwitch(String user, String tool, String action) {
        return as(user).contentType(ContentType.JSON).post("/admin/tools/" + tool + "/" + action).then();
    }
}
