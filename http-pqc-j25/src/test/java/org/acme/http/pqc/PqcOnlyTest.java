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

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.acme.http.pqc.profiles.PqcOnlyProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Server with {@code quarkus.tls.pqc-enforcement-policy=strict}: only the default post-quantum groups are offered.
 *
 * Expected results:
 * <ul>
 * <li>PQC capable client: SUCCESS</li>
 * <li>classical client: FAILURE, the handshake is rejected because no post-quantum group is negotiated</li>
 * </ul>
 */
@QuarkusTest
@TestProfile(PqcOnlyProfile.class)
@EnabledIf("isJdkPqcAvailable")
class PqcOnlyTest extends AbstractPqcTest {

    @Test
    void pqcClientConnects() throws Exception {
        assertPqcClientConnects();
    }

    @Test
    void classicalClientIsRejected() {
        assertClassicalClientIsRejected();
    }
}
