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

package org.acme.resource;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class ElasticSearchTestResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchTestResource.class);

    private static final String IMAGE_NAME = "docker.io/elastic/elasticsearch:8.13.2";
    private ElasticsearchContainer container;

    @Override
    public Map<String, String> start() {

        DockerImageName imageName = DockerImageName.parse(IMAGE_NAME);
        container = new ElasticsearchContainer(
                imageName.asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .waitingFor(Wait.forListeningPort());
        ;

        container.start();

        String address = container.getHttpHostAddress();
        LOG.debug("Connecting to {}", address);

        return Map.of(
                "camel.component.elasticsearch-rest-client.host-addresses-list",
                address);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
