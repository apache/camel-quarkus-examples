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
package org.acme.a2a.openai.ticket.triage.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The outcome of processing a ticket, returned to the caller of {@code POST /tickets}.
 */
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageResult {

    public enum Status {
        /** A specialist agent drafted a reply. */
        REPLIED,
        /** No specialist agent handles this category, a human must pick the ticket up. */
        ESCALATED
    }

    private String ticketId;
    private TicketTriage.Category category;
    private TicketTriage.Priority priority;
    private String summary;
    private Status status;
    private String handledBy;
    private String taskId;
    private String reply;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public TicketTriage.Category getCategory() {
        return category;
    }

    public void setCategory(TicketTriage.Category category) {
        this.category = category;
    }

    public TicketTriage.Priority getPriority() {
        return priority;
    }

    public void setPriority(TicketTriage.Priority priority) {
        this.priority = priority;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }
}
