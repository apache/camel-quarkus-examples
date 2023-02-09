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
package org.acme;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.transaction.xa.XAResource;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.tm.XAResourceRecovery;
import org.jboss.tm.XAResourceRecoveryRegistry;

/**
 * This class is used solely for simulating system crash.
 *
 */
@Startup
public class DummyXAResourceRecovery implements XAResourceRecovery {
    private Logger LOG = Logger.getLogger(DummyXAResourceRecovery.class);

    @Inject
    XAResourceRecoveryRegistry xaResourceRecoveryRegistry;

    @PostConstruct
    void init() {
        LOG.info("register DummyXAResourceRecovery");
        xaResourceRecoveryRegistry.addXAResourceRecovery(this);
    }

    @Override
    public XAResource[] getXAResources() throws RuntimeException {
        List<DummyXAResource> resources = Collections.emptyList();
        try {
            resources = getXAResourcesFromDirectory(DummyXAResource.LOG_DIR);
        } catch (IOException e) {
        }

        if (!resources.isEmpty()) {
            LOG.info(DummyXAResourceRecovery.class.getSimpleName() + " returning list of resources: " + resources);
        }

        return resources.toArray(new XAResource[] {});
    }

    private List<DummyXAResource> getXAResourcesFromDirectory(String directory) throws IOException {
        List<DummyXAResource> resources = new ArrayList<>();

        Files.newDirectoryStream(FileSystems.getDefault().getPath(directory), "*_").forEach(path -> {
            try {
                resources.add(new DummyXAResource(path.toFile()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        return resources;
    }

}
