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
package org.acme.routes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class KeycloakAdminRoute extends RouteBuilder {

    @Override
    public void configure() {
        // Error handling for Keycloak operations
        onException(Exception.class)
                .handled(true)
                .setHeader("Content-Type", constant("application/json"))
                .setHeader("CamelHttpResponseCode", constant(500))
                .setBody(constant("{\"error\": \"Internal server error\", \"message\": \"${exception.message}\"}"))
                .log("Error in Keycloak admin route: ${exception.message}");

        rest("/api/admin")
                .get("/users")
                .produces("application/json")
                .to("direct:list-users");

        from("direct:list-users")
                .routeId("keycloak-admin-users")
                .log("Admin endpoint accessed - returning user list")
                .process(exchange -> {
                    // For this example, return a mock user list demonstrating the endpoint works
                    // In production, you would configure proper Keycloak Admin API access
                    List<Map<String, Object>> users = new ArrayList<>();

                    Map<String, Object> customer = new HashMap<>();
                    customer.put("username", "customer");
                    customer.put("email", "customer@example.com");
                    customer.put("role", "customer-role");
                    users.add(customer);

                    Map<String, Object> admin = new HashMap<>();
                    admin.put("username", "admin");
                    admin.put("email", "admin@example.com");
                    admin.put("role", "admin-role");
                    users.add(admin);

                    exchange.getMessage().setBody(users);
                })
                .marshal().json();
    }
}
