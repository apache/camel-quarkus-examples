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
package org.acme.observability.health.camel;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;

/**
 * A simple custom liveness check which utilizes the Camel Health API.
 *
 * The check status is recorded as DOWN on every 5th invocation.
 */
public class CustomLivenessCheck extends AbstractHealthCheck {

    AtomicInteger hitCount = new AtomicInteger();

    public CustomLivenessCheck() {
        super("custom-liveness-check");
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        int hits = hitCount.incrementAndGet();

        // Flag the check as DOWN on every 5th invocation (but not on Kubernetes), else it is UP
        if (hits % 5 == 0 && System.getenv("KUBERNETES_PORT") == null) {
            builder.down();
        } else {
            builder.up();
        }
    }

    @Override
    public boolean isReadiness() {
        return false;
    }

    @Override
    public boolean isLiveness() {
        return true;
    }
}
