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
package org.acme.langchain4j.ingest.rag;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTestResource(OllamaTestResource.class)
@QuarkusTest
class Langchain4jIngestRagTest {

    private static final String QUESTION = "What is the return policy?";

    @Test
    void documentsAreIngestedAndSearchable() {
        List<Map<String, Object>> hits = awaitReturnPolicy();
        assertTrue(hits.stream().anyMatch(hit -> "company-policy.md".equals(hit.get("document"))),
                "the hit must cite the document it came from, got: " + hits);
    }

    @Test
    void assistantAnswersFromTheDocuments() {
        awaitReturnPolicy();

        // the retrieved context reaches the prompt without any RAG code; no LLM involved here
        String prompt = RestAssured.given()
                .queryParam("q", QUESTION)
                .get("/chat/preview")
                .then().statusCode(200)
                .extract().asString();
        assertTrue(prompt.contains("30 days"), "the prompt must contain the retrieved context, got: " + prompt);

        String answer = RestAssured.given()
                .queryParam("q", QUESTION)
                .get("/chat")
                .then().statusCode(200)
                .extract().asString();
        // the fake model answers correctly only when the retrieved document text was in the request
        assertTrue(answer.contains("30"), "the answer must come from the documents, got: " + answer);

    }

    /** Ingestion runs in the background after startup: wait until the return policy is searchable. */
    static List<Map<String, Object>> awaitReturnPolicy() {
        return await().atMost(3, TimeUnit.MINUTES).pollInterval(1, TimeUnit.SECONDS)
                .until(() -> search("How long do I have to return a product?"),
                        hits -> hits.stream().anyMatch(hit -> String.valueOf(hit.get("text")).contains("30 days")));
    }

    static List<Map<String, Object>> search(String question) {
        return RestAssured.given()
                .queryParam("q", question)
                .get("/search")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");
    }
}
