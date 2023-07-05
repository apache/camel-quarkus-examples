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

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class Routes extends RouteBuilder {

    @Inject
    MeterRegistry registry;

    private void countGreeting(Exchange exchange) {
        // This is our custom metric: just counting how many times the method is called
        registry.counter("org.acme.observability.greeting", "type", "events", "purpose", "example").increment();
    }

    @Override
    public void configure() throws Exception {
        from("platform-http:/greeting")
                .removeHeaders("*")
                .process(this::countGreeting)
                .to("http://localhost:{{greeting-provider-app.service.port}}/greeting-provider");

        from("platform-http:/greeting-provider")
                // Random delay to simulate latency
                .to("micrometer:counter:org.acme.observability.greeting-provider?tags=type=events,purpose=example")
                .delay(simple("${random(1000, 5000)}"))
                .setBody(constant("Hello From Camel Quarkus!"));
    }

}
