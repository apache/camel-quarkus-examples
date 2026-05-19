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
package org.acme.http.pqc.profiles;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Test profile for PQC with classical fallback.
 * Activates the "pqc-with-fallback" profile which configures X25519MLKEM768,secp256r1.
 */
public class PqcWithFallbackProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "pqc-with-fallback";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("jdk.tls.namedGroups", "X25519MLKEM768,x25519,secp256r1");
    }
}
