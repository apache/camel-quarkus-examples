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
package org.acme.cxf.soap;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import javax.xml.ws.soap.SOAPFaultException;

import com.example.customerservice.Customer;
import com.example.customerservice.CustomerService;
import com.example.customerservice.NoSuchCustomerException;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
public class WsdlClientTest extends BaseTest {

    CustomerService cxfClient;

    protected CustomerService createCustomerClient() {
        String URL = getServerUrl() + "/cxf/services/customer";

        ClientProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setServiceClass(CustomerService.class);
        factory.setAddress(URL);
        factory.getFeatures().add(new LoggingFeature());
        return (CustomerService) factory.create();
    }

    @BeforeEach
    public void before() {
        cxfClient = createCustomerClient();
    }

    @Test
    public void testGetCustomer() throws Exception {
        List<Customer> customers = cxfClient.getCustomersByName("test");
        assertEquals(customers.get(0).getName(), "test");
        assertEquals(customers.get(0).getCustomerId(), 1);
    }

    @Test
    public void testNonExistentCustomer() throws Exception {
        Assertions.assertThrows(NoSuchCustomerException.class, () -> cxfClient.getCustomersByName("Non existent"));
    }

    @Test
    public void testInvalidRequest() {
        Assertions.assertThrows(SOAPFaultException.class, () -> cxfClient.getCustomersByName("a"));
    }

    @Test
    public void testUpdateCustomer() throws Exception {
        double revenue = 9999;
        LocalDate birthDate = LocalDate.parse("1990-03-13");

        List<Customer> customers = cxfClient.getCustomersByName("test");

        assertNotEquals(customers.get(0).getRevenue(), revenue);
        assertNotEquals(customers.get(0).getBirthDate(), birthDate);

        Customer customer = customers.get(0);
        customer.setRevenue(revenue);
        customer.setBirthDate(birthDate);

        // void method are async by default
        cxfClient.updateCustomer(customer);

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> {
                    List<Customer> updatedCustomers = cxfClient.getCustomersByName("test");

                    return updatedCustomers.get(0).getName().equals("test") &&
                            updatedCustomers.get(0).getCustomerId() == 1 &&
                            updatedCustomers.get(0).getRevenue() == revenue &&
                            updatedCustomers.get(0).getBirthDate().equals(birthDate);
                });
    }
}
