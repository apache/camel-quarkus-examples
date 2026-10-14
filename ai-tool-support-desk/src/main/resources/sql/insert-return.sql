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

-- Creates the return and gives back its id, or no row when the order already has a return. The reason is one of the
-- codes allowed by the returns table.
INSERT INTO returns (order_id, customer_id, items, amount, reason, status)
VALUES (CAST(:#orderId AS INTEGER), :#${exchangeProperty.customerId}, CAST(:#${exchangeProperty.returnItems} AS JSON),
        :#${exchangeProperty.amount}, :#${header.reason}, :#${exchangeProperty.status})
ON CONFLICT (order_id) DO NOTHING
RETURNING id
