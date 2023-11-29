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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;

public class JdbcRoutes extends RouteBuilder {
    @Override
    public void configure() throws Exception {
        // Define a mapping for the review values
        HashMap<String, Integer> reviewMapping = new HashMap<>();
        reviewMapping.put("best", 1);
        reviewMapping.put("good", 0);
        reviewMapping.put("worst", -1);

        from("timer://insertCamel?delay={{etl.timer.delay}}&period={{etl.timer.period}}&repeatCount={{etl.timer.repeatcount}}")
                .setBody().simple("DELETE FROM Target")
                .to("jdbc:target_db")
                .setBody().simple("SELECT * FROM Source")
                .to("jdbc:source_db")
                .log("Extracting data from source database")
                .split(body())
                .process(exchange -> {
                    Map<String, Object> sourceData = exchange.getIn().getBody(Map.class);
                    String review = (String) sourceData.get("review");
                    int mappedReview = reviewMapping.getOrDefault(review, 0);
                    sourceData.put("review", mappedReview);
                })
                .log("-> Transforming review for hotel '${body[hotel_name]}'")
                .setBody()
                .simple("INSERT INTO Target (id, hotel_name, price, review) VALUES(${body[id]}, '${body[hotel_name]}', ${body[price]}, ${body[review]})")
                .to("jdbc:target_db")
                .log("-> Loading transformed data in target database");
    }
}
