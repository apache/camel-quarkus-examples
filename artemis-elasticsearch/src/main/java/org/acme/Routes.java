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

package org.acme;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.elasticsearch.rest.client.ElasticSearchRestClientConstant;
import org.apache.camel.impl.event.CamelContextStartedEvent;

@ApplicationScoped
public class Routes extends RouteBuilder {

    @Inject
    FluentProducerTemplate template;

    // Create an index on application startup
    void init(@Observes CamelContextStartedEvent event) {
        template.to("elasticsearch-rest-client:docker-cluster?operation=CREATE_INDEX")
                .withHeader(ElasticSearchRestClientConstant.INDEX_NAME, "devices")
                .send();
    }

    @Override
    public void configure() {
        rest()
                .post("/devices")
                .to("direct:devices")
                .get("/devices")
                .produces("application/json")
                .to("direct:getDevices");

        from("direct:devices")
                .routeId("direct-devices")
                .to("paho:devices");

        from("paho:devices")
                .routeId("paho-devices")
                .process(exchange -> {
                    String body = exchange.getMessage().getBody(String.class);
                    exchange.getMessage().setBody(Map.of("devices", body));
                })
                .marshal().json()
                .to("elasticsearch-rest-client:docker-cluster?operation=INDEX_OR_UPDATE&indexName=devices");

        from("direct:getDevices")
                .routeId("get-devices")
                .setHeader(ElasticSearchRestClientConstant.SEARCH_QUERY).constant("{\"query\":{\"match_all\":{}}}")
                .to("elasticsearch-rest-client:docker-cluster?operation=SEARCH&indexName=devices");
    }

}
