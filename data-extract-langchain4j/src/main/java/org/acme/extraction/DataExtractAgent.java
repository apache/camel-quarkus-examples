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

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import org.acme.extraction.CustomPojoExtractionService.CustomPojo;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;

/**
 * Custom agent to extract data from text.
 */
public class DataExtractAgent implements Agent {
    private final AgentConfiguration configuration;
    private final CustomPojoStore pojoStore;

    public DataExtractAgent(
            AgentConfiguration configuration,
            CustomPojoStore pojoStore) {
        this.configuration = configuration;
        this.pojoStore = pojoStore;
    }

    /**
     * Returns a JSON representation of a {@link CustomPojo}.
     */
    @Override
    public String chat(AiAgentBody<?> aiAgentBody, ToolProvider toolProvider) {
        CustomPojo response = createService(toolProvider).extractFromText(aiAgentBody.getUserMessage());

        // Store extracted CustomPojoExtractionService.CustomPojos objects into the CustomPojoStore for later inspection
        pojoStore.addPojo(response);

        // Return a string representation of the result POJO
        return response.toString();
    }

    CustomPojoExtractionService createService(ToolProvider toolProvider) {
        var builder = AiServices.builder(CustomPojoExtractionService.class)
                .chatModel(configuration.getChatModel());

        if (toolProvider != null) {
            builder.toolProvider(toolProvider);
        }

        return builder.build();
    }
}
