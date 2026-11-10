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

import java.lang.reflect.Method;

import javax.net.ssl.SSLParameters;

/**
 * {@code SSLParameters.get/setNamedGroups} exist since JDK 20. Reflection keeps the module compilable with
 * {@code release} 17, which CI uses on JDK 17 and 21. The tests that call this run on JDK 25+ only.
 */
final class NamedGroups {

    private NamedGroups() {
    }

    static String[] get(SSLParameters parameters) {
        return (String[]) invoke(parameters, "getNamedGroups");
    }

    static void set(SSLParameters parameters, String[] namedGroups) {
        invoke(parameters, "setNamedGroups", (Object) namedGroups);
    }

    private static Object invoke(SSLParameters parameters, String name, Object... args) {
        try {
            Method method = args.length == 0
                    ? SSLParameters.class.getMethod(name)
                    : SSLParameters.class.getMethod(name, String[].class);
            return method.invoke(parameters, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
