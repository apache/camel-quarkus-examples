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
package org.acme.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v24.message.ORU_R01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.http.ProtocolException;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

@RegisterForReflection(targets = { Patient.class, CapabilityStatement.class, MethodOutcome.class, Resource.class,
        HumanName.class })
@ApplicationScoped
public class Routes extends RouteBuilder {
    @Inject
    FhirContext fhirContext;

    @Override
    public void configure() {
        onException(ProtocolException.class)
                .handled(true)
                .log(LoggingLevel.ERROR,
                        "Error connecting to FHIR server with URL:{{camel.component.fhir.server-url}}, please check the application.properties file ${exception.message}")
                .end();

        onException(HL7Exception.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Error unmarshalling HL7 data ${exception.message}")
                .end();

        onException(ResourceNotFoundException.class)
                .handled(true)
                .choice().when(simple("${header.id} != null"))
                .setBody(simple("Patient with id ${header.id} not found"))
                .otherwise()
                .setBody(simple("FHIR Server ${{camel.component.fhir.server-url}} not found"))
                .end()
                .setHeader(Exchange.HTTP_RESPONSE_CODE).constant(404);

        // Simple REST API to create / read patient data
        rest("/api/patients")
                .post()
                .to("direct:patientUpload")

                .get("/{id}")
                .to("direct:getPatient");

        // Processes uploaded patient details
        from("direct:patientUpload")
                // unmarshall file to hl7 message
                .unmarshal().hl7()
                .process(exchange -> {
                    ORU_R01 msg = exchange.getIn().getBody(ORU_R01.class);
                    final PID pid = msg.getPATIENT_RESULT().getPATIENT().getPID();
                    String surname = pid.getPatientName()[0].getFamilyName().getFn1_Surname().getValue();
                    String name = pid.getPatientName()[0].getGivenName().getValue();
                    String patientId = msg.getPATIENT_RESULT().getPATIENT().getPID().getPatientID().getCx1_ID().getValue();
                    Patient patient = new Patient();
                    patient.addName().addGiven(name);
                    patient.getNameFirstRep().setFamily(surname);
                    patient.setId(patientId);
                    exchange.getIn().setBody(patient);
                })
                // marshall to JSON for logging
                .marshal().fhirJson("{{camel.component.fhir.fhir-version}}")
                .convertBodyTo(String.class)
                .log("Inserting Patient: ${body}")
                // create Patient in our FHIR server
                .to("fhir://create/resource?inBody=resourceAsString")
                // log the outcome
                .log("Patient created successfully: ${body}")
                // Return a JSON response to the client
                .process(exchange -> {
                    Message message = exchange.getMessage();
                    MethodOutcome methodOutcome = message.getBody(MethodOutcome.class);
                    IParser parser = fhirContext.newJsonParser();
                    String response = parser.encodeResourceToString(methodOutcome.getResource());
                    message.setBody(response);
                    message.setHeader(Exchange.CONTENT_TYPE, "application/json");
                });

        // Retrieves a patient
        from("direct:getPatient")
                .process(exchange -> {
                    String patientId = exchange.getMessage().getHeader("id", String.class);
                    IdType iIdType = new IdType(patientId);
                    exchange.getMessage().setHeader("CamelFhir.id", iIdType);
                })
                .to("fhir://read/resourceById?resourceClass=Patient")
                .process(exchange -> {
                    Message message = exchange.getMessage();
                    Patient patient = message.getBody(Patient.class);
                    IParser parser = fhirContext.newJsonParser();
                    String response = parser.encodeResourceToString(patient);
                    message.setBody(response);
                    message.setHeader(Exchange.CONTENT_TYPE, "application/json");
                });
    }
}
