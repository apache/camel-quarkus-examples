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
package org.acme.extraction;

import java.util.Arrays;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.ollama.OllamaContainer;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;

import static org.eclipse.microprofile.config.ConfigProvider.getConfig;

public class OllamaTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaTestResource.class);

    private static final String OLLAMA_IMAGE = "ollama/ollama:0.14.1";
    private static final int OLLAMA_SERVER_PORT = 11434;

    private static final String MODE_MOCK = "mock";
    private static final String MODE_RECORDING = "record";
    private static final String MODE_CONTAINER = "container";

    private OllamaContainer ollamaContainer;
    private WireMockServer wireMockServer;
    private String baseUrl;

    /**
     * The testMode value could be defined, for instance by invoking: mvn clean test -DtestMode=mock.
     *
     * With the default value "mock", the LLM is faked based on the last recorded run.
     * With the value "record", tests are run against a containerized LLM while the HTTP interactions are recorded.
     * With the value "container" tests are run against a containerized LLM without recording.
     * With any other value, an IllegalArgumentException is thrown.
     */
    private boolean isMockMode;
    private boolean isRecordingMode;
    private boolean isContainerMode;

    private static final String BASE_URL_FORMAT = "http://%s:%s";

    @Override
    public Map<String, String> start() {

        try {
            // Check the test running mode
            String testMode = System.getProperty("testMode", MODE_MOCK);
            isMockMode = MODE_MOCK.equals(testMode);
            isRecordingMode = MODE_RECORDING.equals(testMode);
            isContainerMode = MODE_CONTAINER.equals(testMode);
            if (!isMockMode && !isRecordingMode && !isContainerMode) {
                throw new IllegalArgumentException(
                        "testMode value should be one of " + Arrays.asList(MODE_MOCK, MODE_RECORDING, MODE_CONTAINER));
            }

            if (isMockMode) {
                LOG.info("Starting a fake Ollama server backed by wiremock");
                initWireMockServer();
            } else {
                baseUrl = System.getProperty("baseUrl", System.getenv("BASE_URL"));
                if (baseUrl != null) {
                    LOG.info("Using Ollama server at {}", baseUrl);
                } else {
                    LOG.info("Starting an Ollama server backed by testcontainers");
                    ollamaContainer = new OllamaContainer(OLLAMA_IMAGE)
                            .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("basicAuthContainer"));
                    ollamaContainer.start();

                    String ollamaModelId = getConfig().getValue("langchain4j.ollama.chat-model.model-id", String.class);

                    ExecResult result = ollamaContainer.execInContainer("ollama", "pull", ollamaModelId);
                    long pullBegin = currentTimeMillis();
                    while ((currentTimeMillis() - pullBegin < 10000)
                            && (result.getStderr() == null || !result.getStderr().contains("success"))) {
                        LOG.info("Will retry ollama pull after sleeping 250ms");

                        Thread.sleep(250);

                        result = ollamaContainer.execInContainer("ollama", "pull", ollamaModelId);
                    }

                    baseUrl = format(BASE_URL_FORMAT, ollamaContainer.getHost(),
                            ollamaContainer.getMappedPort(OLLAMA_SERVER_PORT));
                }

                if (isRecordingMode) {
                    LOG.info("Recording interactions with the Ollama server backed by testcontainers");
                    initWireMockServer();
                }
            }

            return Map.of("langchain4j.ollama.base-url", baseUrl);
        } catch (Exception ex) {
            throw new RuntimeException("An issue occurred while starting ollama container", ex);
        }
    }

    private void initWireMockServer() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        if (isRecordingMode) {
            wireMockServer.resetMappings();
            wireMockServer.startRecording(baseUrl);
        }
        baseUrl = format(BASE_URL_FORMAT, "localhost", wireMockServer.port());
    }

    @Override
    public void stop() {
        try {
            if (ollamaContainer != null) {
                ollamaContainer.stop();
            }
        } catch (Exception ex) {
            LOG.error("An issue occurred while stopping ollama container", ex);
        }

        if (isMockMode) {
            wireMockServer.stop();
        } else if (isRecordingMode) {
            wireMockServer.stopRecording();
            wireMockServer.saveMappings();
        }
    }
}
