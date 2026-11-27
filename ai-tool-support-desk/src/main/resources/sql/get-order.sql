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

-- The order as JSON, or no row when the order does not exist or belongs to another customer
SELECT CAST(json_build_object(
           'orderId', o.id,
           'status', o.status,
           'orderedAt', CAST(o.ordered_at AS DATE),
           'deliveredAt', CAST(o.delivered_at AS DATE),
           'items', (SELECT json_agg(json_build_object('sku', i.sku, 'name', i.name, 'quantity', i.quantity,
                                                       'unitPrice', i.price))
                     FROM order_items i
                     WHERE i.order_id = o.id)) AS TEXT)
FROM orders o
WHERE o.id = CAST(:#orderId AS INTEGER)
  AND o.customer_id = :#${exchangeProperty.customerId}
