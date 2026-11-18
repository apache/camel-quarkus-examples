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
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Append-only, hash-chained audit trail. Every append locks the chain head, so appends are serialized.
 */
@ApplicationScoped
@Startup
public class AuditLog {

    private static final Logger LOG = Logger.getLogger(AuditLog.class);
    private static final int MIN_KEY_LENGTH = 32;
    // Random keys have far more, placeholders such as 32 times "a" or "changeme" repeated have fewer
    private static final int MIN_KEY_DISTINCT_CHARACTERS = 8;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    @Inject
    EntityManager em;

    @Inject
    MeterRegistry registry;

    // No default outside dev and test: the application does not start without AUDIT_HMAC_KEY
    @ConfigProperty(name = "audit.hmac-key")
    String hmacKey;

    private AuditChain chain;

    @PostConstruct
    void init() {
        if (hmacKey.length() < MIN_KEY_LENGTH || hmacKey.chars().distinct().count() < MIN_KEY_DISTINCT_CHARACTERS) {
            throw new IllegalStateException("audit.hmac-key must be a random key of at least " + MIN_KEY_LENGTH
                    + " characters, for example from openssl rand -base64 32");
        }
        chain = new AuditChain(hmacKey);
    }

    /**
     * Joins the caller's transaction if there is one, so that business changes and their audit record commit or roll
     * back together.
     */
    @Transactional
    public void append(Caller caller, String action, String resource, Outcome outcome, String reason) {
        AuditChainHead head = em.find(AuditChainHead.class, AuditChainHead.ID, LockModeType.PESSIMISTIC_WRITE);
        AuditEvent event = new AuditEvent(head.getSeq() + 1, Instant.now().truncatedTo(ChronoUnit.MILLIS), caller,
                action, resource, outcome, reason);
        chain.link(event, head.getHash());
        em.persist(event);
        head.advance(event);
        registry.counter("audit.events", "action", action, "outcome", outcome.name()).increment();
    }

    /**
     * Latest records first, optionally filtered by actor and action.
     */
    @Transactional
    public List<AuditEvent> query(Caller caller, String actor, String action, Integer limit) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM AuditEvent e WHERE 1 = 1");
        if (actor != null) {
            jpql.append(" AND e.actor = :actor");
        }
        if (action != null) {
            jpql.append(" AND e.action = :action");
        }
        TypedQuery<AuditEvent> query = em.createQuery(jpql.append(" ORDER BY e.seq DESC").toString(), AuditEvent.class)
                .setMaxResults(limit != null ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT);
        if (actor != null) {
            query.setParameter("actor", actor);
        }
        if (action != null) {
            query.setParameter("action", action);
        }
        List<AuditEvent> events = query.getResultList();
        append(caller, "AUDIT_QUERIED", "audit", Outcome.SUCCESS, null);
        return events;
    }

    /**
     * Recomputes the whole chain. The head lock keeps appends out while the chain is read.
     */
    @Transactional
    public Verification verify(Caller caller) {
        AuditChainHead head = em.find(AuditChainHead.class, AuditChainHead.ID, LockModeType.PESSIMISTIC_WRITE);
        List<AuditEvent> events = em.createQuery("SELECT e FROM AuditEvent e ORDER BY e.seq", AuditEvent.class)
                .getResultList();
        Verification verification = chain.verify(events, head.getSeq(), head.getHash());
        if (!verification.valid()) {
            LOG.errorf("Audit chain is broken at seq %d", verification.brokenAtSeq());
        }
        append(caller, "AUDIT_VERIFIED", "audit", Outcome.SUCCESS,
                verification.valid() ? "valid" : "broken at seq " + verification.brokenAtSeq());
        return verification;
    }

    /**
     * The last seq and hash, to be copied to a system the database administrators cannot change.
     */
    @Transactional
    public String head() {
        AuditChainHead head = em.find(AuditChainHead.class, AuditChainHead.ID);
        return "seq=" + head.getSeq() + " hash=" + head.getHash();
    }
}
