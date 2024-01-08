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

CREATE TABLE CAMEL_MESSAGEPROCESSED (
    id BIGINT NOT NULL,
    createdAt TIMESTAMP(6),
    messageId VARCHAR(255),
    processorName VARCHAR(255),
    PRIMARY KEY (id)
);

CREATE SEQUENCE CAMEL_MESSAGEPROCESSED_SEQ START WITH 1 INCREMENT BY 50;

ALTER TABLE CAMEL_MESSAGEPROCESSED ADD CONSTRAINT message_processed_key_constraint UNIQUE (processorName, messageId);
