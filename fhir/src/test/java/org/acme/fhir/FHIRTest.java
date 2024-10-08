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
package org.acme.fhir;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@WithTestResource(FHIRTestResource.class)
@QuarkusTest
class FHIRTest {
    @Test
    void uploadPatientDetail() {
        JsonPath response = RestAssured.given()
                .header("Content-Type", "text/plain; charset=US-ASCII")
                .body(FHIRTest.class.getResourceAsStream("/data/hl7v2.patient"))
                .post("/api/patients")
                .then()
                .statusCode(200)
                .body(
                        "id", notNullValue(),
                        "name[0].family", equalTo("Price"),
                        "name[0].given[0]", equalTo("Vincent"))
                .extract()
                .body()
                .jsonPath();

        String id = response.getString("id");

        RestAssured.get("/api/patients/" + id)
                .then()
                .statusCode(200)
                .body(
                        "id", notNullValue(),
                        "name[0].family", equalTo("Price"),
                        "name[0].given[0]", equalTo("Vincent"));

        RestAssured.get("/api/patients/999")
                .then()
                .statusCode(404)
                .body(equalTo("Patient with id 999 not found"));
    }
}
