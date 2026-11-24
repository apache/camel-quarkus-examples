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

/**
 * Error body as defined by RFC 9457 (Problem Details for HTTP APIs).
 */
@RegisterForReflection
public record Problem(String type, String title, int status, String detail) {

    public Problem(String title, int status, String detail) {
        this("about:blank", title, status, detail);
    }
}
