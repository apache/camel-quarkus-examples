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
package io.quarkus.camel;

import org.apache.camel.builder.RouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class CamelRoute extends RouteBuilder {

   @Inject
   GreetService greetService;

    @Override
    public void configure() throws Exception {
        from("direct:input").routeId("Test")
           .log("Inside Camel Route Received Payload ==> ${body}")
           /*
            * If you use the below Bean EIP then it uses reflection to invoke the bean method at runtime.
            * Avoiding Reflection will have some improvement in terms of boot up, execution time and max memory used
            *
            */
           //.bean(greetService,"greet(${body.name})")
           .setBody().body(Person.class, p -> greetService.greet(p.getName()))
        .end();
    }
}