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
package org.acme.kamelet;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jboss.logging.Logger;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

public class ChuckNorrisTestResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOG = Logger.getLogger(ChuckNorrisTestResource.class);
    private static final String WIREMOCK_RECORD = System.getProperty("wiremock.record");
    private WireMockServer wireMock;

    @Override
    public Map<String, String> start() {
        LOG.info("Starting WireMock");

        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        if (WIREMOCK_RECORD != null && WIREMOCK_RECORD.equals("true")) {
            LOG.info("Enable WireMock record mode");
            wireMock.resetMappings();
            wireMock.startRecording("https://api.chucknorris.io/jokes/random");
        }

        wireMock.start();

        String endpointUri = "http://localhost:%d".formatted(wireMock.port());
        LOG.infof("Started WireMock on %s", endpointUri);

        return Map.of(
                "chuck.norris.api.url", endpointUri,
                "camel.main.globalOptions[http.proxyHost]", "localhost",
                "camel.main.globalOptions[http.proxyPort]", String.valueOf(wireMock.port()),
                "camel.main.globalOptions[https.proxyHost]", "localhost",
                "camel.main.globalOptions[https.proxyPort]", String.valueOf(wireMock.port()));
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            if (WIREMOCK_RECORD != null && WIREMOCK_RECORD.equals("true")) {
                wireMock.stopRecording();
                wireMock.saveMappings();
            }
            wireMock.stop();
        }
    }
}
