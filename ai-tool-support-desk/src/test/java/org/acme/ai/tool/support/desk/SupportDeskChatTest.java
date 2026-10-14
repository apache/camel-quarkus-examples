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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.acme.ai.tool.support.desk.SupportDesk.as;
import static org.acme.ai.tool.support.desk.SupportDesk.killSwitch;
import static org.acme.ai.tool.support.desk.SupportDesk.returnAmount;
import static org.acme.ai.tool.support.desk.SupportDesk.returnId;
import static org.acme.ai.tool.support.desk.SupportDesk.returnStatus;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the customer chat of the support desk: the agent calls the ai-tool routes tagged {@code customer} on behalf
 * of the logged-in customer. The tests check what the tool calls did, in the database and on the carrier API, rather
 * than the wording of the replies.
 * <p>
 * With the simulated model, each conversation turn is a set of stub mappings in {@code src/test/resources/mappings}:
 * the model first asks for tool calls, then answers once it gets the tool results. Each stub only matches the expected
 * tool result, so a tool returning something else fails the test.
 */
@QuarkusTest
@QuarkusTestResource(OllamaTestResource.class)
@QuarkusTestResource(SupportDeskTestResource.class)
class SupportDeskChatTest {

    @Test
    void chatIsForLoggedInCustomers() {
        given()
                .contentType(ContentType.JSON)
                .body(message("Where is my order 1003?"))
                .post("/support/chat")
                .then()
                .statusCode(401);

        // carol is support staff, not a customer
        chat("carol", "Where is my order 1003?")
                .statusCode(403);

        chat("alice", " ")
                .statusCode(400);
    }

    @Test
    void unlistedPathIsDenied() {
        // Only /support/*, /admin/* and /mcp are allowed, see quarkus.http.auth.permission in application.properties
        given().get("/other").then().statusCode(401);
        as("alice").get("/other").then().statusCode(403);
        as("carol").get("/other").then().statusCode(403);
    }

    @Test
    void customerOnlySeesOwnOrders() {
        chat("alice", "What is the status of my order 1001?")
                .statusCode(200)
                .body("reply", not(emptyOrNullString()));

        // The chat gets the customer tools only, never the tools of the support staff
        assertEquals(List.of("escalate_to_human", "get_order", "request_return", "track_shipment"),
                OllamaTestResource.offeredTools());

        // Order 2001 is bob's: get_order runs for alice and does not find it, so the model never sees bob's headphones
        chat("alice", "And what about order 2001?")
                .statusCode(200)
                .body("reply", not(containsStringIgnoringCase("headphones")));
    }

    @Test
    void cheapReturnIsApprovedRightAway() {
        chat("alice", "Please return the two USB-C cables of order 1001, they are too short.")
                .statusCode(200);

        // 2 x 12.50 EUR is approved right away
        assertEquals("APPROVED", returnStatus("alice", 1001));
        assertEquals(25.0, returnAmount("alice", 1001));
    }

    @Test
    void expensiveReturnWaitsForApproval() {
        chat("alice", "I want to return the smart speaker of order 1002, it does not connect to my wifi.")
                .statusCode(200);

        // 129 EUR needs the approval of the support staff, see SupportDeskMcpTest
        assertEquals("PENDING_APPROVAL", returnStatus("alice", 1002));
        assertEquals(129.0, returnAmount("alice", 1002));
    }

    @Test
    void returnJustOver50EurWaitsForApproval() {
        chat("bob", "Please return the webcam of order 2004, I do not need it.")
                .statusCode(200);

        // 50.50 EUR is more than the 50 EUR that are approved right away, cents count
        assertEquals("PENDING_APPROVAL", returnStatus("bob", 2004));
        assertEquals(50.5, returnAmount("bob", 2004));
    }

