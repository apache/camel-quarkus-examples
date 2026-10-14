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

-- The demo data of dev and test mode, which Dev Services load after db/schema.sql. Dates are relative to now, so that
-- the 30 day return window always applies to the same orders.

INSERT INTO customers (id, name, email) VALUES
    ('alice', 'Alice Smith', 'alice@example.com'),
    ('bob', 'Bob Jones', 'bob@example.com'),
    ('dave', 'Dave Miller', 'dave@example.com');

INSERT INTO orders (id, customer_id, status, ordered_at, delivered_at, tracking_number) VALUES
    (1001, 'alice', 'DELIVERED', now() - INTERVAL '8 days', now() - INTERVAL '5 days', 'ACME-7741'),
    (1002, 'alice', 'DELIVERED', now() - INTERVAL '14 days', now() - INTERVAL '10 days', 'ACME-7702'),
    (1003, 'alice', 'SHIPPED', now() - INTERVAL '2 days', NULL, 'ACME-7790'),
    (1004, 'alice', 'DELIVERED', now() - INTERVAL '65 days', now() - INTERVAL '60 days', 'ACME-6120'),
    (2001, 'bob', 'DELIVERED', now() - INTERVAL '6 days', now() - INTERVAL '3 days', 'ACME-7755'),
    (2002, 'bob', 'DELIVERED', now() - INTERVAL '9 days', now() - INTERVAL '7 days', 'ACME-7731'),
    (2003, 'bob', 'DELIVERED', now() - INTERVAL '4 days', now() - INTERVAL '2 days', 'ACME-7768'),
    (2004, 'bob', 'DELIVERED', now() - INTERVAL '6 days', now() - INTERVAL '4 days', 'ACME-7772'),
    (3001, 'dave', 'DELIVERED', now() - INTERVAL '5 days', now() - INTERVAL '3 days', 'ACME-7783');

INSERT INTO order_items (order_id, sku, name, quantity, price) VALUES
    (1001, 'CABLE-USBC', 'USB-C cable, 2 m', 2, 12.50),
    (1002, 'SPEAKER-S1', 'Smart speaker S1', 1, 129.00),
    (1003, 'MOUSE-M5', 'Wireless mouse M5', 1, 39.90),
    (1004, 'LAMP-L2', 'Desk lamp L2', 1, 45.00),
    (2001, 'HEADPHONES-H2', 'Headphones H2', 1, 89.00),
    (2002, 'KEYBOARD-K3', 'Keyboard K3', 1, 59.00),
    (2003, 'CHARGER-C1', 'USB charger C1', 1, 24.90),
    (2004, 'WEBCAM-W1', 'Webcam W1', 1, 50.50),
    (3001, 'CASE-P1', 'Phone case P1', 1, 15.00);

-- Two returns waiting for a decision of the support staff
INSERT INTO returns (order_id, customer_id, items, amount, reason, status) VALUES
    (2001, 'bob', '[{"sku": "HEADPHONES-H2", "name": "Headphones H2", "quantity": 1}]', 89.00, 'DEFECTIVE',
     'PENDING_APPROVAL'),
    (2002, 'bob', '[{"sku": "KEYBOARD-K3", "name": "Keyboard K3", "quantity": 1}]', 59.00, 'DEFECTIVE',
     'PENDING_APPROVAL');
