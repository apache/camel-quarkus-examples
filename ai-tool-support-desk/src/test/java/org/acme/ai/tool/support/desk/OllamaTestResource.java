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

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.restassured.path.json.JsonPath;
import org.eclipse.microprofile.config.ConfigProvider;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Puts a WireMock server between the application and the model, in one of three modes chosen with
 * {@code -DtestMode=...}:
 * <ul>
 * <li>{@code mock}, the default: WireMock plays back the stub mappings in {@code src/test/resources/mappings}, so the
 * tests run offline and do not depend on the answers of a real model.</li>
 * <li>{@code record}: WireMock passes the requests on to the model at {@code support-desk.llm.base-url}, granite4.1:3b
 * on Ollama by default, and saves them as new stub mappings in {@code target/wiremock/mappings}.</li>
 * <li>{@code live}: WireMock only passes the requests on to the model.</li>
 * </ul>
 * In each mode, the tests can look at the requests the application sent to the model.
 */
public class OllamaTestResource implements QuarkusTestResourceLifecycleManager {

    private static final String MOCK = "mock";
    private static final String RECORD = "record";
    private static final String LIVE = "live";

    private static WireMockServer wireMock;
    private static String testMode;

    @Override
    public Map<String, String> start() {
        testMode = System.getProperty("testMode", MOCK);
        if (!List.of(MOCK, RECORD, LIVE).contains(testMode)) {
            throw new IllegalArgumentException("testMode must be one of " + List.of(MOCK, RECORD, LIVE));
        }
        // The stub mappings are read from src/test/resources/mappings by default
        WireMockConfiguration config = wireMockConfig().dynamicPort();
        if (!MOCK.equals(testMode)) {
            // Start without the stub mappings, and save the recorded ones apart from them
            new File("target/wiremock/mappings").mkdirs();
            config.usingFilesUnderDirectory("target/wiremock");
        }
        wireMock = new WireMockServer(config);
        wireMock.start();
        if (!MOCK.equals(testMode)) {
            String modelUrl = ConfigProvider.getConfig().getValue("support-desk.llm.base-url", String.class);
            if (RECORD.equals(testMode)) {
                wireMock.startRecording(modelUrl);
            } else {
                wireMock.stubFor(any(anyUrl()).willReturn(aResponse().proxiedFrom(modelUrl)));
            }
        }
        return Map.of("support-desk.llm.base-url", wireMock.baseUrl());
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            if (RECORD.equals(testMode)) {
                wireMock.stopRecording();
                wireMock.saveMappings();
            }
            wireMock.stop();
        }
    }

    /**
     * Whether the model is simulated by the stub mappings, rather than a real model answering.
     */
    static boolean isMocking() {
        return MOCK.equals(testMode);
    }

    /**
     * The names of the tools offered to the model in the latest request, sorted.
     */
    static List<String> offeredTools() {
        ServeEvent latest = wireMock.getAllServeEvents().stream()
                .max(Comparator.comparing(event -> event.getRequest().getLoggedDate()))
                .orElseThrow();
        List<String> tools = JsonPath.from(latest.getRequest().getBodyAsString()).getList("tools.function.name");
        return tools.stream().sorted().toList();
    }

    /**
     * How many times the model was sent the result of the given tool call, as the last message of the conversation.
     */
    static long requestsAfterToolCall(String toolCallId) {
        return wireMock.getAllServeEvents().stream()
                .map(event -> JsonPath.from(event.getRequest().getBodyAsString()).getString("messages[-1].tool_call_id"))
                .filter(toolCallId::equals)
                .count();
    }

    /**
     * How many times the model was asked to answer the given customer message.
     */
    static long requestsFor(String customerMessage) {
        return wireMock.getAllServeEvents().stream()
                .map(event -> JsonPath.from(event.getRequest().getBodyAsString()).getString("messages[-1].content"))
                .filter(customerMessage::equals)
                .count();
    }
}
