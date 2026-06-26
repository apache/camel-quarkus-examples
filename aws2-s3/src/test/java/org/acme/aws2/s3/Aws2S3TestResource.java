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
package org.acme.aws2.s3;

import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

public class Aws2S3TestResource implements QuarkusTestResourceLifecycleManager {
    private static final Logger LOG = LoggerFactory.getLogger(Aws2S3TestResource.class);
    private static final int FLOCI_PORT = 4566;
    private static final String ACCESS_KEY = "testAccessKeyId";
    private static final String SECRET_KEY = "testSecretKeyId";
    private static final String REGION = "us-east-1";

    private GenericContainer<?> floci;

    @Override
    public Map<String, String> start() {

        Config config = ConfigProvider.getConfig();
        Optional<String> accessKey = config.getOptionalValue("camel.component.aws2-s3.accessKey", String.class);
        Optional<String> secretKey = config.getOptionalValue("camel.component.aws2-s3.secretKey", String.class);
        Optional<String> region = config.getOptionalValue("camel.component.aws2-s3.region", String.class);
        Optional<String> bucketName = config.getOptionalValue("cq.aws2-s3.example.bucketName", String.class);

        final boolean realCredentialsProvided = accessKey.isPresent() && secretKey.isPresent() && region.isPresent()
                && bucketName.isPresent();

        if (realCredentialsProvided) {
            LOG.info("Real backend will be used");
            return Collections.emptyMap();
        }
        LOG.info("Mock backend will be used");

        DockerImageName imageName = DockerImageName
                .parse(config.getValue("floci.container.image", String.class));
        floci = new GenericContainer<>(imageName)
                .withExposedPorts(FLOCI_PORT)
                .waitingFor(Wait.forHttp("/_floci/health").forPort(FLOCI_PORT))
                .withEnv("AWS_ACCESS_KEY_ID", ACCESS_KEY)
                .withEnv("AWS_SECRET_ACCESS_KEY", SECRET_KEY)
                .withLogConsumer(new Slf4jLogConsumer(LOG));
        floci.start();

        String endpoint = String.format("http://%s:%d", floci.getHost(), floci.getMappedPort(FLOCI_PORT));

        return Map.of("camel.component.aws2-s3.accessKey", ACCESS_KEY,
                "camel.component.aws2-s3.secretKey", SECRET_KEY,
                "camel.component.aws2-s3.region", REGION,
                "camel.component.aws2-s3.override-endpoint", "true",
                "camel.component.aws2-s3.uri-endpoint-override", endpoint,
                "camel.component.aws2-s3.force-path-style", "true",
                "cq.aws2-s3.example.bucketName", createBucket(endpoint));
    }

    private String createBucket(String endpoint) {
        S3ClientBuilder clientBuilder = S3Client.builder();
        clientBuilder
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .endpointOverride(URI.create(endpoint))
                .forcePathStyle(true)
                .region(Region.of(REGION));
        final S3Client s3Client = clientBuilder.build();

        final String bucketName = "camel-quarkus-" + RandomStringUtils.secure().nextAlphanumeric(49).toLowerCase(Locale.ROOT);
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        return bucketName;
    }

    @Override
    public void stop() {
        if (floci != null) {
            floci.stop();
        }
    }
}
