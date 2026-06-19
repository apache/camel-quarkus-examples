#!/usr/bin/env bash
#
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
#

# Reads a Scalpel report and emits GitHub Actions matrix + modules outputs.
# Usage: generate-test-matrix.sh <scalpel-report.json>
# Output lines are in key=value format suitable for >> $GITHUB_OUTPUT.

set -euo pipefail

REPORT="${1:?Usage: $0 <scalpel-report.json>}"
MAX_GROUPS=10

# Unique top-level modules (first path component of each affected module path)
ALL=()
if [ -f "$REPORT" ]; then
    while IFS= read -r line; do
        ALL+=("$line")
    done < <(jq -r '[.affectedModules[] | select(.path != "") | .path | split("/")[0]] | unique[]' "$REPORT")
fi

# Fall back to all reactor modules when Scalpel found nothing or wrote no report
# (no base branch configured, e.g. a plain push with no PR context)
if [ ${#ALL[@]} -eq 0 ]; then
    while IFS= read -r line; do
        ALL+=("$line")
    done < <(grep '<module>' pom.xml | sed 's|.*<module>\(.*\)</module>.*|\1|' | sort)
fi

# Separate single-module and multi-module projects.
# Multi-module projects get a dedicated native test group (they run from their own
# root directory) and are expanded with sub-modules for the -pl modules list.
SINGLE=()
MULTI=()
for m in "${ALL[@]}"; do
    if grep -q '<module>' "$m/pom.xml" 2>/dev/null; then
        MULTI+=("$m")
    else
        SINGLE+=("$m")
    fi
done

# Round-robin distribution of single-module projects into at most MAX_GROUPS groups
GROUP_TESTS=()
for i in "${!SINGLE[@]}"; do
    gid=$((i % MAX_GROUPS))
    if [[ -z "${GROUP_TESTS[$gid]+x}" ]]; then
        GROUP_TESTS[$gid]="${SINGLE[$i]}"
    else
        GROUP_TESTS[$gid]="${GROUP_TESTS[$gid]},${SINGLE[$i]}"
    fi
done

# Build matrix JSON include array
INCLUDE="["
SEP=""
for gid in $(echo "${!GROUP_TESTS[@]}" | tr ' ' '\n' | sort -n); do
    INCLUDE+="${SEP}$(printf '{"name":"group-%02d","tests":"%s"}' $((gid + 1)) "${GROUP_TESTS[$gid]}")"
    SEP=","
done
# Multi-module projects each get their own dedicated group at the end
NUM_GROUPS=${#GROUP_TESTS[@]}
for i in "${!MULTI[@]}"; do
    INCLUDE+="${SEP}$(printf '{"name":"group-%02d","tests":"%s"}' $((NUM_GROUPS + i + 1)) "${MULTI[$i]}")"
    SEP=","
done
INCLUDE+="]"

# Build expanded module list for -pl: multi-module projects include their sub-modules
# so that Maven builds the full project tree rather than just the parent POM.
EXPANDED=()
for m in "${ALL[@]}"; do
    EXPANDED+=("$m")
    if grep -q '<module>' "$m/pom.xml" 2>/dev/null; then
        while IFS= read -r submod; do
            [ -n "$submod" ] && EXPANDED+=("$m/$submod")
        done < <(grep '<module>' "$m/pom.xml" | sed 's|.*<module>\(.*\)</module>.*|\1|')
    fi
done

echo "matrix={\"include\":${INCLUDE}}"
echo "modules=$(IFS=,; echo "${EXPANDED[*]}")"
