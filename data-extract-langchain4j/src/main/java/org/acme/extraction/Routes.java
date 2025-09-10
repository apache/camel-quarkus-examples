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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;

import static org.acme.extraction.DataExtractAgentConfiguration.AGENT_ID;

@ApplicationScoped
public class Routes extends RouteBuilder {

    @Inject
    CustomPojoStore customPojoStore;

    @Override
    public void configure() {
        // Consumes file documents that contain conversation transcripts (JSON format)
        from("file:target/transcripts?sortBy=file:name")
                .log("A document has been received by the camel-quarkus-file extension: ${body}")
                // Get the content to fulfill the CustomPojoExtractService text argument
                .setBody().jsonpath("$.content")
                // Initiate a conversation with the LLM
                .toF("langchain4j-agent:%s", AGENT_ID);

        // This route make it possible to inspect the extracted POJOs, mainly used for demo and test
        from("platform-http:/custom-pojo-store?produces=application/json")
                .bean(customPojoStore);
    }
}
