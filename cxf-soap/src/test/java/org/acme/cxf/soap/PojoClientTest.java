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
import jakarta.xml.ws.Service;
import org.acme.cxf.soap.pojo.service.Address;
import org.acme.cxf.soap.pojo.service.Contact;
import org.acme.cxf.soap.pojo.service.ContactService;
import org.acme.cxf.soap.pojo.service.ContactType;
import org.acme.cxf.soap.pojo.service.NoSuchContactException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class PojoClientTest extends BaseTest {

    protected ContactService createCXFClient() {
        try {
            final URL serviceUrl = new URL(getServerUrl() + "/cxf/services/contact?wsdl");
            final QName qName = new QName(ContactService.TARGET_NS, ContactService.class.getSimpleName());
            final Service service = Service.create(serviceUrl, qName);
            return service.getPort(ContactService.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Contact createContact() {
        Contact contact = new Contact();
        contact.setName("Croway");
        contact.setType(ContactType.OTHER);
        Address address = new Address();
        address.setCity("Rome");
        address.setStreet("Test Street");
        contact.setAddress(address);

        return contact;
    }

    @Test
    public void testBasic() throws NoSuchContactException {
        ContactService cxfClient = createCXFClient();

        cxfClient.addContact(createContact());
        Assertions.assertSame(1, cxfClient.getContacts().getContacts().size(), "We should have one contact.");

        Assertions.assertNotNull(cxfClient.getContact("Croway"), "We haven't found contact.");

        Assertions.assertThrows(NoSuchContactException.class, () -> cxfClient.getContact("Non existent"));
    }
}
