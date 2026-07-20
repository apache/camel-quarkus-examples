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
package org.acme.cyberark.vault;

import java.util.UUID;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;

@Certificates(baseDir = "target/certs", certificates = {
        @Certificate(name = "nginx", formats = { Format.PEM }, cn = "proxy", subjectAlternativeNames = "proxy")
})
@QuarkusTest
@WithTestResource(CyberarkVaultTestResource.class)
class CyberarkVaultTest {
    @Test
    void testCreateGetAndPropertyPlaceholder() {
        String secret = UUID.randomUUID().toString();

        RestAssured.given()
                .body(secret)
                .post("/cyberark-vault/createSecret")
                .then()
                .statusCode(204);

        RestAssured
                .get("/cyberark-vault/getSecret")
                .then()
                .statusCode(200)
                .body(is(secret));

        RestAssured
                .get("/cyberark-vault/propertyPlaceholder")
                .then()
                .statusCode(200)
                .body(is(secret));
    }
}
