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
package org.acme.spring.redis;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.redis.RedisConstants;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SpringRedisRoutes extends RouteBuilder {

    @ConfigProperty(name = "camel.quarkus.spring-redis.host")
    String host;

    @ConfigProperty(name = "camel.quarkus.spring-redis.port")
    int port;

    @Override
    public void configure() throws Exception {
        // set a value into Redis
        from("timer:msg?repeatCount=1")
                .setHeader(RedisConstants.COMMAND).constant("SET")
                .setHeader(RedisConstants.KEY).constant("myKey")
                .setHeader(RedisConstants.VALUE).constant("Hello Redis!")
                .toF("spring-redis://%s:%d?redisTemplate=#redisTemplate", host, port)
                .log("Uploaded message into redis with key 'myKey'");

        //get value from redis
        from("timer:msg?period=2s")
                .setHeader(RedisConstants.COMMAND).constant("GET")
                .setHeader(RedisConstants.KEY).constant("myKey")
                .toF("spring-redis://%s:%d?redisTemplate=#redisTemplate", host, port)
                .log("Received value: ${body}");

        //publish a message
        from("timer:topic?repeatCount=1")
                .setHeader(RedisConstants.COMMAND).constant("PUBLISH")
                .setHeader(RedisConstants.CHANNEL).constant("myTopic")
                .setHeader(RedisConstants.MESSAGE).constant("Hello Redis from the topic!")
                .toF("spring-redis://%s:%d?redisTemplate=#redisTemplate", host, port)
                .log("Sent message to topic myTopic");

        //subscribe and receive messages
        fromF("spring-redis://%s:%d?redisTemplate=#redisTemplate&channels=myTopic&command=SUBSCRIBE&connectionFactory=#connectionFactory",
                host, port)
                .log("Received message: ${body}");

    }
}
