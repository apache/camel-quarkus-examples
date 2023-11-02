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
package org.acme.jpa.idempotent.repository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.apache.camel.Handler;
import org.jboss.logging.Logger;

@ApplicationScoped
@Named("costlyApiService")
public class CostlyApiService {

    private static final Logger LOG = Logger.getLogger(CostlyApiService.class);

    private static Set<String> ALREADY_USED_CONTENT = ConcurrentHashMap.newKeySet();

    /**
     * The content parameter is populated with the incoming HTTP body sent to this API.
     */
    @Handler
    void invoke(String content) {
        if (ALREADY_USED_CONTENT.contains(content)) {
            LOG.info("Costly API has been called two times with the same content => TOO MUCH EXPENSIVE !");
        } else {
            ALREADY_USED_CONTENT.add(content);
            LOG.info("Costly API has been called with new content => GOOD");
        }
    }

    String getContentSet() {
        return String.join(",", ALREADY_USED_CONTENT);
    }
}
