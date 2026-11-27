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
package org.acme.ai.tool.support.desk;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Headers;

/**
 * The customer chat, and the query of {@link ChatResource}. The tools are in
 * {@link ToolRoutes}.
 */
@ApplicationScoped
public class SupportDeskRoutes extends RouteBuilder {

    @Override
    public void configure() {
        // The customer chat: ChatResource sends the customer's message with the authenticated user in the customerId
        // exchange property.
        from("direct:chat")
                .routeId("chat")
                // No error handler: ChatResource turns a failure into the HTTP response, for example HTTP 429 for a
                // second message
                .errorHandler(noErrorHandler())
                // One message at a time per customer: a second concurrent message is rejected (HTTP 429)
                .throttle(1).concurrentRequestsMode()
                .correlationExpression(exchangeProperty(ChatResource.CUSTOMER_ID))
                .rejectExecution(true)
                .setHeader(Headers.SYSTEM_MESSAGE, simple("resource:classpath:prompts/support-agent.txt"))
                // Each customer has their own conversation memory
                .setHeader(Headers.MEMORY_ID, exchangeProperty(ChatResource.CUSTOMER_ID))
                // The agent gets the ai-tool routes tagged "customer" as tools, see ToolRoutes, and may make at most 5
                // rounds of tool calls to answer one message
                .to("langchain4j-agent:support?agentConfiguration=#supportAgentConfiguration&tags=customer"
                        + "&maxToolCallingRoundTrips=5");

        // The returns of the customer, for ChatResource
        from("direct:customer-returns")
                .routeId("customer-returns")
                .to("sql:SELECT CAST(COALESCE(json_agg(json_build_object('returnId', id, 'orderId', order_id, 'amount', amount,"
                        + " 'status', status) ORDER BY id), '[]') AS TEXT) FROM returns"
                        + " WHERE customer_id = :#${exchangeProperty.customerId}"
                        + "?outputType=SelectOne");
    }
}
