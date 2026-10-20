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

import java.net.URI;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.a2a.openai.ticket.triage.orchestrator.model.LlmInfo;
import org.acme.a2a.openai.ticket.triage.orchestrator.model.Ticket;
import org.acme.a2a.openai.ticket.triage.orchestrator.model.TicketTriage;
import org.acme.a2a.openai.ticket.triage.orchestrator.model.TriageResult;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.model.TaskState;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The processing steps of the ticket triage route that are easier to express in plain Java than in the Camel DSL.
 */
@ApplicationScoped
public class TriageSupport {

    public static final String PROPERTY_TICKET_ID = "ticketId";
    public static final String PROPERTY_TICKET = "ticket";
    public static final String PROPERTY_TRIAGE = "triage";
    public static final String PROPERTY_AGENT_NAME = "agentName";

    public static final int MAX_SUBJECT_LENGTH = 200;
    public static final int MAX_BODY_LENGTH = 4000;
    public static final int MAX_SUMMARY_LENGTH = 500;

    static final String OPENAI_HOST = "api.openai.com";

    @ConfigProperty(name = "camel.component.openai.base-url")
    String openAiBaseUrl;

    @ConfigProperty(name = "camel.component.openai.model")
    String openAiModel;

    /**
     * Validates the submitted ticket before any money is spent on an LLM call and assigns it an id.
     */
    public void validateTicket(Exchange exchange) {
        Ticket ticket = exchange.getMessage().getBody(Ticket.class);
        if (ticket == null) {
            throw new InvalidTicketException("A ticket with a subject and a body is required");
        }
        requireText("subject", ticket.getSubject(), MAX_SUBJECT_LENGTH);
        requireText("body", ticket.getBody(), MAX_BODY_LENGTH);

        exchange.setProperty(PROPERTY_TICKET, ticket);
        exchange.setProperty(PROPERTY_TICKET_ID, UUID.randomUUID().toString());
    }

    /**
     * Builds the user message for the triage agent. Only the data the model needs is sent to it.
     */
    public void triagePrompt(Exchange exchange) {
        Ticket ticket = exchange.getProperty(PROPERTY_TICKET, Ticket.class);
        exchange.getMessage().setBody("""
                Subject: %s

                Message:
                %s
                """.formatted(ticket.getSubject().strip(), ticket.getBody().strip()));
    }

    /**
     * Sanity checks the {@link TicketTriage} parsed from the model output. The strict JSON schema already prevents
     * unknown categories and priorities, the remaining free text field is bounded here before anything acts on it.
     */
    public void validateTriage(Exchange exchange) {
        Message message = exchange.getMessage();
        TicketTriage triage = message.getBody(TicketTriage.class);
        if (triage == null || triage.getCategory() == null || triage.getPriority() == null) {
            throw new IllegalStateException("The triage agent did not return a category and a priority");
        }
        String summary = triage.getSummary() == null ? "" : triage.getSummary().strip();
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH);
        }
        triage.setSummary(summary);

        exchange.setProperty(PROPERTY_TRIAGE, triage);
    }

    /**
     * Builds the message sent to the specialist agent over A2A: the triage outcome plus the original ticket.
     */
    public void specialistPrompt(Exchange exchange) {
        Ticket ticket = exchange.getProperty(PROPERTY_TICKET, Ticket.class);
        TicketTriage triage = exchange.getProperty(PROPERTY_TRIAGE, TicketTriage.class);
        exchange.getMessage().setBody("""
                Category: %s
                Priority: %s
                Triage summary: %s

                Subject: %s

                Customer message:
                %s
                """.formatted(triage.getCategory(), triage.getPriority(), triage.getSummary(),
                ticket.getSubject().strip(), ticket.getBody().strip()));
    }

    /**
     * Assembles the response for the caller. When a specialist agent handled the ticket, the message body holds the
     * drafted reply and the A2A headers identify the task on the remote agent and its final state.
     */
    public void buildResult(Exchange exchange) {
        Message message = exchange.getMessage();
        TicketTriage triage = exchange.getProperty(PROPERTY_TRIAGE, TicketTriage.class);
        String agentName = exchange.getProperty(PROPERTY_AGENT_NAME, String.class);

        TriageResult result = new TriageResult();
        result.setTicketId(exchange.getProperty(PROPERTY_TICKET_ID, String.class));
        result.setCategory(triage.getCategory());
        result.setPriority(triage.getPriority());
        result.setSummary(triage.getSummary());
        if (agentName != null) {
            // Only a completed task carries a usable reply. Anything else (failed, canceled, input required, ...)
            // is reported as an error to the caller rather than passed off as a drafted reply.
            String taskState = message.getHeader(A2AConstants.TASK_STATE, String.class);
            if (!TaskState.COMPLETED.getProtoName().equals(taskState)) {
                throw new IllegalStateException(agentName + " did not complete the task, state: " + taskState);
            }
            result.setStatus(TriageResult.Status.REPLIED);
            result.setHandledBy(agentName);
            result.setTaskId(message.getHeader(A2AConstants.TASK_ID, String.class));
            result.setReply(message.getBody(String.class));
        } else {
            result.setStatus(TriageResult.Status.ESCALATED);
        }
        message.setBody(result);
    }

    /**
     * Tells the web UI which LLM the triage agent is configured with. Of the base URL, only the host and port are
     * exposed: the path, the query and any credentials in it stay on the server, and so does the API key.
     */
    public void llmInfo(Exchange exchange) {
        String host = null;
        int port = -1;
        try {
            URI uri = URI.create(openAiBaseUrl.strip());
            host = uri.getHost();
            port = uri.getPort();
        } catch (IllegalArgumentException e) {
            // Reported as unknown below
        }

        boolean openAi = OPENAI_HOST.equalsIgnoreCase(host);
        String provider;
        if (openAi) {
            provider = "OpenAI";
        } else if (host == null) {
            provider = "unknown";
        } else {
            provider = port == -1 ? host : host + ":" + port;
        }
        exchange.getMessage().setBody(new LlmInfo(provider, openAiModel, openAi));
    }

    private static void requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidTicketException("The ticket " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new InvalidTicketException("The ticket " + field + " must not exceed " + maxLength + " characters");
        }
    }
}
