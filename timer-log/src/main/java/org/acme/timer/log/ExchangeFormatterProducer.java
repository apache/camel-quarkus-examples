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
package org.acme.timer.log;

import jakarta.enterprise.inject.Produces;
import org.apache.camel.Exchange;
import org.apache.camel.component.log.LogComponent;
import org.apache.camel.spi.ExchangeFormatter;

public class ExchangeFormatterProducer {

    /**
     * Enum of ANSI escape codes for console colors.
     */
    enum LogColor {
        BLUE("\u001b[34m"),
        YELLOW("\u001b[33m");

        private String escapeCode;

        LogColor(String escapeCode) {
            this.escapeCode = escapeCode;
        }

        public String apply(String string) {
            return this.escapeCode + string + "\u001b[0m";
        }
    }

    /**
     * Custom {@link ExchangeFormatter} bean to apply different colors to messages depending on which log endpoint they are
     * sent to.
     *
     * This bean is automatically wired into the {@link LogComponent} on startup.
     */
    @Produces
    public ExchangeFormatter exchangeFormatter() {
        return new ExchangeFormatter() {
            @Override
            public String format(Exchange exchange) {
                String toEndpoint = exchange.getProperty(Exchange.TO_ENDPOINT, String.class);
                String body = exchange.getMessage().getBody(String.class);
                switch (toEndpoint) {
                case "log://timer":
                    return LogColor.BLUE.apply("Java DSL: " + body);
                case "log://timer-xml":
                    return LogColor.YELLOW.apply("XML DSL: " + body);
                default:
                    return body;
                }
            }
        };
    }
}
