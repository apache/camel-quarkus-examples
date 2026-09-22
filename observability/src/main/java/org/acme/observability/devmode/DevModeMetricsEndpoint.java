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
package org.acme.observability.devmode;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.arc.profile.IfBuildProfile;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Exposes the Prometheus registry on the main HTTP port, in dev mode only.
 *
 * <p>
 * The Grafana LGTM dev service configures its Prometheus to scrape {@code quarkus.http.port} at
 * {@code quarkus.management.root-path} + {@code /metrics}. It has no support for the Quarkus management interface,
 * which is what {@code camel-quarkus-observability-services} enables and serves metrics from (port 9876, path
 * {@code /observe/metrics}), so the scrape target would otherwise never come up and every metrics panel in Grafana
 * would stay empty.
 *
 * <p>
 * Serving the same registry from {@code /q/metrics} on the application port gives the dev service something to
 * scrape, without moving the endpoint that the rest of this example demonstrates. The bean is not built into the
 * application outside of dev mode.
 */
@IfBuildProfile("dev")
@ApplicationScoped
public class DevModeMetricsEndpoint {

    @Inject
    PrometheusMeterRegistry registry;

    void registerMetricsRoute(@Observes Router router) {
        router.get("/q/metrics").handler(ctx -> ctx.response()
                .putHeader("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
                .end(registry.scrape()));
    }
}
