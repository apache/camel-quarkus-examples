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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import static java.lang.String.format;

public class OllamaTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(OllamaTestResource.class);

    // LangChain4j offers only latest tag for ollama-codellama, hence we do use latest until more tags are introduced
    private static final String OLLAMA_IMAGE = "langchain4j/ollama-codellama:latest";
    private static final int OLLAMA_SERVER_PORT = 11434;

    private GenericContainer<?> ollamaContainer;

    private WireMockServer wireMockServer;
    private String baseUrl;

    private static final String MODE_MOCK = "mock";
    private static final String MODE_RECORDING = "record";
    private static final String MODE_CONTAINER = "container";

    /**
     * The testMode value could be defined, for instance by invoking:
     * mvn clean test -DtestMode=mock.
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
            LOG.info("Starting an Ollama server backed by testcontainers");
            ollamaContainer = new GenericContainer<>(OLLAMA_IMAGE)
                    .withExposedPorts(OLLAMA_SERVER_PORT)
                    .withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("basicAuthContainer"))
                    .waitingFor(Wait.forLogMessage(".* msg=\"inference compute\" .*", 1));
            ollamaContainer.start();

            baseUrl = format(BASE_URL_FORMAT, ollamaContainer.getHost(), ollamaContainer.getMappedPort(OLLAMA_SERVER_PORT));

            if (isRecordingMode) {
                LOG.info("Recording interactions with the Ollama server backed by testcontainers");
                initWireMockServer();
            }
        }

        return Map.of("quarkus.langchain4j.ollama.base-url", baseUrl);
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
            LOG.error("An issue occurred while stopping " + ollamaContainer.getNetworkAliases(), ex);
        }

        if (isMockMode) {
            wireMockServer.stop();
        } else if (isRecordingMode) {
            wireMockServer.stopRecording();
            wireMockServer.saveMappings();
        }
    }
}
