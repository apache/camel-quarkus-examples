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
package org.acme.partner.gateway;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpHeaderFilterStrategy;

/**
 * Header filter of the platform-http endpoints. Responses get only the headers the gateway sets, never request
 * headers, also when a request is answered before the removeHeaders of its route (a partner token without tenant).
 * A reverse proxy acts on some response headers, for example nginx serves the path of an X-Accel-Redirect.
 */
@RegisterForReflection
public class ResponseHeaderFilter extends HttpHeaderFilterStrategy {

    // The only header the routes add, platform-http writes Content-Type and Content-Length itself
    private static final String RETRY_AFTER = "Retry-After";

    @Override
    public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
        return !RETRY_AFTER.equalsIgnoreCase(headerName);
    }

    // A client cannot send it, it would pass the filter above
    @Override
    public boolean applyFilterToExternalHeaders(String headerName, Object headerValue, Exchange exchange) {
        return RETRY_AFTER.equalsIgnoreCase(headerName)
                || super.applyFilterToExternalHeaders(headerName, headerValue, exchange);
    }
}
