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
package org.acme.observability;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;

public class TimerRoute extends RouteBuilder {

    AtomicInteger counter = new AtomicInteger();

    @Override
    public void configure() throws Exception {
        from("timer:greeting?delay=10000&period=10000")
                .setBody(exchange -> "Custom body #" + counter.incrementAndGet())
                .log("Generated from timer: ${body}")
                .bean("timerCounter", "count")
                .to("http://{{greeting-app.service.host}}:{{greeting-app.service.port}}/greeting");
    }
}
