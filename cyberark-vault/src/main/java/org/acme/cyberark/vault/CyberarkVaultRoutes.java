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
package org.acme.cyberark.vault;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.PropertiesComponent;

@ApplicationScoped
public class CyberarkVaultRoutes extends RouteBuilder {
    static final String SECRET_ID = "BotApp/secretVar";

    @Override
    public void configure() throws Exception {

        from("direct:createSecret")
                .toF("cyberark-vault:secret?operation=createSecret&secretId=%s"
                        + "&url={{conjur.url}}&account={{conjur.account}}"
                        + "&username=RAW({{conjur.writer.username}})&apiKey=RAW({{conjur.writer.apiKey}})", SECRET_ID)
                .log("Secret %s created/updated".formatted(SECRET_ID));

        from("direct:getSecret")
                .toF("cyberark-vault:secret?secretId=%s"
                        + "&url={{conjur.url}}&account={{conjur.account}}"
                        + "&username=RAW({{conjur.reader.username}})&apiKey=RAW({{conjur.reader.apiKey}})", SECRET_ID)
                .log("Secret %s retrieved successfully".formatted(SECRET_ID));

        from("direct:propertyPlaceholder")
                .process(exchange -> {
                    PropertiesComponent component = exchange.getContext().getPropertiesComponent();
                    component.resolveProperty("cyberark:" + SECRET_ID).ifPresent(value -> {
                        exchange.getMessage().setBody(value);
                    });
                });

        from("timer:readSecret?period=5000")
                .autoStartup("{{timer.enabled:true}}")
                .doTry()
                .process(exchange -> {
                    PropertiesComponent component = exchange.getContext().getPropertiesComponent();
                    component.resolveProperty("cyberark:" + SECRET_ID).ifPresent(value -> {
                        exchange.getMessage().setBody(value);
                    });
                })
                .log("Property placeholder %s resolved successfully".formatted(SECRET_ID))
                .doCatch(Exception.class)
                .log("No secret stored yet. Create one with: curl -X POST http://localhost:8080/cyberark-vault/createSecret -d 'my-secret'")
                .end();
    }
}
