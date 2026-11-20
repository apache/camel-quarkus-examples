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
package org.acme.partner.gateway;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.DisabledOnIntegrationTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that the audit table is append-only in the database itself.
 * <ul>
 * <li>UPDATE, DELETE and TRUNCATE on audit_event fail</li>
 * <li>the application role cannot drop or disable the trigger, as Flyway runs as another role</li>
 * </ul>
 * Even a bug or a stolen application connection must not be able to rewrite the audit history.
 * <p>
 * The check runs in JVM mode only: it uses the DataSource bean of the application, and a
 * {@code @QuarkusIntegrationTest} (native or packaged mode) cannot inject beans, so the integration test class
 * reports it as skipped.
 */
@QuarkusTest
@DisabledIfSystemProperty(named = "skip-testcontainers-tests", matches = ".*", disabledReason = "Requires containers")
class AuditImmutabilityTest {

    /**
     * UPDATE, DELETE and TRUNCATE on audit_event are refused by the database triggers.
     * Skipped in native and integration mode, which cannot reach the DataSource bean of the application.
     */
    @Test
    @DisabledOnIntegrationTest("Needs the DataSource of the application")
    void auditEventsCannotBeChangedOrDeleted() throws SQLException {
        // not an @Inject field, @QuarkusIntegrationTest does not allow them
        DataSource dataSource = Arc.container().instance(DataSource.class).get();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet count = statement.executeQuery("SELECT count(*) FROM audit_event")) {
                count.next();
                assertTrue(count.getLong(1) > 0, "Camel startup writes audit records");
            }

            for (String tampering : new String[] {
                    "UPDATE audit_event SET reason = 'tampered'",
                    "DELETE FROM audit_event",
                    "TRUNCATE audit_event" }) {
                SQLException e = assertThrows(SQLException.class, () -> statement.executeUpdate(tampering));
                // the runtime role has no such privilege, the trigger stops the owner as well
                assertTrue(e.getMessage().contains("permission denied")
                        || e.getMessage().contains("audit_event is append-only"), e.getMessage());
            }
        }
    }

    /**
     * The runtime role does not own the tables, Flyway does: it cannot drop or disable the trigger.
     */
    @Test
    @DisabledOnIntegrationTest("Needs the DataSource of the application")
    void applicationRoleCannotRemoveTheTrigger() throws SQLException {
        DataSource dataSource = Arc.container().instance(DataSource.class).get();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                for (String tampering : new String[] {
                        "ALTER TABLE audit_event DISABLE TRIGGER audit_event_no_update_or_delete",
                        "DROP TRIGGER audit_event_no_update_or_delete ON audit_event" }) {
                    SQLException e = assertThrows(SQLException.class, () -> statement.execute(tampering));
                    assertTrue(e.getMessage().contains("must be owner"), e.getMessage());
                    connection.rollback();
                }
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }
}
