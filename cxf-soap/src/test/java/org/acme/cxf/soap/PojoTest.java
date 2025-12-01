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

import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.namespace.QName;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.xml.ws.Service;
import org.acme.cxf.soap.pojo.service.Address;
import org.acme.cxf.soap.pojo.service.Contact;
import org.acme.cxf.soap.pojo.service.ContactService;
import org.acme.cxf.soap.pojo.service.ContactType;
import org.acme.cxf.soap.pojo.service.NoSuchContactException;
import org.acme.cxf.soap.utils.CxfServerUtils;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PojoTest {

    protected static Contact createConsumerContact() {
        Contact contact = new Contact();
        contact.setName("Croway");
        contact.setType(ContactType.OTHER);
        Address address = new Address();
        address.setCity("Rome");
        address.setStreet("Test Street");
        contact.setAddress(address);

        return contact;
    }

    protected static Contact createProducerContact() {
        Contact contact = new Contact();
        contact.setName("Lukas");
        contact.setType(ContactType.OTHER);
        Address address = new Address();
        address.setCity("Czech Republic");
        address.setStreet("Random Street");
        contact.setAddress(address);

        return contact;
    }

    protected ContactService createCXFClient() {
        try {
            final URL serviceUrl = new URL(CxfServerUtils.getServerUrl() + "/contact?wsdl");
            final QName qName = new QName(ContactService.TARGET_NS, ContactService.class.getSimpleName());
            final Service service = Service.create(serviceUrl, qName);
            return service.getPort(ContactService.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testPojoCamelConsumer() throws NoSuchContactException {
        ContactService cxfClient = createCXFClient();
        int countContactsStart = cxfClient.getContacts().getContacts().size();
        cxfClient.addContact(createConsumerContact());
        Assertions.assertSame(countContactsStart + 1, cxfClient.getContacts().getContacts().size(),
                "We should have one contact added.");

        Assertions.assertNotNull(cxfClient.getContact("Croway"), "We haven't found contact.");

        Assertions.assertThrows(NoSuchContactException.class, () -> cxfClient.getContact("Non existent"));
    }

    @Test
    public void testPojoCamelProducer() throws NoSuchContactException {
        ContactService cxfClient = createCXFClient();
        int countContactsStart = cxfClient.getContacts().getContacts().size();

        RestAssured.given()
                .log().all()
                .contentType("application/json")
                .body(createProducerContact())
                .queryParam(CxfConstants.OPERATION_NAME, "addContact")
                .post("/producer/contact")
                .then()
                .statusCode(200);

        RestAssured.given()
                .contentType("application/json")
                .queryParam(CxfConstants.OPERATION_NAME, "getContacts")
                .get("/producer/contacts")
                .then()
                .statusCode(200)
                .body("contacts.size()", Matchers.equalTo(countContactsStart + 1));

        Assertions.assertNotNull(cxfClient.getContact("Lukas"), "We haven't found contact.");
    }
}
