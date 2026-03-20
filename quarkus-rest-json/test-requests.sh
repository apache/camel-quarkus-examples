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


# Test script for the Camel-Quarkus REST Integration Example


if ! jq --version &> /dev/null; then
    echo "Error: jq is not installed. Please install it first." >&2
    exit 1
fi

# rest of your script...
BASE_URL="http://localhost:8080"

echo "========================================="
echo "Camel-Quarkus REST Integration Tests"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test 1: Create order with GOLD customer (10% discount)
echo -e "${BLUE}2. Creating order for GOLD customer (CUST001)...${NC}"
curl -s -X POST "${BASE_URL}/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST001",
    "items": [
      {
        "productId": "PROD001",
        "productName": "Widget A",
        "quantity": 1,
        "price": 50
      },
      {
        "productId": "PROD002",
        "productName": "Widget B",
        "quantity": 2,
        "price": 25
      }
    ]
  }' | jq .
echo -e "\n"

# Test 2: PLATINUM customer (15% discount)
echo -e "${BLUE}3. Creating order for PLATINUM customer (CUST004)...${NC}"
curl -s -X POST "${BASE_URL}/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST004",
    "items": [
      {
        "productId": "PROD001",
        "productName": "Widget A",
        "quantity": 1,
        "price": 50
      },
      {
        "productId": "PROD002",
        "productName": "Widget B",
        "quantity": 2,
        "price": 25
      }
    ]
  }' | jq .
echo -e "\n"

# Test 3: STANDARD customer (no discount)
echo -e "${BLUE}4. Creating order for STANDARD customer (CUST999)...${NC}"
curl -s -X POST "${BASE_URL}/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST999",
    "items": [
      {
        "productId": "PROD001",
        "productName": "Widget A",
        "quantity": 1,
        "price": 50
      },
      {
        "productId": "PROD002",
        "productName": "Widget B",
        "quantity": 2,
        "price": 25
      }
    ]
  }' | jq .
echo -e "\n"

echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}All tests completed!${NC}"
echo -e "${GREEN}=========================================${NC}"
