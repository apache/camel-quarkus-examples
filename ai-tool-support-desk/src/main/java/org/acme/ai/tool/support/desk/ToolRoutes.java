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

/**
 * The AI tools of the support desk. Each route is one tool: the route id is the tool name, the ai-tool endpoint
 * describes the tool to the model and the steps hold the business rules.
 * <p>
 * The tools tagged {@code customer} are given to the customer chat agent (see {@link SupportDeskRoutes}), the tools
 * tagged {@code support} are published to the support staff by the MCP server ({@code quarkus.camel.mcp-server.tags} in
 * {@code application.properties}).
 * <p>
 * The customer tools only ever act on the orders of the logged-in customer: {@link ChatResource} puts the authenticated
 * user into the {@code customerId} exchange property and the SQL statements filter by it. The model cannot choose the
 * customer. The customer tools that change something are guarded by the {@code verifiedCustomers} authorization policy
 * ({@link VerifiedCustomerPolicy}): only verified customers may call them.
 */
@ApplicationScoped
public class ToolRoutes extends RouteBuilder {

    @Override
    public void configure() {
        from("ai-tool:get_order?tags=customer"
                + "&description=Get an order of the customer with its status, delivery date and items"
                + "&parameter.orderId=integer"
                + "&parameter.orderId.description=The order number"
                + "&parameter.orderId.required=true"
                + "&readOnlyHint=true")
                .routeId("get_order")
                .to("sql:classpath:sql/get-order.sql?outputType=SelectOne")
                .choice()
                .when(simple("${body} == null"))
                .setBody(simple("Order ${header.orderId} not found"))
                .end();

        from("ai-tool:track_shipment?tags=customer"
                + "&description=Track the shipment of an order of the customer with the carrier"
                + "&parameter.orderId=integer"
                + "&parameter.orderId.description=The order number"
                + "&parameter.orderId.required=true"
                + "&readOnlyHint=true"
                + "&openWorldHint=true")
                .routeId("track_shipment")
                // Only shipments on their way are tracked, get_order tells about the other orders
                .to("sql:SELECT tracking_number FROM orders WHERE id = CAST(:#orderId AS INTEGER)"
                        + " AND customer_id = :#${exchangeProperty.customerId} AND status = 'SHIPPED'"
                        + "?outputType=SelectOne")
                .choice()
                .when(simple("${body} == null"))
                .setBody(simple("Order ${header.orderId} is not on its way, get_order tells its status"))
                .stop()
                .end()
                .setProperty("trackingNumber", simple("${body}"))
                // Only the tracking number leaves the application, none of the headers of the tool call
                .removeHeaders("*")
                // A slow or failing carrier API must not break the conversation
                .circuitBreaker()
                .faultToleranceConfiguration().timeoutEnabled(true).timeoutDuration(5000).end()
                .toD("{{support-desk.carrier.url}}/shipments/${exchangeProperty.trackingNumber}?httpMethod=GET")
                .convertBodyTo(String.class)
                .onFallback()
                .setBody(constant("The carrier tracking service is not available right now, try again later"))
                .end();

        from("ai-tool:request_return?tags=customer"
                + "&description=Request the return of items of a delivered order of the customer, for a refund."
                + " Returns are accepted up to 30 days after delivery, and an order can be returned only once."
                + " Refunds up to 50 EUR are approved right away, larger ones are reviewed by the support staff."
                + "&argSchema=classpath:schemas/request-return.json"
                + "&authorizationPolicy=#verifiedCustomers")
                .routeId("request_return")
                // The reason must be one of the codes of the returns table. The model learns which ones, rather than
                // that the tool failed.
                .choice()
                .when(simple("${header.reason} !in 'DEFECTIVE,DAMAGED,WRONG_ITEM,NOT_AS_EXPECTED,NO_LONGER_NEEDED'"))
                .setBody(constant(
                        "The reason must be one of DEFECTIVE, DAMAGED, WRONG_ITEM, NOT_AS_EXPECTED and NO_LONGER_NEEDED"))
                .stop()
                .end()
                // PostgreSQL gets the requested items as JSON
                .setBody(header("items"))
                .marshal().json()
                .setProperty("items", simple("${bodyAs(String)}"))
                .to("sql:classpath:sql/check-return.sql?outputType=SelectOne")
                // The return rules
                .choice()
                .when(simple("${body} == null"))
                .setBody(simple("Order ${header.orderId} not found"))
                .stop()
                .when(simple("${body[delivered]} != true"))
                .setBody(simple("Order ${header.orderId} has not been delivered yet and cannot be returned"))
                .stop()
                .when(simple("${body[inReturnWindow]} != true"))
                .setBody(simple("Order ${header.orderId} was delivered more than 30 days ago, it can no longer be returned"))
                .stop()
                .when(simple("${body[itemsOnOrder]} != true"))
                .setBody(simple("The requested items or quantities are not part of order ${header.orderId}"))
                .stop()
                .when(simple("${body[autoApprove]} == true"))
                .setProperty("status", constant("APPROVED"))
                .otherwise()
                .setProperty("status", constant("PENDING_APPROVAL"))
                .end()
                .setProperty("amount", simple("${body[amount]}"))
                .setProperty("returnItems", simple("${body[items]}"))
                .to("sql:classpath:sql/insert-return.sql?outputType=SelectOne")
                .choice()
                .when(simple("${body} == null"))
                .setBody(simple("Order ${header.orderId} already has a return"))
                .when(simple("${exchangeProperty.status} == 'APPROVED'"))
                .setBody(simple("Return ${body} of ${exchangeProperty.amount} EUR approved"))
                .otherwise()
                .setBody(simple("Return ${body} of ${exchangeProperty.amount} EUR created,"
                        + " it waits for the approval of the support staff"))
                .end();

        from("ai-tool:escalate_to_human?tags=customer"
                + "&description=Hand the conversation over to a human support engineer by opening a ticket."
                + " Use it when the customer asks for a human or when the other tools cannot solve the problem."
                + "&parameter.summary=string"
                + "&parameter.summary.description=A short summary of the problem for the support engineer"
                + "&parameter.summary.required=true"
                + "&parameter.priority=string"
                + "&parameter.priority.description=How urgent the problem is"
                + "&parameter.priority.enum=LOW,MEDIUM,HIGH,URGENT"
                + "&parameter.priority.required=true"
                + "&authorizationPolicy=#verifiedCustomers")
                .routeId("escalate_to_human")
                // The priority must be one of the priorities of the tickets table. The model learns which ones, rather
                // than that the tool failed.
                .choice()
                .when(simple("${header.priority} !in 'LOW,MEDIUM,HIGH,URGENT'"))
                .setBody(constant("The priority must be one of LOW, MEDIUM, HIGH and URGENT"))
                .stop()
                .end()
                .to("sql:classpath:sql/open-ticket.sql?outputType=SelectOne")
                .setBody(simple("Ticket ${body} opened, a support engineer will contact the customer by email"));

        from("ai-tool:list_pending_returns?tags=support"
                + "&description=List the returns that wait for the approval of the support staff,"
                + " with their order, items and customer"
                + "&readOnlyHint=true"
                // Published to MCP clients as true when unset, as the MCP specification defines
                + "&openWorldHint=false")
                .routeId("list_pending_returns")
                .to("sql:classpath:sql/list-pending-returns.sql?outputType=SelectOne");

        from("ai-tool:decide_return?tags=support"
                + "&description=Approve or reject a return that waits for approval"
                + "&parameter.returnId=integer"
                + "&parameter.returnId.description=The id of the return, as listed by list_pending_returns"
                + "&parameter.returnId.required=true"
                + "&parameter.decision=string"
                + "&parameter.decision.enum=approve,reject"
                + "&parameter.decision.required=true"
                + "&parameter.note=string"
                + "&parameter.note.description=Why the return is approved or rejected"
                + "&destructiveHint=true"
                + "&idempotentHint=true"
                + "&openWorldHint=false")
                .routeId("decide_return")
                .choice()
                .when(simple("${header.decision} == 'approve'"))
                .setProperty("status", constant("APPROVED"))
                .when(simple("${header.decision} == 'reject'"))
                .setProperty("status", constant("REJECTED"))
                .otherwise()
                .setBody(constant("The decision must be approve or reject"))
                .stop()
                .end()
                // Only a pending return changes: deciding again, or deciding an unknown return, changes nothing. The
                // note is cut to the size of its column.
                .to("sql:UPDATE returns SET status = :#${exchangeProperty.status}, note = LEFT(:#${header.note}, 500)"
                        + " WHERE id = CAST(:#returnId AS INTEGER) AND status = 'PENDING_APPROVAL' RETURNING id"
                        + "?outputType=SelectOne")
                .choice()
                .when(simple("${body} == null"))
                .setBody(simple("Return ${header.returnId} does not wait for approval, nothing changed"))
                .when(simple("${exchangeProperty.status} == 'APPROVED'"))
                .setBody(simple("Return ${body} approved"))
                .otherwise()
                .setBody(simple("Return ${body} rejected"))
                .end();
    }
}
