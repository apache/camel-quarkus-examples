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
package org.acme.cxf.soap.pojo.service.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.acme.cxf.soap.pojo.service.Contact;
import org.acme.cxf.soap.pojo.service.ContactService;
import org.acme.cxf.soap.pojo.service.Contacts;
import org.acme.cxf.soap.pojo.service.NoSuchContactException;

@ApplicationScoped
@Named("inMemoryContactService")
public class ContactServiceInMemoryImpl implements ContactService {

    private Map<String, Contact> contacts = new ConcurrentHashMap<>();

    @Override
    public void addContact(Contact contact) {
        contacts.put(contact.getName(), contact);
    }

    @Override
    public Contact getContact(String name) throws NoSuchContactException {
        if (!contacts.containsKey(name)) {
            throw new NoSuchContactException(name);
        }

        return contacts.get(name);
    }

    @Override
    public Contacts getContacts() {
        return new Contacts(contacts.values());
    }

    @Override
    public void updateContact(String name, Contact contact) throws NoSuchContactException {
        if (!contacts.containsKey(name)) {
            throw new NoSuchContactException(name);
        }
        if (!contacts.get(name).equals(contact.getName())) {
            contacts.remove(name);
        }
        contacts.put(contact.getName(), contact);
    }

    @Override
    public void removeContact(String name) throws NoSuchContactException {
        if (!contacts.containsKey(name)) {
            throw new NoSuchContactException(name);
        }
        contacts.remove(name);
    }
}
