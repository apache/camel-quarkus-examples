#!/usr/bin/env bash

# Iterates over all available examples and calls mvnd with the specified arguments for each of them

set -x
set -e

pwd="$(pwd)"
for moduleDir in $(ls -d */)
do
    if [ -f "${pwd}/${moduleDir}/pom.xml" ]; then
        cd "${pwd}/${moduleDir}"
        ../mvnw "$@"
    fi
done

cd "${pwd}"
./mvnw org.l2x6.cq:cq-maven-plugin:0.23.1:update-example-pages
