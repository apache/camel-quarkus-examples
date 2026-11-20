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

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single row holding the seq and hash of the last audit event. Appends lock it, which keeps the chain linear.
 */
@Entity
@Table(name = "audit_chain_head")
public class AuditChainHead {

    static final int ID = 1;

    @Id
    private int id;
    private long seq;
    private String hash;

    public long getSeq() {
        return seq;
    }

    public String getHash() {
        return hash;
    }

    void advance(AuditEvent event) {
        this.seq = event.getSeq();
        this.hash = event.getHash();
    }
}
