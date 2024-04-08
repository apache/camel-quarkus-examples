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
package org.acme.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Singleton;

@Singleton
@RegisterForReflection
public class SessionManager {
    private final Map<String, String> SESSIONS = new ConcurrentHashMap<>();

    public void startSession(String userName, String connectionKey) {
        SESSIONS.put(userName.toLowerCase(), connectionKey);
    }

    public void endSession(String userName) {
        SESSIONS.remove(userName.toLowerCase());
    }

    public String getConnectionKey(String userName) {
        return SESSIONS.get(userName.toLowerCase());
    }

    public boolean isSessionExists(String userName) {
        return SESSIONS.containsKey(userName.toLowerCase());
    }

    public String[] getAllConnectedUsers() {
        String[] connectedUsers = new String[SESSIONS.size()];
        int index = 0;
        for (String userName : SESSIONS.keySet()) {
            connectedUsers[index++] = userName;
        }
        return connectedUsers;
    }
}
