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
package org.acme.a2a.openai.ticket.triage.technical;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.a2a.A2AProgress;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.SupportedInterface;
import org.apache.camel.component.openai.OpenAIConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The technical support agent: a Camel route exposed as an A2A agent. The a2a component serves the agent card, handles
 * the protocol, task state and authentication, while the route only drafts the reply with OpenAI.
 */
@ApplicationScoped
public class TechnicalAgentRoute extends RouteBuilder {

    @Override
    public void configure() {
        from("a2a:classpath:cards/technical-agent-card.json"
                // JSON-RPC is the binding to use with the Quarkus HTTP server, see the README
                + "?protocolBinding=JSONRPC"
                // The agent is served under its own base path, so that several agents can share one HTTP server
                + "&basePath=/agents/technical"
                // The public URL of the agent comes from the configuration, see technicalAgentCard() below
                + "&agentCard=#technicalAgentCard"
                // validateAuth is left at its default (true): the agent card declares an API key security scheme and
                // every A2A operation must present the key. Only the agent card itself is public.
                + "&apiKey={{a2a.agents.api-key}}"
                // Bound the number of concurrent LLM calls, excess requests get a SERVER_BUSY JSON-RPC error
                + "&maxConcurrentTasks={{a2a.agents.max-concurrent-tasks}}")
                .routeId("technical-agent")
                // Log the task, never the ticket content: it may contain personal data
                .log("${routeId} started task ${header.CamelA2ATaskId}")
                // Clients using SendStreamingMessage see this progress update while the reply is being drafted
                .process(exchange -> A2AProgress.emit(exchange, "Drafting a reply"))
                .setHeader(OpenAIConstants.SYSTEM_MESSAGE, simple("resource:classpath:prompts/technical-agent.txt"))
                // maxTokens bounds the cost of a single call. With reasoning models it also covers the reasoning
                // tokens, so a reply can be cut off when the budget runs out.
                .to("openai:chat-completion?maxTokens=2048")
                // A cut off or empty reply must never reach a customer: fail the task instead
                .filter(simple("${header.CamelOpenAIFinishReason} == 'length' || ${body} == null || ${body} == ''"))
                .throwException(IllegalStateException.class, "The model did not produce a complete reply")
                .end()
                .log("${routeId} completed task ${header.CamelA2ATaskId}");
    }

    /**
     * Completes the agent card from {@code cards/technical-agent-card.json} with the URL under which this agent is
     * reachable, so that the card advertises the right address in every environment.
     */
    @Produces
    @Named("technicalAgentCard")
    AgentCard technicalAgentCard(@ConfigProperty(name = "a2a.technical-agent.public-url") String publicUrl) {
        SupportedInterface jsonRpc = new SupportedInterface();
        jsonRpc.setUrl(publicUrl);
        jsonRpc.setProtocolBinding("JSONRPC");
        jsonRpc.setProtocolVersion("1.0");
        return AgentCard.builder().setSupportedInterfaces(List.of(jsonRpc)).build();
    }
}
