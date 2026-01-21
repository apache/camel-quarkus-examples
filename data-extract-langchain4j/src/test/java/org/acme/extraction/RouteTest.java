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
package org.acme.extraction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@QuarkusTestResource(OllamaTestResource.class)
@QuarkusTest
public class RouteTest {

    @Test
    void unstructuredFileTranscriptsAreTransformedToPojos() throws IOException {

        FileUtils.deleteQuietly(new File("target/transcripts-tmp/"));
        FileUtils.deleteQuietly(new File("target/transcripts/"));

        FileUtils.copyDirectory(new File("src/test/resources/transcripts/"), new File("target/transcripts-tmp/"));
        Files.move(Paths.get("target/transcripts-tmp"), Paths.get("target/transcripts"), StandardCopyOption.ATOMIC_MOVE);

        await().pollInterval(200, TimeUnit.MILLISECONDS).atMost(3, TimeUnit.MINUTES)
                .until(
                        () -> {
                            return given()
                                    .contentType(ContentType.JSON)
                                    .when()
                                    .get("/custom-pojo-store")
                                    .path("pojos.size()").equals(3);
                        });

        given()
                .contentType(ContentType.JSON)
                .when()
                .get("/custom-pojo-store")
                .then()
                .statusCode(200)
                // Assert values of the first extracted POJO
                .body("pojos[0].customerSatisfied", is("true"))
                .body("pojos[0].customerName", is("Sarah London"))
                .body("pojos[0].customerBirthday", is("10 JULY 1986"))
                .body("pojos[0].summary", not(empty()))
                // Assert values of the second extracted POJO
                .body("pojos[1].customerSatisfied", is("false"))
                .body("pojos[1].customerName", is("John Doe"))
                .body("pojos[1].customerBirthday", is("01 NOVEMBER 2001"))
                .body("pojos[1].summary", not(empty()))
                // Assert values of the third extracted POJO
                .body("pojos[2].customerSatisfied", is("true"))
                .body("pojos[2].customerName", is("Kate Boss"))
                .body("pojos[2].customerBirthday", is("13 AUGUST 1999"))
                .body("pojos[2].summary", not(empty()));
    }

}
