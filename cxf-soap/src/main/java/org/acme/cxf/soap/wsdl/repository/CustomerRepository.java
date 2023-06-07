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
package org.acme.cxf.soap.wsdl.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.datatype.DatatypeConfigurationException;

import com.example.customerservice.Customer;
import com.example.customerservice.CustomerType;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CustomerRepository {
    List<Customer> customers = new ArrayList<>();

    @PostConstruct
    private void init() throws DatatypeConfigurationException {
        populateCustomers();
    }

    public List<Customer> getCustomersByName(String name) {
        return getCustomersStreamByName(name)
                .collect(Collectors.toList());
    }

    private Stream<Customer> getCustomersStreamByName(String name) {
        return customers.stream().filter(c -> c.getName().equals(name));
    }

    public void updateCustomer(Customer customer) {
        getCustomersStreamByName(customer.getName())
                .forEach(storedCustomer -> {
                    storedCustomer.setRevenue(customer.getRevenue());
                    storedCustomer.setCustomerId(customer.getCustomerId());
                    storedCustomer.setNumOrders(customer.getNumOrders());
                    storedCustomer.setType(customer.getType());
                    storedCustomer.setTest(customer.getTest());
                    storedCustomer.setBirthDate(customer.getBirthDate());
                });
    }

    private void populateCustomers() throws DatatypeConfigurationException {
        Customer a = new Customer();
        a.setCustomerId(1);
        a.setName("test");
        a.setType(CustomerType.PRIVATE);
        a.setNumOrders(1);
        a.setBirthDate(LocalDate.now());

        customers.add(a);
    }
}
