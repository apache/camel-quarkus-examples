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

import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

/**
 * A stand-in for the carrier API, so that the example runs without an account. It only runs in dev mode: elsewhere,
 * point {@code support-desk.carrier.url} at the real API.
 */
@ApplicationScoped
public class StandInRoutes extends RouteBuilder {

    @Override
    public void configure() {
        // Not @IfBuildProfile("dev"): Camel Quarkus adds the routes of a RouteBuilder class even when it is not a bean
        if (LaunchMode.current() != LaunchMode.DEVELOPMENT) {
            return;
        }

        // The carrier API
        from("platform-http:/fake/carrier/shipments/{trackingNumber}?httpMethodRestrict=GET&produces=application/json")
                .routeId("fake-carrier")
                .setBody(simple("{\"trackingNumber\": \"${header.trackingNumber}\", \"status\": \"IN_TRANSIT\","
                        + " \"estimatedDelivery\": \"${date:now+48h:yyyy-MM-dd}\"}"));
    }
}
