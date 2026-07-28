#!/bin/bash
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

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONJUR_ACCOUNT="myConjurAccount"
CONJUR_PORT="${CONJUR_PORT:-9080}"
COMPOSE_ARGS="-f $SCRIPT_DIR/docker-compose.yml -p cyberark-vault"

# --- stop mode ---
if [[ "${1:-}" == "stop" ]]; then
    docker compose $COMPOSE_ARGS down -v
    echo "Conjur environment stopped."
    exit 0
fi

# --- prerequisites ---
for cmd in docker curl jq; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "Error: '$cmd' is required but not installed." >&2
        exit 1
    fi
done

# --- clean start (wipe previous volumes so account/policy are fresh) ---
docker compose $COMPOSE_ARGS down -v 2>/dev/null || true

echo "Starting Conjur environment..."
CONJUR_PORT="$CONJUR_PORT" docker compose $COMPOSE_ARGS up -d

# --- wait for Conjur to accept connections ---
echo "Waiting for Conjur to be ready..."
for i in $(seq 1 60); do
    if curl -so /dev/null "http://localhost:$CONJUR_PORT/" 2>/dev/null; then
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "Error: Conjur did not become ready within 120 s." >&2
        exit 1
    fi
    sleep 2
done
echo "Conjur is ready."

# --- create account ---
echo "Creating account '$CONJUR_ACCOUNT'..."
ACCOUNT_OUTPUT=$(docker compose $COMPOSE_ARGS exec -T conjur \
    conjurctl account create "$CONJUR_ACCOUNT" 2>&1)
ADMIN_KEY=$(echo "$ACCOUNT_OUTPUT" | tr -s '[:space:]' '\n' | tail -1)

if [ -z "$ADMIN_KEY" ]; then
    echo "Error: failed to extract admin API key." >&2
    echo "$ACCOUNT_OUTPUT" >&2
    exit 1
fi

# --- authenticate as admin ---
TOKEN=$(curl -sf -X POST \
    "http://localhost:$CONJUR_PORT/authn/$CONJUR_ACCOUNT/admin/authenticate" \
    -d "$ADMIN_KEY" | base64 | tr -d '\n')

# --- load policy ---
echo "Loading BotApp policy..."
POLICY_FILE="$SCRIPT_DIR/src/test/resources/conf/policy/BotApp.yml"
POLICY_RESPONSE=$(curl -sf -X PUT \
    "http://localhost:$CONJUR_PORT/policies/$CONJUR_ACCOUNT/policy/root" \
    -H "Authorization: Token token=\"$TOKEN\"" \
    -H "Content-Type: application/x-yaml" \
    --data-binary @"$POLICY_FILE")

READ_API_KEY=$(echo "$POLICY_RESPONSE" | jq -r \
    ".created_roles[\"$CONJUR_ACCOUNT:host:BotApp/myDemoApp\"].api_key")
WRITE_API_KEY=$(echo "$POLICY_RESPONSE" | jq -r \
    ".created_roles[\"$CONJUR_ACCOUNT:user:Dave@BotApp\"].api_key")

if [ "$READ_API_KEY" = "null" ] || [ "$WRITE_API_KEY" = "null" ]; then
    echo "Error: failed to extract API keys from policy response." >&2
    echo "$POLICY_RESPONSE" >&2
    exit 1
fi

# --- output ---
cat <<EOF

Conjur environment is ready!

Run these commands, then start the example:

  export CQ_CONJUR_URL=http://localhost:$CONJUR_PORT
  export CQ_CONJUR_ACCOUNT=$CONJUR_ACCOUNT
  export CQ_CONJUR_READ_USER=host/BotApp/myDemoApp
  export CQ_CONJUR_READ_USER_API_KEY=$READ_API_KEY
  export CQ_CONJUR_READ_WRITE_USER=user/Dave@BotApp
  export CQ_CONJUR_READ_WRITE_USER_API_KEY=$WRITE_API_KEY

  mvn clean compile quarkus:dev -f $SCRIPT_DIR/pom.xml

To stop:  $0 stop
EOF
