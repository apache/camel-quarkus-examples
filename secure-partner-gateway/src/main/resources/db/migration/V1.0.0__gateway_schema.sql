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

-- Run as the schema owner: tables created by gateway_app would let the application drop the append-only trigger
DO $$ BEGIN
    IF current_user = 'gateway_app' THEN
        RAISE EXCEPTION 'Run Flyway as the schema owner (QUARKUS_FLYWAY_USERNAME), not as gateway_app';
    END IF;
END $$;

-- Orders, one row per (partner, Idempotency-Key). The unique constraint makes retries idempotent.
CREATE TABLE partner_order (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    partner_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    sku VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT partner_order_idempotency_key UNIQUE (partner_id, idempotency_key)
);

-- Hash-chained audit trail
CREATE TABLE audit_event (
    seq BIGINT NOT NULL PRIMARY KEY,
    ts TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    actor VARCHAR(255) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    partner_id VARCHAR(64),
    action VARCHAR(32) NOT NULL,
    resource VARCHAR(255),
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(255),
    exchange_id VARCHAR(64),
    remote_addr VARCHAR(64),
    prev_hash VARCHAR(64) NOT NULL,
    hash VARCHAR(64) NOT NULL
);

-- Single row holding the last seq and hash; appends lock it to keep the chain linear
CREATE TABLE audit_chain_head (
    id INTEGER NOT NULL PRIMARY KEY CHECK (id = 1),
    seq BIGINT NOT NULL,
    hash VARCHAR(64) NOT NULL
);

INSERT INTO audit_chain_head (id, seq, hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');

CREATE FUNCTION audit_event_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only, % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_append_only();

CREATE TRIGGER audit_event_no_truncate
    BEFORE TRUNCATE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_append_only();

-- The application connects as gateway_app, which gets only what it needs. Flyway runs as the owner of the tables,
-- so the application can neither change audit records nor drop or disable the triggers.
GRANT SELECT, INSERT ON partner_order TO gateway_app;
GRANT SELECT, INSERT ON audit_event TO gateway_app;
GRANT SELECT, UPDATE ON audit_chain_head TO gateway_app;
