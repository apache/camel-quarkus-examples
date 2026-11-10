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
 * PQC-only server: the policy is switched to {@code strict}. Vert.x then replaces the configured key exchange groups
 * with the hybrid PQC groups (X25519MLKEM768, SecP256r1MLKEM768, SecP384r1MLKEM1024), so classical-only clients are
 * rejected.
 */
public class PqcOnlyProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.tls.pqc-enforcement-policy", "strict");
    }
}
