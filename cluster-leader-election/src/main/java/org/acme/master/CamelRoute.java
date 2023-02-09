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
package org.acme.master;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

@ApplicationScoped
public class CamelRoute extends EndpointRouteBuilder {

    @Override
    public void configure() throws Exception {
        // This route is configured to be local (see application.properties)
        // so it will be started regardless of the leadership status of
        // this node.
        from("timer:heartbeat?period=10000")
                .routeId("heartbeat")
                .log("HeartBeating route (timer) {{node.id}} ...");

        // This route is configured to be clustered so it will be started
        // by the controller only when this node is leader
        from("master:{{node.namespace}}:timer:clustered?period=5000")
                .routeId("clustered")
                .log("Clustered route (timer) {{node.id}} ...");
    }
}
