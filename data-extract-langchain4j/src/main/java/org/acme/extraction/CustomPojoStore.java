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
package org.acme.extraction;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.extraction.CustomPojoExtractionService.CustomPojo;
import org.apache.camel.Handler;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CustomPojoStore {

    private static final Logger LOG = Logger.getLogger(CustomPojoStore.class);

    private List<CustomPojo> pojos = new CopyOnWriteArrayList<>();

    @Handler
    CustomPojo addPojo(CustomPojo pojo) {
        LOG.info("An extracted POJO has been added to the store: " + pojo);
        pojos.add(pojo);
        return pojo;
    }

    String asString() {
        StringBuilder sb = new StringBuilder("{ \"pojos\": [");
        String pojoString = pojos.stream().map(CustomPojo::toString).collect(Collectors.joining(","));
        sb.append(pojoString);
        sb.append("] }");
        return sb.toString();
    }

}
