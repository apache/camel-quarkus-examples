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

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.component.ai.tool.AiToolEndpoint;

/**
 * Operations for the support staff: the kill switch of the tools.
 * <p>
 * The kill switch accepts only {@code Content-Type: application/json}: a cross-site HTML form cannot send it, so a page
 * open in the browser of a logged-in support engineer cannot switch a tool (CSRF).
 */
@Path("/admin")
@RolesAllowed("support")
public class AdminResource {

    @Inject
    CamelContext camelContext;

    /**
     * Stops the route of a tool. The tool disappears from the chat agent and from the MCP server, whose clients are
     * notified that the tool list changed.
     */
    @POST
    @Path("/tools/{tool}/disable")
    @Consumes(MediaType.APPLICATION_JSON)
    public void disable(@PathParam("tool") String tool) throws Exception {
        camelContext.getRouteController().stopRoute(toolRouteId(tool));
    }

    /**
     * Starts the route of a tool again, which brings the tool back.
     */
    @POST
    @Path("/tools/{tool}/enable")
    @Consumes(MediaType.APPLICATION_JSON)
    public void enable(@PathParam("tool") String tool) throws Exception {
        camelContext.getRouteController().startRoute(toolRouteId(tool));
    }

    // The route id is the tool name. Only tool routes can be switched off this way.
    private String toolRouteId(String tool) {
        Route route = camelContext.getRoute(tool);
        if (route == null || !(route.getEndpoint() instanceof AiToolEndpoint)) {
            throw new NotFoundException();
        }
        return tool;
    }
}
