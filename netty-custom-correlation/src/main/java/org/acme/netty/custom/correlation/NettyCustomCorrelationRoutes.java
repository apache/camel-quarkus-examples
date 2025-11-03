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
package org.acme.netty.custom.correlation;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.builder.RouteBuilder;

@RegisterForReflection(targets = { ExchangeTimedOutException.class })
public class NettyCustomCorrelationRoutes extends RouteBuilder {

    private int counter;

    public int increment() {
        return ++counter;
    }

    @Override
    public void configure() throws Exception {

        // lets build a special custom error message for timeout
        onException(ExchangeTimedOutException.class)
                // here we tell Camel to continue routing
                .continued(true)
                // after it has built this special timeout error message body
                .setBody(simple("#${header.corId}-Time out error!!!"));

        //netty server, responds with echo, after 5 seconds
        from("netty:tcp://localhost:4444?sync=true&encoders=#myEncoder&decoders=#myDecoder")
                .id("server")
                // use 5s delay  and make the delay asynchronous
                .delay(simple("5000")).asyncDelayed().end()
                .transform(simple("${body}-Echo"));

        //client with correlation
        from("timer:trigger")
                .id("client")
                // set correlation id as unique incrementing number
                .setHeader("corId", method(this, "increment"))
                // save exchange id
                .process(e -> {
                    e.getIn().setHeader("requestExchangeId", e.getExchangeId());
                })
                // build request message as a string body
                .setBody(simple("#${header.corId}:${header.requestExchangeId}"))
                //log sending details
                .log("Client request: ${body}")
                // call netty server using a single shared connection and using custom correlation manager
                // to ensure we can correctly map the request and response pairs
                .to("netty:tcp://localhost:4444?sync=true&encoders=#myEncoder&decoders=#myDecoder"
                        + "&producerPoolEnabled=false&correlationManager=#myCorrelationManager")
                // detect whether response is received with the same exchange as sent request
                .process(e -> {
                    if (e.getIn().getBody(String.class).contains(e.getExchangeId())) {
                        e.getIn().setHeader("correct", "true");
                    } else {
                        e.getIn().setHeader("correct", "false");
                    }
                })
                .choice().when(header("correct").isEqualTo(simple("true")))
                .log("Server response: #${header.corId} (correct reply): ${exchangeId}")
                .otherwise()
                .log("Server response: #${header.corId} (wrong reply): ${exchangeId}")
                .end();

    }
}
