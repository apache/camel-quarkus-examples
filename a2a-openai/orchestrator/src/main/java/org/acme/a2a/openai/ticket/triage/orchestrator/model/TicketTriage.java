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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The structured output that the triage agent asks the model for.
 * <p>
 * The OpenAI component derives a strict JSON schema from this class ({@code outputClass} endpoint option), so the model
 * can only answer with the categories and priorities declared here. The {@link JsonPropertyDescription} annotations
 * become part of that schema and help the model fill the fields correctly.
 */
@RegisterForReflection
public class TicketTriage {

    public enum Category {
        BILLING,
        TECHNICAL,
        GENERAL
    }

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    @JsonPropertyDescription("The team that should handle the ticket")
    private Category category;

    @JsonPropertyDescription("How quickly the ticket must be handled")
    private Priority priority;

    @JsonPropertyDescription("One neutral sentence summarizing the customer's request for a support engineer")
    private String summary;

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
