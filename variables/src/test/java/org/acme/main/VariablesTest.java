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
package org.acme.main;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

@QuarkusTest
public class VariablesTest {

    @Test
    public void testVariables() {
        Config config = ConfigProvider.getConfig();
        String greeting = config.getValue("camel.variable.greeting", String.class);
        String contextName = config.getValue("camel.context.name", String.class);
        String initRandom = config.getValue("camel.variable.random", String.class);

        Pattern p = Pattern.compile(String.format(".* %s from %s: \\d.*", greeting, contextName), Pattern.DOTALL);

        await().atMost(10L, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS).until(() -> {
            String log = new String(Files.readAllBytes(Paths.get("target/quarkus.log")), StandardCharsets.UTF_8);
            return log.contains(String.format("%s : %s", greeting, initRandom)) &&
                    p.matcher(log).matches();
        });
    }
}
