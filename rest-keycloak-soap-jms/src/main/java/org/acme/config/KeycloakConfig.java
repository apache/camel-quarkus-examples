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
package org.acme.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.acme.inventory.InventoryServicePortType;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.component.keycloak.security.KeycloakSecurityPolicy;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class KeycloakConfig {

    private static final String REALMS_PATH = "/realms/";

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    private String authServerUrl;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    private String clientId;

    @ConfigProperty(name = "quarkus.oidc.credentials.secret", defaultValue = "")
    private String clientSecret;

    @ConfigProperty(name = "soap.inventory.client.address")
    private String soapClientAddress;

    @Produces
    @Named("keycloakPolicy")
    public KeycloakSecurityPolicy keycloakSecurityPolicy() {
        // Validate auth-server-url is provided
        if (authServerUrl == null || authServerUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "quarkus.oidc.auth-server-url is required but was not configured");
        }

        // Parse auth-server-url to extract serverUrl and realm
        // Expected format: http://localhost:8082/realms/amq-demo
        int realmsIndex = authServerUrl.indexOf(REALMS_PATH);
        if (realmsIndex == -1) {
            throw new IllegalArgumentException(
                    "Invalid auth-server-url format. Expected format: <server-url>/realms/<realm-name>, " +
                            "but got: " + authServerUrl);
        }

        String serverUrl = authServerUrl.substring(0, realmsIndex);
        String realm = authServerUrl.substring(realmsIndex + REALMS_PATH.length());

        // Validate realm name is not empty
        if (realm.isEmpty()) {
            throw new IllegalArgumentException(
                    "Realm name cannot be empty in auth-server-url: " + authServerUrl);
        }

        KeycloakSecurityPolicy policy = new KeycloakSecurityPolicy();
        policy.setServerUrl(serverUrl);
        policy.setRealm(realm);
        policy.setClientId(clientId);
        policy.setClientSecret(clientSecret);
        return policy;
    }

    @Produces
    @ApplicationScoped
    @Named("inventoryService")
    public CxfEndpoint inventoryService() {
        // SOAP Server endpoint
        CxfEndpoint inventoryEndpoint = new CxfEndpoint();
        inventoryEndpoint.setWsdlURL("wsdl/InventoryService.wsdl");
        inventoryEndpoint.setServiceClass(InventoryServicePortType.class);
        inventoryEndpoint.setAddress("/inventory");
        return inventoryEndpoint;
    }

    @Produces
    @ApplicationScoped
    @Named("inventoryServiceClient")
    public CxfEndpoint inventoryServiceClient() {
        // SOAP Client endpoint - calls the local SOAP service
        CxfEndpoint clientEndpoint = new CxfEndpoint();
        clientEndpoint.setWsdlURL("wsdl/InventoryService.wsdl");
        clientEndpoint.setServiceClass(InventoryServicePortType.class);
        clientEndpoint.setAddress(soapClientAddress);
        return clientEndpoint;
    }
}
