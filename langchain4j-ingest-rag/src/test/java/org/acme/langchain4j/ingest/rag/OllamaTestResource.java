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

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * The chat model of the tests. By default a WireMock server plays the Ollama API with canned
 * answers, so the tests need no model. With {@code OLLAMA_BASE_URL} set, that real Ollama is
 * used instead, with the model named by {@code OLLAMA_MODEL}.
 */
public class OllamaTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaTestResource.class);
    private static final String CHAT = "/api/chat";

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        String realBaseUrl = System.getenv("OLLAMA_BASE_URL");
        if (realBaseUrl != null && !realBaseUrl.isBlank()) {
            LOG.info("Using the Ollama server at {}", realBaseUrl);
            return Map.of();
        }

        LOG.info("Starting a fake Ollama server backed by WireMock");
        server = new WireMockServer(options().dynamicPort());
        server.start();
        // the "model" knows the answer only when the retrieved document text is part of the request,
        // as it would be after augmentation; a bare question gets the fallback below, so a correct
        // answer in the tests proves that retrieval grounded the prompt
        server.stubFor(post(urlPathEqualTo(CHAT))
                .withRequestBody(containing("within 30 days of delivery"))
                .willReturn(okJson(answer(
                        "Products can be returned within 30 days of delivery, unused and in their original packaging."))));
        server.stubFor(post(urlPathEqualTo(CHAT))
                .atPriority(10)
                .willReturn(okJson(answer("I don't have complete information on that."))));

        return Map.of("quarkus.langchain4j.ollama.base-url", server.baseUrl());
    }

    private static String answer(String content) {
        return """
                {
                  "model": "llama3.2",
                  "created_at": "2026-01-01T00:00:00Z",
                  "message": { "role": "assistant", "content": "%s" },
                  "done": true,
                  "done_reason": "stop",
                  "prompt_eval_count": 1,
                  "eval_count": 1
                }
                """.formatted(content);
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
