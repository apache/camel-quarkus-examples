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

-- The returns waiting for the approval of the support staff, as a JSON array. They hold no free text written by
-- customers, as it would reach the AI assistant of the support staff: the reason is a code and the items are named after
-- the catalog.
SELECT CAST(COALESCE(json_agg(json_build_object(
           'returnId', r.id,
           'customerId', r.customer_id,
           'orderId', r.order_id,
           'deliveredAt', CAST(o.delivered_at AS DATE),
           'items', r.items,
           'amount', r.amount,
           'reason', r.reason,
           'requestedAt', CAST(r.created_at AS DATE)) ORDER BY r.id), '[]') AS TEXT)
FROM returns r
JOIN orders o ON o.id = r.order_id
WHERE r.status = 'PENDING_APPROVAL'
