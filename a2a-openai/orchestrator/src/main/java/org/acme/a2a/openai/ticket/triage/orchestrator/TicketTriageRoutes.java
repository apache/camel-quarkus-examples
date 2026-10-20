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
package org.acme.a2a.openai.ticket.triage.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.a2a.openai.ticket.triage.orchestrator.model.Ticket;
import org.acme.a2a.openai.ticket.triage.orchestrator.model.TicketTriage;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.openai.OpenAIConstants;
import org.apache.camel.processor.ThrottlerRejectedExecutionException;

/**
 * The orchestrator: accepts support tickets over HTTP, lets the triage agent classify them with OpenAI and delegates
 * them to the matching specialist agent using the A2A protocol.
 */
@ApplicationScoped
public class TicketTriageRoutes extends RouteBuilder {

    @Inject
    TriageSupport support;

    @Override
    public void configure() {
        // Invalid tickets are rejected with HTTP 400. The details are logged, the caller only gets a generic message
        // so that nothing about the internals leaks out. A JSON parsing error only counts as the caller's fault while
        // the ticket has not been parsed yet (onWhen): Jackson errors later in the route are server side problems.
        onException(InvalidTicketException.class, JsonProcessingException.class)
                .onWhen(exchangeProperty(TriageSupport.PROPERTY_TICKET).isNull())
                .handled(true)
                .log(LoggingLevel.WARN, "Rejected ticket: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant("{\"error\":\"Invalid ticket: a non-empty subject and body are required\"}"));

        // The orchestrator is at capacity: tell the caller to retry later (HTTP 503) instead of queueing LLM calls
        onException(ThrottlerRejectedExecutionException.class)
                .handled(true)
                .log(LoggingLevel.WARN, "Rejected ticket: too many tickets are being processed")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(503))
                .setHeader("Retry-After", constant(5))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant("{\"error\":\"Too many tickets are being processed, please retry later\"}"));

        // Anything else (the LLM provider is down, an agent is unreachable or failed the task, ...) becomes HTTP 502.
        // The full exception is logged server side, but never returned to the caller.
        onException(Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        "Ticket ${exchangeProperty.ticketId} could not be processed: ${exception.stacktrace}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(502))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setBody(constant("{\"error\":\"The ticket could not be processed, please retry later\"}"));

        from("platform-http:/tickets?httpMethodRestrict=POST&consumes=application/json&produces=application/json")
                .routeId("ticket-triage")
                .unmarshal().json(Ticket.class)
                .process(support::validateTicket)
                // Drop every inbound HTTP header before talking to the LLM or to other agents. Otherwise a caller could
                // inject CamelOpenAI* or CamelA2A* headers and hijack the prompt or the task.
                .removeHeaders("*")
                .log("Ticket ${exchangeProperty.ticketId} received")
                // Bound the number of tickets in flight, and with it the number of concurrent LLM calls. Excess
                // requests are rejected right away (see the ThrottlerRejectedExecutionException handler above).
                .throttle(constant("{{triage.max-concurrent-tickets}}")).concurrentRequestsMode().rejectExecution(true)
                // Step 1: the triage agent classifies the ticket. The strict JSON schema derived from TicketTriage
                // forces a well-formed answer, maxTokens bounds the cost of a single call.
                .setHeader(OpenAIConstants.SYSTEM_MESSAGE, simple("resource:classpath:prompts/triage-agent.txt"))
                .process(support::triagePrompt)
                .to("openai:chat-completion?outputClass=" + TicketTriage.class.getName() + "&maxTokens=2048")
                // The model output is untrusted input, just like the ticket: parse it and bound it before acting on it
                .unmarshal().json(TicketTriage.class)
                .process(support::validateTriage)
                .log("Ticket ${exchangeProperty.ticketId} triaged as ${body.category} with priority ${body.priority}")
                // Step 2: delegate to the specialist agent for the category. The agent is discovered through its agent
                // card and authenticated with the shared API key. The mapping from category to agent is explicit on
                // purpose: the model's output never selects a URL. toD is used for a static URI because it creates the
                // endpoint when the first ticket arrives, so the agent card is fetched at that point rather than at
                // startup: the agents are separate services and do not have to be up before this application starts.
                .choice()
                .when(simple("${body.category} == 'BILLING'"))
                .setProperty(TriageSupport.PROPERTY_AGENT_NAME, constant("Billing Support Agent"))
                .process(support::specialistPrompt)
                .toD("a2a:{{a2a.agents.billing.url}}?protocolBinding=JSONRPC&apiKey={{a2a.agents.api-key}}")
                .when(simple("${body.category} == 'TECHNICAL'"))
                .setProperty(TriageSupport.PROPERTY_AGENT_NAME, constant("Technical Support Agent"))
                .process(support::specialistPrompt)
                .toD("a2a:{{a2a.agents.technical.url}}?protocolBinding=JSONRPC&apiKey={{a2a.agents.api-key}}")
                .otherwise()
                .log("Ticket ${exchangeProperty.ticketId} has no specialist agent and is escalated to a human")
                .end()
                .process(support::buildResult)
                .marshal().json();

        // Lets the web UI show whether the triage agent talks to OpenAI or to another OpenAI compatible API
        from("platform-http:/llm?httpMethodRestrict=GET&produces=application/json")
                .routeId("llm-info")
                .process(support::llmInfo)
                .marshal().json();
    }
}
