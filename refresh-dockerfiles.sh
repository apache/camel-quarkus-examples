#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script refreshes the Dockerfiles in the examples by generating a temporary project
# using the Quarkus Maven plugin and copies the generated Dockerfiles.

set -e

PROJECT_ROOT=$(pwd)

# Get Quarkus version properties from a reference example project pom.xml
QUARKUS_MAVEN_PLUGIN_VERSION=$("$PROJECT_ROOT/mvnw" -f timer-log/pom.xml help:evaluate -Dexpression=quarkus.platform.version -q -DforceStdout)
QUARKUS_MAVEN_PLUGIN_GROUP_ID=$("$PROJECT_ROOT/mvnw" -f timer-log/pom.xml help:evaluate -Dexpression=quarkus.platform.group-id -q -DforceStdout)
QUARKUS_MAVEN_PLUGIN_ARTIFACT_ID="quarkus-maven-plugin"

if [ -z "$QUARKUS_MAVEN_PLUGIN_VERSION" ] || [ -z "$QUARKUS_MAVEN_PLUGIN_GROUP_ID" ] || [ -z "$QUARKUS_MAVEN_PLUGIN_ARTIFACT_ID" ]; then
    echo "Error: Could not extract all Quarkus platform properties from timer-log/pom.xml"
    exit 1
fi

echo "Detected Quarkus Maven Plugin Group ID: $QUARKUS_MAVEN_PLUGIN_GROUP_ID"
echo "Detected Quarkus Maven Plugin Artifact ID: $QUARKUS_MAVEN_PLUGIN_ARTIFACT_ID"
echo "Detected Quarkus Maven Plugin Version: $QUARKUS_MAVEN_PLUGIN_VERSION"

TEMP_DIR=$(mktemp -d)
echo "Created temporary directory: $TEMP_DIR"

cd "$TEMP_DIR"

echo "Generating temporary Quarkus project with Quarkus Maven Plugin..."
"$PROJECT_ROOT/mvnw" "${QUARKUS_MAVEN_PLUGIN_GROUP_ID}:${QUARKUS_MAVEN_PLUGIN_ARTIFACT_ID}:${QUARKUS_MAVEN_PLUGIN_VERSION}:create" \
    -DprojectGroupId=org.acme \
    -DprojectArtifactId=temp-quarkus-project \
    -DnoCode=true \
    -Dextensions="resteasy-reactive"

if [ $? -ne 0 ]; then
    echo "Error: Quarkus project generation failed."
    rm -rf "$TEMP_DIR"
    exit 1
fi

GENERATED_DOCKER_DIR="$TEMP_DIR/temp-quarkus-project/src/main/docker"

if [ ! -d "$GENERATED_DOCKER_DIR" ]; then
    echo "Error: Generated Docker directory not found at $GENERATED_DOCKER_DIR"
    rm -rf "$TEMP_DIR"
    exit 1
fi

cd -

echo "Updating Dockerfiles in example projects..."

for EXAMPLE_DIR in */; do
    if [ -d "$EXAMPLE_DIR" ] && [ "$EXAMPLE_DIR" != "docs/" ] && [ "$EXAMPLE_DIR" != ".git/" ] && [ "$EXAMPLE_DIR" != ".github/" ] && [ "$EXAMPLE_DIR" != ".idea/" ] && [ "$EXAMPLE_DIR" != ".mvn/" ]; then
        TARGET_DOCKER_DIR="$EXAMPLE_DIR/src/main/docker"
        if [ -d "$TARGET_DOCKER_DIR" ]; then
            echo "  - Updating Dockerfiles in $EXAMPLE_DIR"
            rm -rf "$TARGET_DOCKER_DIR"/*
            cp -r "$GENERATED_DOCKER_DIR"/* "$TARGET_DOCKER_DIR"/

            PROJECT_NAME=$(basename "$EXAMPLE_DIR")
            for DOCKERFILE in "$TARGET_DOCKER_DIR"/*; do
                if [ -f "$DOCKERFILE" ]; then
                    echo "    - Replacing 'temp-quarkus-project' with '$PROJECT_NAME' in $DOCKERFILE"
                    sed -i "" "s/temp-quarkus-project/$PROJECT_NAME/g" "$DOCKERFILE"
                fi
            done
            echo "    - Running mvn license:format in $EXAMPLE_DIR"
            (cd "$EXAMPLE_DIR" && "$PROJECT_ROOT/mvnw" license:format)
        else
            echo "  - Skipping $EXAMPLE_DIR: No src/main/docker directory found."
        fi
    fi
done

echo "Cleaning up temporary directory: $TEMP_DIR"
rm -rf "$TEMP_DIR"

echo "Dockerfile refresh complete."