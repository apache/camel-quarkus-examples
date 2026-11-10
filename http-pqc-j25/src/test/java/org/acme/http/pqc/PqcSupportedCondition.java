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
package org.acme.http.pqc;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips the tests when the running JDK's TLS stack (SunJSSE) does not support X25519MLKEM768 (JEP 527), e.g. JDK 25
 * before 25.0.5. The application would fail at startup there.
 */
class PqcSupportedCondition implements ExecutionCondition {

    private static final String HYBRID_GROUP = "X25519MLKEM768";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return isSupported()
                ? ConditionEvaluationResult.enabled(HYBRID_GROUP + " is supported by the JDK")
                : ConditionEvaluationResult.disabled(HYBRID_GROUP + " is not supported by JDK "
                        + Runtime.version() + ", JDK 25.0.5+ is required");
    }

    static boolean isSupported() {
        try {
            String[] groups = SSLContext.getDefault().getDefaultSSLParameters().getNamedGroups();
            return groups != null && Arrays.asList(groups).contains(HYBRID_GROUP);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
