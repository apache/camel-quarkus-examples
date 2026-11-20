/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.partner.gateway.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One audit record. It never holds request bodies or tokens, but {@code actor} (user names) and {@code remoteAddr}
 * (IP addresses) are personal data.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    private static final int MAX_LENGTH = 255;

    @Id
    private long seq;
    private Instant ts;
    private String actor;
    @Column(name = "actor_type")
    @Enumerated(EnumType.STRING)
    private ActorType actorType;
    @Column(name = "partner_id")
    private String partnerId;
    private String action;
    private String resource;
    @Enumerated(EnumType.STRING)
    private Outcome outcome;
    private String reason;
    @Column(name = "exchange_id")
    private String exchangeId;
    @Column(name = "remote_addr")
    private String remoteAddr;
    @Column(name = "prev_hash")
    private String prevHash;
    private String hash;

    protected AuditEvent() {
    }

    public AuditEvent(long seq, Instant ts, Caller caller, String action, String resource, Outcome outcome,
            String reason) {
        this.seq = seq;
        this.ts = ts;
        this.actor = truncate(caller.name());
        this.actorType = caller.type();
        this.partnerId = caller.partnerId();
        this.action = action;
        this.resource = truncate(resource);
        this.outcome = outcome;
        this.reason = truncate(reason);
        this.exchangeId = caller.exchangeId();
        this.remoteAddr = caller.remoteAddr();
    }

    void link(String prevHash, String hash) {
        this.prevHash = prevHash;
        this.hash = hash;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }

    public long getSeq() {
        return seq;
    }

    public Instant getTs() {
        return ts;
    }

    public String getActor() {
        return actor;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public String getAction() {
        return action;
    }

    public String getResource() {
        return resource;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }
}
