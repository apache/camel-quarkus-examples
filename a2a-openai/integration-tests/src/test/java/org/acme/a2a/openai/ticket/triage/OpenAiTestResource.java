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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.StubMappingTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Configures the application under test: the orchestrator and both specialist agents run together in the test
 * application, and the OpenAI API is either simulated or real.
 * <ul>
 * <li>By default the OpenAI API is simulated by WireMock, using the stub mappings in
 * {@code src/test/resources/mappings}. No network access or API key is needed.</li>
 * <li>When the {@code OPENAI_API_KEY} environment variable is set, the tests run against the real API. The endpoint
 * and the model can be changed with the {@code OPENAI_BASE_URL} and {@code OPENAI_MODEL} environment variables, so
 * any OpenAI compatible provider can be used.</li>
 * <li>When additionally {@code WIREMOCK_RECORD=true} is set, WireMock proxies the real API and saves the exchanged
 * requests and responses as new stub mappings.</li>
 * </ul>
 */
public class OpenAiTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String OPENAI_API_KEY = "OPENAI_API_KEY";
    public static final String OPENAI_BASE_URL = "OPENAI_BASE_URL";
    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    /** The shared secret the tests (and the orchestrator) present to the specialist agents. */
    public static final String AGENT_API_KEY = "test-only-" + UUID.randomUUID();
    /** A ticket subject for which the simulated OpenAI API answers with HTTP 500. */
    public static final String OUTAGE_SUBJECT = "Simulated LLM outage";

    private static boolean mocking;

    private static final Logger LOG = Logger.getLogger(OpenAiTestResource.class);
    private static final Set<String> SENSITIVE_RESPONSE_HEADERS = Set.of("openai-organization", "openai-project",
            "set-cookie");

    private WireMockServer wireMock;
    private boolean recording;

    @Override
    public Map<String, String> start() {
        Map<String, String> config = new HashMap<>();
        config.put("a2a.agents.api-key", AGENT_API_KEY);
        // The test application hosts the orchestrator and both agents, so the agents are reached on the test port
        String agentsBaseUrl = "http://localhost:" + ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.test-port", Integer.class).orElse(8081);
        config.put("a2a.agents.billing.url", agentsBaseUrl + "/agents/billing");
        config.put("a2a.agents.technical.url", agentsBaseUrl + "/agents/technical");
        config.put("a2a.billing-agent.public-url", agentsBaseUrl + "/agents/billing");
        config.put("a2a.technical-agent.public-url", agentsBaseUrl + "/agents/technical");

        boolean realApi = System.getenv(OPENAI_API_KEY) != null;
        recording = Boolean.parseBoolean(System.getenv("WIREMOCK_RECORD"))
                || Boolean.parseBoolean(System.getProperty("wiremock.record"));
        String baseUrl = envOrDefault(OPENAI_BASE_URL, DEFAULT_BASE_URL);

        mocking = !realApi;
        if (realApi && !recording) {
            LOG.infof("Testing against the OpenAI API at %s", baseUrl);
            return config;
        }

        if (recording && !realApi) {
            throw new IllegalStateException(OPENAI_API_KEY + " must be set to record new WireMock mappings");
        }

        wireMock = new WireMockServer(wireMockConfig()
                .dynamicPort()
                .extensions(new SensitiveHeaderRemover()));
        wireMock.start();

        if (recording) {
            LOG.infof("Recording OpenAI API interactions with %s", baseUrl);
            wireMock.startRecording(recordSpec()
                    .forTarget(stripTrailingSlash(baseUrl))
                    .transformers(SensitiveHeaderRemover.NAME));
        } else {
            LOG.info("Simulating the OpenAI API with WireMock");
            config.put("camel.component.openai.api-key", "not-a-real-key");
            // Simulate an outage of the LLM provider for one specific ticket
            wireMock.stubFor(post(urlPathEqualTo("/chat/completions"))
                    .withRequestBody(containing(OUTAGE_SUBJECT))
                    .willReturn(serverError()));
        }

        config.put("camel.component.openai.base-url", wireMock.baseUrl());
        return config;
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            if (recording) {
                wireMock.stopRecording();
                wireMock.saveMappings();
            }
            wireMock.stop();
        }
    }

    /**
     * Whether the OpenAI API is simulated by WireMock, as opposed to tests running against a real API.
     */
    public static boolean isMocking() {
        return mocking;
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Keeps account identifiers and cookies returned by the real API out of the recorded stub mappings.
     */
    static final class SensitiveHeaderRemover extends StubMappingTransformer {

        static final String NAME = "sensitive-header-remover";

        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public StubMapping transform(StubMapping stubMapping, FileSource files, Parameters parameters) {
            List<HttpHeader> headers = stubMapping.getResponse().getHeaders().all().stream()
                    .filter(header -> !SENSITIVE_RESPONSE_HEADERS.contains(header.key().toLowerCase()))
                    .toList();
            stubMapping.setResponse(ResponseDefinitionBuilder.like(stubMapping.getResponse())
                    .withHeaders(new HttpHeaders(headers))
                    .build());
            return stubMapping;
        }
    }
}
