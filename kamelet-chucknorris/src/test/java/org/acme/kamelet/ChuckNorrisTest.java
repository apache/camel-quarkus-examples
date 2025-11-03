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
package org.acme.kamelet;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

@QuarkusTestResource(ChuckNorrisTestResource.class)
@QuarkusTest
public class ChuckNorrisTest {
    @Test
    public void chuckNorrisKamelet() {
        await().atMost(10L, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS).until(() -> {
            List<String> log = Files.readAllLines(Paths.get("target/quarkus.log"));
            return log.stream().anyMatch(line -> line.matches(".*timer.*Chuck.*"));
        });
    }
}
