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

import io.quarkus.amazon.lambda.test.LambdaClient;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;

@QuarkusTest
public class AWSLambdaHandlerTest {

    @Inject
    GreetService greetService;

    @BeforeAll
    public static void setup() {
        GreetService mock = Mockito.mock(GreetService.class);
        Mockito.when(mock.greet("Stu")).thenReturn("Hello Stu ! How are you? from GreetService");
        QuarkusMock.installMockForType(mock, GreetService.class);
    }

    @Test
    public void testSimpleLambdaSuccess() throws Exception {
        Person in = new Person();
        in.setName("Stu");
        String out = LambdaClient.invoke(String.class, in);
        Assertions.assertEquals("Hello Stu ! How are you? from GreetService", out);
    }
}