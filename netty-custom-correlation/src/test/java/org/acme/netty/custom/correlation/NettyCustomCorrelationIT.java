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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import io.quarkus.test.common.TestLog;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class NettyCustomCorrelationIT extends NettyCustomCorrelationTest {

    @Override
    protected Path logFilePath(TestLog testLog) throws IOException {
        Path configuredPath = testLog.getLogFilePath();
        try (Stream<Path> paths = Files.list(configuredPath.getParent())) {
            return paths.filter(path -> path.getFileName().toString().startsWith("test-"))
                    .map(path -> path.resolve(configuredPath.getFileName()))
                    .filter(Files::exists)
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElse(configuredPath);
        }
    }
}
