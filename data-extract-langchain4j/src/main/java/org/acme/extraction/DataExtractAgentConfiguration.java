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

import java.time.Duration;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DataExtractAgentConfiguration {
    public static final String AGENT_ID = "data-extract-agent";

    @ConfigProperty(name = "langchain4j.ollama.base-url")
    String baseUrl;

    @ConfigProperty(name = "langchain4j.ollama.chat-model.model-id")
    String chatModelId;

    @Singleton
    AgentConfiguration agentConfiguration() {
        return new AgentConfiguration().withChatModel(OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .topK(1)
                .topP(0.1)
                .modelName(chatModelId)
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(3))
                .build());
    }

    @Singleton
    @Identifier(AGENT_ID)
    Agent agent(AgentConfiguration configuration, CustomPojoStore pojoStore) {
        return new DataExtractAgent(configuration, pojoStore);
    }
}
