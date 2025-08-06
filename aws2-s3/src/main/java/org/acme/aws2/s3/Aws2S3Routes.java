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
package org.acme.aws2.s3;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Constants;

public class Aws2S3Routes extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("file:src/main/toUpload?noop=true")
                .id("file-consumer-route")
                .process(exchange -> {
                    exchange.getIn().setHeader(AWS2S3Constants.KEY, "camel.txt");
                })
                .to("aws2-s3://{{cq.aws2-s3.example.bucketName}}")
                .log("Uploaded file with content '${body}' to s3 bucket {{cq.aws2-s3.example.bucketName}}");

        from("aws2-s3://{{cq.aws2-s3.example.bucketName}}")
                .id("producer-route")
                .log("Received body: ${body}");
    }
}
