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
package org.acme.cxf.soap.wsdl;

import java.util.HashMap;
import java.util.List;

import com.example.customerservice.Customer;
import com.example.customerservice.CustomerService;
import com.example.customerservice.NoSuchCustomer;
import com.example.customerservice.NoSuchCustomerException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.acme.cxf.soap.wsdl.repository.CustomerRepository;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.cxf.message.MessageContentsList;

/**
 * This class demonstrate how to expose a SOAP endpoint starting from a wsdl, using the
 * quarkus-maven-plugin:generate-code
 */
@ApplicationScoped
public class MyWsdlRouteBuilder extends RouteBuilder {

    private final CustomerRepository customerRepository;

    public MyWsdlRouteBuilder(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Produces
    @ApplicationScoped
    @Named
    CxfEndpoint customer() {
        CxfEndpoint customersEndpoint = new CxfEndpoint();
        customersEndpoint.setWsdlURL("wsdl/CustomerService.wsdl");
        customersEndpoint.setServiceClass(CustomerService.class);
        customersEndpoint.setAddress("/customer");
        customersEndpoint.setProperties(new HashMap<>());
        // Request validation will be executed, in particular the name validation in getCustomersByName
        customersEndpoint.getProperties().put("schema-validation-enabled", "true");

        return customersEndpoint;
    }

    @Override
    public void configure() throws Exception {
        // CustomerService is generated with quarkus-maven-plugin:generate-code during the build
        from("cxf:bean:customer")
                .recipientList(simple("direct:${header.operationName}"));

        from("direct:getCustomersByName").process(exchange -> {
            String name = exchange.getIn().getBody(String.class);

            MessageContentsList resultList = new MessageContentsList();
            List<Customer> customersByName = customerRepository.getCustomersByName(name);

            if (customersByName.isEmpty()) {
                NoSuchCustomer noSuchCustomer = new NoSuchCustomer();
                noSuchCustomer.setCustomerName(name);

                throw new NoSuchCustomerException("Customer not found", noSuchCustomer);
            }

            resultList.add(customersByName);
            exchange.getMessage().setBody(resultList);
        });

        from("direct:updateCustomer")
                .process(exchange -> customerRepository.updateCustomer(exchange.getIn().getBody(Customer.class)));
    }
}
