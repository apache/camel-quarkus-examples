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
package org.acme.bindy.ftp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.camel.util.CollectionHelper;
import org.apache.commons.io.FileUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class FtpTestResource implements QuarkusTestResourceLifecycleManager {

    private static final int FTP_PORT = 2222;
    private static final String SSH_IMAGE = "quay.io/jamesnetherton/sftp-server:0.3.0";

    private GenericContainer container;

    @Override
    public Map<String, String> start() {
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "books");
        try {
            FileUtils.deleteDirectory(tmpDir.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            container = new GenericContainer(SSH_IMAGE)
                    .withExposedPorts(FTP_PORT)
                    .withEnv("PASSWORD_ACCESS", "true")
                    .withEnv("FTP_USER", "ftpuser")
                    .withEnv("FTP_PASSWORD", "ftppassword")
                    .waitingFor(Wait.forListeningPort());

            container.start();

            return CollectionHelper.mapOf(
                    "ftp.host", container.getContainerIpAddress(),
                    "ftp.port", container.getMappedPort(FTP_PORT).toString(),
                    "timer.delay", "100");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