    @Test
    void unverifiedCustomerCannotRequestAReturn() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "The simulated model answers dave");

        // dave has not verified their account: the authorization policy of request_return denies the call before the
        // route runs, and the model only learns that it is not authorized
        chat("dave", "Please return the phone case of order 3001, I do not need it.")
                .statusCode(200);

        assertNull(returnStatus("dave", 3001));
    }

    @Test
    void unverifiedCustomerCannotOpenATicket() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "The simulated model answers dave");

        // The policy denies escalate_to_human: the stub of the answer only matches the "not authorized" tool result
        chat("dave", "I want to talk to a human about my order 3001.")
                .statusCode(200);
    }

    @Test
    void orderOfAnotherCustomerCannotBeReturned() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model asks for the order of bob");

        // Order 2002 is bob's: for alice, request_return does not find it, and the stub of the answer matches nothing else
        chat("alice", "Please return the keyboard of order 2002, some keys stick.")
                .statusCode(200);

        assertNull(returnStatus("alice", 2002));
    }

    @Test
    void invalidReasonIsExplainedToTheModel() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model makes up a reason");

        // The model learns the allowed reasons rather than that the tool failed: the stub of its answer matches nothing
        // else
        chat("alice", "Please return the USB-C cables of order 1001, I simply do not like them.")
                .statusCode(200);
    }

    @Test
    void undeliveredOrderCannotBeReturned() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model asks for this return");

        // Order 1003 is on its way: the stubs of the answer only match the refusal of request_return
        chat("alice", "Please return the mouse of order 1003, I ordered it by mistake.")
                .statusCode(200);

        assertNull(returnStatus("alice", 1003));
    }

    @Test
    void orderDeliveredMoreThan30DaysAgoCannotBeReturned() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model asks for this return");

        // Order 1004 was delivered 60 days ago: the stubs of the answer only match the refusal of request_return
        chat("alice", "Please return the desk lamp of order 1004, I do not need it.")
                .statusCode(200);

        assertNull(returnStatus("alice", 1004));
    }

    @Test
    void orderCannotBeReturnedTwice() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model asks for this return");

        // Order 2001 has the return 1 of the demo data: the stubs of the answer only match the refusal of request_return
        chat("bob", "Please return the headphones of order 2001 again, the sound still cuts out.")
                .statusCode(200);

        assertEquals(1, returnId("bob", 2001));
    }

    @Test
    void itemListedTwiceIsNotRefundedTwice() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model lists an item twice on purpose");

        // Order 2003 holds one charger: requesting it twice is refused rather than refunded twice
        chat("bob", "Please return the charger of order 2003, I do not need it.")
                .statusCode(200);

        assertNull(returnStatus("bob", 2003));
    }

    @Test
    void shipmentIsTrackedWithTheCarrier() {
        chat("alice", "Where is my order 1003 right now?")
                .statusCode(200);

        assertTrue(SupportDeskTestResource.trackingRequests("ACME-7790") >= 1);
        // Only the tracking number leaves the application: the arguments of the tool call stay inside
        assertEquals(0, SupportDeskTestResource.trackingRequestsWithHeader("ACME-7790", "orderId"));
    }

    @Test
    void unavailableCarrierDoesNotBreakTheChat() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "The simulated model asks the carrier again");

        int trackingRequests = SupportDeskTestResource.trackingRequests("ACME-7790");
        SupportDeskTestResource.carrierUnavailable(true);
        try {
            // The circuit breaker falls back to telling the model that the carrier is not available: the stub of its
            // answer matches nothing else
            chat("alice", "Has my order 1003 left the warehouse?")
                    .statusCode(200);
            assertEquals(trackingRequests + 1, SupportDeskTestResource.trackingRequests("ACME-7790"));
        } finally {
            SupportDeskTestResource.carrierUnavailable(false);
        }
    }

    @Test
    void invalidPriorityIsExplainedToTheModel() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model makes up a priority");

        // The model learns the allowed priorities rather than that the tool failed: the stub of its answer matches
        // nothing else
        chat("alice", "Please let a human call me back about order 1001, it is critical.")
                .statusCode(200);
    }

    @Test
    void customerCanAskForAHuman() {
        chat("alice", "I want to talk to a human, the mouse of order 1003 is urgent for my work.")
                .statusCode(200);
    }

    @Test
    void failedToolCallRevealsNoDetails() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model asks for an invalid order");

        // get_order fails on an order number that is not a number. The model only learns "Tool execution failed": the
        // stub of its answer matches nothing else.
        chat("alice", "What is the status of my order number abc?")
                .statusCode(200);
    }

    @Test
    void tooManyToolCallsEndWithAReplyToTheCustomer() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model calls tools endlessly");

        // The model keeps calling get_order: after 5 rounds of tool calls (maxToolCallingRoundTrips in
        // SupportDeskRoutes), the agent gives up and the customer is told
        chat("alice", "Check my order 1001 again and again.")
                .statusCode(502)
                .body("reply", equalTo(ChatResource.NO_ANSWER));
        assertEquals(5, OllamaTestResource.requestsAfterToolCall("call_loop"));
    }

    @Test
    void killSwitchRemovesAToolFromTheChat() {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model calls a tool it does not have");

        killSwitch("carol", "track_shipment", "disable").statusCode(204);
        try {
            // The model still calls track_shipment, as if it remembered the tool. It learns that the tool is not
            // available and answers, rather than the chat failing.
            chat("alice", "Is my order 1003 still on its way?")
                    .statusCode(200);
            assertEquals(List.of("escalate_to_human", "get_order", "request_return"), OllamaTestResource.offeredTools());
        } finally {
            killSwitch("carol", "track_shipment", "enable").statusCode(204);
        }
    }

    @Test
    void killSwitchRejectsAFormPost() {
        // What a cross-site HTML form would send with the credentials of a logged-in support engineer
        as("carol").contentType(ContentType.URLENC).formParam("x", "y")
                .post("/admin/tools/track_shipment/disable")
                .then()
                .statusCode(415);
    }

    @Test
    void customerHasOneMessageInProgressAtMost() throws Exception {
        Assumptions.assumeTrue(OllamaTestResource.isMocking(), "Only the simulated model answers slowly on purpose");

        String slowMessage = "Hello, is anybody there?";
        CompletableFuture<Integer> aliceReply = CompletableFuture.supplyAsync(
                () -> chat("alice", slowMessage).extract().statusCode());
        await().atMost(10, TimeUnit.SECONDS).until(() -> OllamaTestResource.requestsFor(slowMessage) == 1);

        // Another customer is not held up by the message of alice
        CompletableFuture<Integer> bobReply = CompletableFuture.supplyAsync(
                () -> chat("bob", slowMessage).extract().statusCode());
        await().atMost(10, TimeUnit.SECONDS).until(() -> OllamaTestResource.requestsFor(slowMessage) == 2);

        // A second message of alice is rejected while the first one waits for its reply
        chat("alice", "Hello?")
                .statusCode(429)
                .header("Retry-After", "5");
        assertEquals(200, aliceReply.get(30, TimeUnit.SECONDS));
        assertEquals(200, bobReply.get(30, TimeUnit.SECONDS));
    }

    private static ValidatableResponse chat(String customer, String message) {
        return as(customer)
                .contentType(ContentType.JSON)
                .body(message(message))
                .post("/support/chat")
                .then();
    }

    private static Map<String, String> message(String message) {
        return Map.of("message", message);
    }
}
