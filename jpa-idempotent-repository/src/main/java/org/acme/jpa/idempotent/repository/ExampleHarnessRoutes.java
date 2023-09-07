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
package org.acme.jpa.idempotent.repository;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

/**
 * The example harness provide a timer based route creating some input files for the main example route in
 * JpaIdempotentRoute.java. It also defines a platform-http based route that mock the costly API.
 */
@ApplicationScoped
public class ExampleHarnessRoutes extends RouteBuilder {

    private static int COUNT = 1;

    @Override
    public void configure() {
        /**
         * Generate some example input files
         */
        from("timer:createExampleInputFiles?delay={{timer.delay}}&period={{timer.period}}&repeatCount={{timer.repeatCount}}")
                /**
                 * Populate the content of each file with a series of odd numbers 1,3,1,5,1,7 Note that the value 1 is a
                 * doublon in the series, so some of the files have a duplicate content
                 */
                .setBody(e -> Integer.toString(++COUNT % 2 == 0 ? 1 : COUNT))
                .log("-----------------------------------------------------------------")
                .log("Creating an example input file with content ${body}")
                /**
                 * Arbitrary delay to ensure logs are printed in human friendly order most of the time
                 */
                .delay(constant(1000))
                /**
                 * Create the file with the generated content in the target/input-files folder
                 */
                .to("file:target/input-files");

        /**
         * Consume the incoming API calls.
         */
        from("platform-http:/costly-api-call")
                /**
                 * Delegate treatment to the bean named costlyApiService defined in CostlyApiService.java
                 */
                .bean("costlyApiService");

        /**
         * Creates a service that return the set of content that were already consumed.
         */
        from("platform-http:/content-set")
                .bean("costlyApiService", "getContentSet");
    }
}
