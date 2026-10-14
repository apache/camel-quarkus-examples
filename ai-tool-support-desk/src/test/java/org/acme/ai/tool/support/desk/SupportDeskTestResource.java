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

import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Configures the support desk for the tests: the users, and WireMock standing in for the carrier, so that the tests can
 * check which calls were made and simulate an outage.
 */
public class SupportDeskTestResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMock;
    private static StubMapping carrierOutage;

    @Override
    public Map<String, String> start() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlPathMatching("/carrier/shipments/.+"))
                .willReturn(okJson("{\"status\": \"OUT_FOR_DELIVERY\", \"estimatedDelivery\": \"today\"}")));

        Map<String, String> config = new HashMap<>();
        config.put("support-desk.carrier.url", wireMock.baseUrl() + "/carrier");
        // The demo users of dev mode, whose password is their user name. dave is a customer who is not verified.
        config.put("quarkus.security.users.embedded.plain-text", "true");
        Map.of("alice", "customer,verified", "bob", "customer,verified", "carol", "support", "dave", "customer")
                .forEach((user, roles) -> {
                    config.put("quarkus.security.users.embedded.users." + user, user);
                    config.put("quarkus.security.users.embedded.roles." + user, roles);
                });
        return config;
    }

    @Override
    public void stop() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    /**
     * Simulates an outage of the carrier API, or ends it.
     */
    static void carrierUnavailable(boolean unavailable) {
        if (unavailable) {
            carrierOutage = wireMock.stubFor(get(urlPathMatching("/carrier/shipments/.+")).willReturn(serviceUnavailable()));
        } else {
            wireMock.removeStub(carrierOutage);
        }
    }

    /**
     * How many times the carrier was asked about the given shipment with the given HTTP header.
     */
    static int trackingRequestsWithHeader(String trackingNumber, String header) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo("/carrier/shipments/" + trackingNumber))
                .withHeader(header, matching(".*"))).size();
    }

    /**
     * How many times the carrier was asked about the given shipment.
     */
    static int trackingRequests(String trackingNumber) {
        return wireMock.findAll(getRequestedFor(urlPathEqualTo("/carrier/shipments/" + trackingNumber))).size();
    }
}
