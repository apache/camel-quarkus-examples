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

import org.apache.camel.builder.RouteBuilder;

public class Routes extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        from("paho:devices/light?brokerUrl=tcp://localhost:1883")
                .process("indexProcessing")
                .to("elasticsearch://docker-cluster?operation=index");

        //        from("direct:search")
        //                .to("elasticsearch://cheese?operation=Search&indexName=heyyou");
    }

    //exchange.getMessage().setHeader(ElasticsearchConstants.PARAM_INDEX_NAME, new IndexRequest.Builder<String>().index("light"));
    //exchange.getMessage().setHeader(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
    //exchange.getMessage().setBody(new IndexRequest.Builder<String>().document("smth"));

}
