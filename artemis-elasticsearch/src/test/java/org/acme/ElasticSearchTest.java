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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import org.acme.resource.CustomPahoTestResource;
import org.acme.resource.ElasticSearchTestResource;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(CustomPahoTestResource.class)
@QuarkusTestResource(ElasticSearchTestResource.class)
public class ElasticSearchTest {

    @Test
    public void baseTest() throws IOException {

        final ObjectMapper mapper = new ObjectMapper();

        Map<String, String> map = new HashMap<>();

        map.put("devices", "Hey you");

        String jsonString = mapper.writeValueAsString(map);

        RestAssured.registerParser("text/plain", Parser.JSON);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(jsonString)
                .when()
                .post("/devices")
                .then()
                .statusCode(200)
                .body("devices", is("Hey you"));

    }
}
