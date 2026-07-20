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
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CyberarkVaultRoutes extends RouteBuilder {

    @ConfigProperty(name = "conjur.url")
    String url;
    @ConfigProperty(name = "conjur.account")
    String account;
    @ConfigProperty(name = "conjur.writer.username")
    String writerUsername;
    @ConfigProperty(name = "conjur.writer.apiKey")
    String writerApiKey;
    @ConfigProperty(name = "conjur.reader.username")
    String readerUsername;
    @ConfigProperty(name = "conjur.reader.apiKey")
    String readerApiKey;

    @Override
    public void configure() throws Exception {

        from("direct:createSecret")
                .toF("cyberark-vault:secret?operation=createSecret&secretId=BotApp/secretVar&url=%s&account=%s&username=%s&apiKey=%s",
                        url, account, writerUsername, writerApiKey)
                .log("Secret created/updated");

        from("direct:getSecret")
                .toF("cyberark-vault:secret?secretId=BotApp/secretVar&url=%s&account=%s&username=%s&apiKey=%s",
                        url, account, readerUsername, readerApiKey)
                .log("Retrieved secret: ${body}");

        from("direct:propertyPlaceholder")
                .process(exchange -> {
                    PropertiesComponent component = exchange.getContext().getPropertiesComponent();
                    component.resolveProperty("cyberark:BotApp/secretVar").ifPresent(value -> {
                        exchange.getMessage().setBody(value);
                    });
                });

        from("timer:readSecret?period=5000")
                .autoStartup("{{timer.enabled:true}}")
                .doTry()
                .process(exchange -> {
                    PropertiesComponent component = exchange.getContext().getPropertiesComponent();
                    component.resolveProperty("cyberark:BotApp/secretVar").ifPresent(value -> {
                        exchange.getMessage().setBody(value);
                    });
                })
                .log("Property placeholder cyberark:BotApp/secretVar resolved to: ${body}")
                .doCatch(Exception.class)
                .log("No secret stored yet. Create one with: curl -X POST http://localhost:8080/cyberark-vault/createSecret -d 'my-secret'")
                .end();
    }
}
