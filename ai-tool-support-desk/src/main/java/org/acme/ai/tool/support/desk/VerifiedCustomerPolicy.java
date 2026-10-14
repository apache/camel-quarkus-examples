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
package org.acme.ai.tool.support.desk;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.CamelAuthorizationException;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.spi.AuthorizationPolicy;

/**
 * The authorization policy of the customer tools that change something, see {@code authorizationPolicy} in
 * {@link ToolRoutes}: only verified customers may call them.
 * <p>
 * It runs in front of the tool route. A denied call never runs the route: the model learns that it is not authorized.
 * The policy decides on the exchange properties set by
 * {@link ChatResource} from the authenticated user, never on the headers, which hold the arguments chosen by the model.
 */
@ApplicationScoped
@Named("verifiedCustomers")
public class VerifiedCustomerPolicy implements AuthorizationPolicy {

    @Override
    public void beforeWrap(Route route, NamedNode definition) {
    }

    @Override
    public Processor wrap(Route route, Processor processor) {
        return exchange -> {
            if (!exchange.getProperty(ChatResource.CUSTOMER_VERIFIED, false, Boolean.class)) {
                throw new CamelAuthorizationException("Customer "
                        + exchange.getProperty(ChatResource.CUSTOMER_ID, String.class) + " is not verified", exchange);
            }
            processor.process(exchange);
        };
    }
}
