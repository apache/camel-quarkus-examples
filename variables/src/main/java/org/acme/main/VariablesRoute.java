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
package org.acme.main;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

/**
 * A simple {@link RouteBuilder}.
 */
@ApplicationScoped
public class VariablesRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("timer:random?period={{timer.period:1000}}").startupOrder(1)
                .setBody(simple("${variable.global:greeting} ${variable.global:fromApp}: ${variable.global:random}"))
                .log("${body}");

        from("timer:java?delay={{timer.delay:5000}}").startupOrder(2)
                .setVariable("global:fromApp", simple("from ${camelId}"))
                .setVariable("global:random", simple("${random(1,10)}"));
    }
}
