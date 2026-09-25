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
package org.acme.langchain4j.ingest.rag;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * The assistant. There is no RAG code here: the langchain4j-embeddingstore extension, which the
 * ingest extension depends on, produces a RetrievalAugmentor from the application's embedding
 * store and model, and Quarkus LangChain4j picks it up as the default, so every question is
 * augmented with the segments the pipeline ingested.
 */
@RegisterAiService
public interface DocumentAssistant {

    @SystemMessage("""
            You answer questions about the company's documents.
            Use ONLY the information provided together with the question.
            If it does not contain the answer, reply: I don't have complete information on that.
            Be concise.""")
    String answer(@UserMessage String question);
}
