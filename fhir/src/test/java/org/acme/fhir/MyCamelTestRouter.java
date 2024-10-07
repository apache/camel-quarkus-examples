package org.acme.fhir;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;

@ApplicationScoped
public class MyCamelTestRouter extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        from("direct:start")
                .to("fhir://read/resourceById?resourceClass=Patient&stringId=1&serverUrl={{serverUrl}}&fhirVersion={{fhirVersion}}")
                .to("mock:result");
    }
}
