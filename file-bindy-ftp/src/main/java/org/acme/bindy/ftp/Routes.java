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
package org.acme.bindy.ftp;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;

public class Routes extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // Generate some book objects with random data
        from("timer:generateBooks?period={{timer.period}}&delay={{timer.delay}}")
                .log("Generating randomized books CSV data")
                .process("bookGenerator")
                // Marshal each book to CSV format
                .marshal().bindy(BindyType.Csv, Book.class)
                // Write CSV data to file
                .to("file:{{csv.location}}");

        // Consume book CSV files
        from("file:{{csv.location}}?delay=1000")
                .log("Reading books CSV data from ${header.CamelFileName}")
                .unmarshal().bindy(BindyType.Csv, Book.class)
                .split(body())
                .to("direct:aggregateBooks");

        // Aggregate books based on their genre
        from("direct:aggregateBooks")
                .setHeader("BookGenre", simple("${body.genre}"))
                .aggregate(simple("${body.genre}"), new GroupedBodyAggregationStrategy()).completionInterval(5000)
                .log("Processed ${header.CamelAggregatedSize} books for genre '${header.BookGenre}'")
                .to("seda:processed");

        from("seda:processed")
                // Marshal books back to CSV format
                .marshal().bindy(BindyType.Csv, Book.class)
                .setHeader(Exchange.FILE_NAME, simple("books-${header.BookGenre}-${exchangeId}.csv"))
                // Send aggregated book genre CSV files to an FTP host
                .to("sftp://{{ftp.username}}@{{ftp.host}}:{{ftp.port}}/uploads/books?password={{ftp.password}}")
                .log("Uploaded ${header.CamelFileName}");
    }
}
