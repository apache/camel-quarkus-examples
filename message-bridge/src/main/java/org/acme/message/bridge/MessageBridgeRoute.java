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
package org.acme.message.bridge;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionManager;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class MessageBridgeRoute extends RouteBuilder {
    @Inject
    TransactionManager transactionManager;

    @Override
    public void configure() throws Exception {
        rest()
                .post("/message")
                .id("rest")
                .to("direct:publish");

        from("direct:publish")
                .id("ibmmq")
                .transacted()
                .log("Sending message to IBMMQ: ${body}")
                .to("ibmmq:queue:{{ibm.mq.queue}}?disableReplyTo=true");

        from("ibmmq:queue:{{ibm.mq.queue}}")
                .id("ibmmq-amq")
                .transacted()
                .process(ex -> {
                    // Enlist our custom XAResource
                    // if the body contains "crash", this resource will kill the JVM during transaction commit
                    // this resource is then used to recover the message after the crash
                    DummyXAResource xaResource = new DummyXAResource(ex.getIn().getBody(String.class).contains("crash"));
                    transactionManager.getTransaction().enlistResource(xaResource);
                })
                .choice()
                .when(simple("${header.JMSRedelivered}"))
                .log("Redelivering message after rollback to ActiveMQ: ${body}")
                .otherwise()
                .log("Sending message from IBMMQ to ActiveMQ: ${body}")
                .end()
                .to("amq:queue:{{amq.queue}}")
                .process(ex -> {
                    if (ex.getIn().getBody(String.class).toLowerCase().contains("rollback")) {
                        // To simulate the rollback just once, we examine the value of the JMSRedelivered flag in the message
                        // if the value is "false", we initiate the rollback
                        // if the value is "true", it indicates that the rollback has already occurred,
                        // so we allow the message to proceed through the route successfully
                        if (!ex.getIn().getHeader("JMSRedelivered", Boolean.class)) {
                            // Simulate rollback
                            throw new RuntimeException("Simulated rollback");
                        }
                    }
                });

        from("amq:queue:{{amq.queue}}")
                .id("amq")
                .log("ActiveMQ received: ${body}");
    }
}
