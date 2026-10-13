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
package org.acme.a2a.openai.ticket.triage;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talks to the specialist agents directly, the way any A2A client would: discover the agent card, then send JSON-RPC
 * requests authenticated with the API key.
 */
@QuarkusTest
@QuarkusTestResource(OpenAiTestResource.class)
class SpecialistAgentTest {

    private static final String AGENT_URL = "/agents/billing/";
    private static final String MESSAGE = "I was charged twice for my Pro subscription this month, "
            + "please refund one of the payments.";
    private static final String TECHNICAL_AGENT_URL = "/agents/technical/";
    private static final String TECHNICAL_MESSAGE = "The mobile app crashes every time I open the settings screen.";

    @Test
    void agentCardIsPublic() {
        given()
                .get(AGENT_URL + ".well-known/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", is("Billing Support Agent"),
                        "capabilities.streaming", is(true),
                        "supportedInterfaces[0].protocolBinding", is("JSONRPC"),
                        // The advertised URL comes from the configuration, not from the card file
                        "supportedInterfaces[0].url", endsWith("/agents/billing"),
                        "skills[0].id", is("draft-billing-reply"),
                        "securitySchemes.apiKey", notNullValue());
    }

    @Test
    void technicalAgentDraftsReply() {
        given()
                .get(TECHNICAL_AGENT_URL + ".well-known/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", is("Technical Support Agent"),
                        "supportedInterfaces[0].url", endsWith("/agents/technical"));

        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", OpenAiTestResource.AGENT_API_KEY)
                .body(sendMessage("SendMessage", TECHNICAL_MESSAGE))
                .post(TECHNICAL_AGENT_URL)
                .then()
                .statusCode(200)
                .body("result.task.status.state", is("TASK_STATE_COMPLETED"))
                .body(containsString("Acme Technical Support"));
    }

    @Test
    void operationsRequireTheApiKey() {
        // With the JSON-RPC binding, a rejected request is a JSON-RPC error envelope (HTTP 200), not an HTTP 401

        // No key
        given()
                .contentType(ContentType.JSON)
                .body(sendMessage("SendMessage"))
                .post(AGENT_URL)
                .then()
                .statusCode(200)
                .header("WWW-Authenticate", notNullValue())
                .body("result", nullValue(),
                        "error.code", is(-32000),
                        "error.data[0].reason", is("AUTHENTICATION"));

        // Wrong key
        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", "wrong-key")
                .body(sendMessage("SendMessage"))
                .post(AGENT_URL)
                .then()
                .statusCode(200)
                .body("result", nullValue(),
                        "error.code", is(-32000),
                        "error.data[0].reason", is("AUTHENTICATION"));
    }

    @Test
    void authenticatedClientGetsADraftedReply() {
        given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", OpenAiTestResource.AGENT_API_KEY)
                .body(sendMessage("SendMessage"))
                .post(AGENT_URL)
                .then()
                .statusCode(200)
                .body("jsonrpc", is("2.0"),
                        "id", is("req-1"),
                        "result.task.id", notNullValue(),
                        "result.task.status.state", is("TASK_STATE_COMPLETED"))
                .body(containsString("Acme Billing Support"));
    }

    @Test
    void streamingClientReceivesProgressUpdates() {
        String events = given()
                .contentType(ContentType.JSON)
                .header("X-API-Key", OpenAiTestResource.AGENT_API_KEY)
                .body(sendMessage("SendStreamingMessage"))
                .post(AGENT_URL)
                .then()
                .statusCode(200)
                .contentType("text/event-stream")
                .extract().asString();

        assertTrue(events.contains("Drafting a reply"), "Expected a progress update in the event stream: " + events);
        assertTrue(events.contains("Acme Billing Support"), "Expected the drafted reply in the event stream: " + events);
    }

    private static String sendMessage(String method) {
        return sendMessage(method, MESSAGE);
    }

    private static String sendMessage(String method, String text) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"id\":\"req-1\","
                + "\"params\":{\"message\":{\"messageId\":\"msg-1\",\"role\":\"user\","
                + "\"parts\":[{\"text\":\"" + text + "\"}]}}}";
    }
}
