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
package org.acme;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.quarkus.artemis.test.ArtemisTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(H2DatabaseTestResource.class)
@QuarkusTestResource(ArtemisTestResource.class)
public class JtaTest {
    @Test
    public void testXA() {
        String body = UUID.randomUUID().toString();

        given().when().post("/api/messages/" + body)
                .then()
                .statusCode(200);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(
                () -> given().when().get("/api/messages")
                        .then().statusCode(200).extract().asString().contains(body + "-ok"));
    }

    @Test
    public void testRollback() {
        String result = given().when().get("/api/messages").asString();

        given().when().post("/api/messages/fail")
                .then()
                .statusCode(500);

        given().when().get("/api/messages")
                .then()
                .statusCode(200)
                .body(is(result));
    }
}
