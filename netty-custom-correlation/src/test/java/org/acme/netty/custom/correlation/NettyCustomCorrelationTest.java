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
package org.acme.netty.custom.correlation;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

@QuarkusTest
class NettyCustomCorrelationTest {

    @Test
    public void testWithCorrelation() {
        await().atMost(20L, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    String log = Files.readString(Paths.get("target/quarkus.log"));
                    //we should receive both correlated and uncorrelated response
                    Assertions.assertTrue(log.contains("(correct reply)"));
                    Assertions.assertFalse(log.contains("(wrong reply)"));
                    //we should wait for at least 5 responses
                    Assertions.assertTrue(log.contains("Server response: #5"));
                });
    }
}
