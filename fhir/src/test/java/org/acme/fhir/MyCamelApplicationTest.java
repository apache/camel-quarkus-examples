package org.acme.fhir;

import java.io.File;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.fhir.services.FhirService;
import org.apache.camel.test.infra.fhir.services.FhirServiceFactory;
import org.apache.commons.io.FileUtils;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@QuarkusTest
public class MyCamelApplicationTest {

    @RegisterExtension
    public static FhirService service = FhirServiceFactory.createService();

    @Inject
    CamelContext camelContext;

    @Inject
    ProducerTemplate producerTemplate;

    @Test
    public void shouldPushConvertedHl7toFhir() throws Exception {
        MockEndpoint mock = camelContext.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);

        // Copy HL7 test data into the test input directory
        FileUtils.copyDirectory(new File("src/main/data"), new File("target/work/fhir/testinput"));

        // Wait for the file to be processed
        Thread.sleep(TimeUnit.SECONDS.toMillis(5));

        // Trigger the route manually
        producerTemplate.sendBody("direct:start", null);

        // Check if the result is as expected
        mock.assertIsSatisfied();

        Assertions.assertEquals("Freeman",
                mock.getExchanges().get(0).getIn().getBody(Patient.class).getName().get(0).getFamily());
    }
}
