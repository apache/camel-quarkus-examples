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
package org.acme.cxf.soap.pojo;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.acme.cxf.soap.pojo.service.Contact;
import org.acme.cxf.soap.pojo.service.ContactService;
import org.acme.cxf.soap.pojo.service.Contacts;
import org.acme.cxf.soap.utils.CxfServerUtils;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;

/**
 * This class demonstrate how to use camel-quarkus-cxf-soap client
 */
@ApplicationScoped
public class PojoCxfProducerRouteBuilder extends RouteBuilder {

    @Produces
    @ApplicationScoped
    @Named
    CxfEndpoint soapClientEndpointPojo() {
        final CxfEndpoint result = new CxfEndpoint();
        result.setDataFormat(DataFormat.POJO);
        result.setServiceClass(ContactService.class);
        result.setAddress("%s/contact".formatted(CxfServerUtils.getServerUrl()));
        return result;
    }

    @Override
    public void configure() throws Exception {
        restConfiguration()
                .bindingMode(RestBindingMode.json);

        rest("/producer/contact").post()
                .type(Contact.class)
                .to("direct:contact");
        rest("/producer/contacts").get()
                .bindingMode(RestBindingMode.off)
                .produces("application/json")
                .to("direct:contacts");

        from("direct:contact")
                .to("cxf:bean:soapClientEndpointPojo");

        from("direct:contacts")
                .to("cxf:bean:soapClientEndpointPojo")
                .convertBodyTo(Contacts.class)
                .marshal().json(JsonLibrary.Jackson);
    }
}
