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

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Describes the LLM that the triage agent talks to, for display in the web UI.
 */
@RegisterForReflection
public class LlmInfo {

    private final String provider;
    private final String model;
    private final boolean openAi;

    public LlmInfo(String provider, String model, boolean openAi) {
        this.provider = provider;
        this.model = model;
        this.openAi = openAi;
    }

    /**
     * {@code OpenAI}, or the host of the OpenAI compatible API that is used instead.
     */
    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    /**
     * Whether the requests go to the OpenAI API itself rather than to another OpenAI compatible API.
     */
    public boolean isOpenAi() {
        return openAi;
    }
}
