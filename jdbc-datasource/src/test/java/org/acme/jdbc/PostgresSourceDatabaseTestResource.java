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
package org.acme.jdbc;

import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.TestcontainersConfiguration;

import static org.apache.camel.util.CollectionHelper.mapOf;

public class PostgresSourceDatabaseTestResource<T extends GenericContainer> implements QuarkusTestResourceLifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresSourceDatabaseTestResource.class);

    private static final int POSTGRES_PORT = 5432;
    private static final String POSTGRES_IMAGE = "docker.io/postgres:15.0";

    private static final String POSTGRES_SOURCE_DB_NAME = "source_db";
    private static final String POSTGRES_SOURCE_PASSWORD = "1234567@8_source";
    private static final String POSTGRES_SOURCE_USER = "ETL_source_user";

    private GenericContainer<?> sourceDbContainer;

    @Override
    public Map<String, String> start() {
        LOG.info(TestcontainersConfiguration.getInstance().toString());

        sourceDbContainer = new GenericContainer<>(POSTGRES_IMAGE)
                .withExposedPorts(POSTGRES_PORT)
                .withEnv("POSTGRES_USER", POSTGRES_SOURCE_USER)
                .withEnv("POSTGRES_PASSWORD", POSTGRES_SOURCE_PASSWORD)
                .withEnv("POSTGRES_DB", POSTGRES_SOURCE_DB_NAME)
                .withClasspathResourceMapping("init-source-db.sql", "/docker-entrypoint-initdb.d/init-source-db.sql",
                        BindMode.READ_ONLY)
                .withLogConsumer(new Slf4jLogConsumer(LOG)).waitingFor(Wait.forListeningPort());
        sourceDbContainer.start();

        // Print Postgres server connectivity information
        String sourceJbdcUrl = String.format("jdbc:postgresql://%s:%s/%s", sourceDbContainer.getHost(),
                sourceDbContainer.getMappedPort(POSTGRES_PORT), POSTGRES_SOURCE_DB_NAME);
        LOG.info("The test source_db could be accessed through the following JDBC url: " + sourceJbdcUrl);

        return mapOf("quarkus.datasource.source_db.jdbc.url", sourceJbdcUrl,
                "timer.period", "100",
                "timer.delay", "0");
    }

    @Override
    public void stop() {
        try {
            if (sourceDbContainer != null) {
                sourceDbContainer.stop();
            }
        } catch (Exception ex) {
            LOG.error("An issue occured while stopping the sourceDbContainer", ex);
        }
    }

}
