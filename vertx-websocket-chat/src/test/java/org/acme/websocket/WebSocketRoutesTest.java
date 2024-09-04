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
package org.acme.websocket;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class WebSocketRoutesTest {
    @TestHTTPResource("/chat/bob")
    URI userBob;

    @TestHTTPResource("/chat/amy")
    URI userAmy;

    @Test
    void chatTest() throws InterruptedException {
        CountDownLatch connectLatch = new CountDownLatch(2);
        CountDownLatch messageLatch = new CountDownLatch(2);

        Vertx vertx = Vertx.vertx();
        WebSocketClient client = vertx.createWebSocketClient();
        try {
            AtomicReference<WebSocket> bobWebSocketAtomicReference = new AtomicReference<>();
            client.connect(userBob.getPort(), userBob.getHost(), userBob.getPath()).onSuccess(webSocket -> {
                bobWebSocketAtomicReference.set(webSocket);
                connectLatch.countDown();
            });

            AtomicReference<WebSocket> amyWebSocketAtomicReference = new AtomicReference<>();
            client.connect(userAmy.getPort(), userAmy.getHost(), userAmy.getPath()).onSuccess(webSocket -> {
                amyWebSocketAtomicReference.set(webSocket);
                connectLatch.countDown();
            });

            assertTrue(connectLatch.await(5, TimeUnit.SECONDS));

            WebSocket bobWebSocket = bobWebSocketAtomicReference.get();
            bobWebSocket.handler(message -> {
                if (message.toString().toLowerCase().contains("hi bob")) {
                    messageLatch.countDown();
                }
            });

            WebSocket amyWebSocket = amyWebSocketAtomicReference.get();
            amyWebSocket.handler(message -> {
                if (message.toString().toLowerCase().contains("hi amy")) {
                    messageLatch.countDown();
                }
            });

            bobWebSocket.write(Buffer.buffer("Hi Amy"));
            amyWebSocket.write(Buffer.buffer("Hi Bob"));

            assertTrue(messageLatch.await(5, TimeUnit.SECONDS));
        } finally {
            if (client != null) {
                client.close();
            }

            if (vertx != null) {
                vertx.close();
            }
        }
    }
}
