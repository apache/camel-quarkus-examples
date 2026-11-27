--
-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--      http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- The tables of the support desk. Create them in your PostgreSQL. In dev and test mode, Dev Services run this script,
-- then db/demo-data.sql.

CREATE TABLE customers (
    -- the user name the customer logs in with
    id    VARCHAR(50) PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL
);

CREATE TABLE orders (
    id              INTEGER PRIMARY KEY,
    customer_id     VARCHAR(50) NOT NULL REFERENCES customers (id),
    -- PROCESSING, SHIPPED or DELIVERED
    status          VARCHAR(20) NOT NULL,
    ordered_at      TIMESTAMP NOT NULL,
    delivered_at    TIMESTAMP,
    tracking_number VARCHAR(50)
);

CREATE TABLE order_items (
    order_id INTEGER NOT NULL REFERENCES orders (id),
    sku      VARCHAR(50) NOT NULL,
    name     VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL,
    -- unit price in EUR
    price    NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (order_id, sku)
);

CREATE TABLE returns (
    id          SERIAL PRIMARY KEY,
    -- one return per order: a repeated request_return call cannot create a second refund
    order_id    INTEGER NOT NULL UNIQUE REFERENCES orders (id),
    customer_id VARCHAR(50) NOT NULL REFERENCES customers (id),
    -- the returned items: SKU, name in the catalog and quantity
    items       JSON NOT NULL,
    amount      NUMERIC(10, 2) NOT NULL,
    -- a code rather than free text, so that no text written by a customer reaches the AI assistant of the support staff
    reason      VARCHAR(20) NOT NULL
                CHECK (reason IN ('DEFECTIVE', 'DAMAGED', 'WRONG_ITEM', 'NOT_AS_EXPECTED', 'NO_LONGER_NEEDED')),
    -- APPROVED right away, or PENDING_APPROVAL until the support staff decide: APPROVED or REJECTED
    status      VARCHAR(20) NOT NULL,
    note        VARCHAR(500),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
    id          SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL REFERENCES customers (id),
    summary     VARCHAR(1000) NOT NULL,
    priority    VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    -- OPEN, for the support team to pick up
    status      VARCHAR(10) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
