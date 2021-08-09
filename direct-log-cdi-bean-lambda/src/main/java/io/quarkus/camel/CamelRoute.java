package io.quarkus.camel;

import org.apache.camel.builder.RouteBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class CamelRoute extends RouteBuilder {

   @Inject
   GreetService greetService;

    @Override
    public void configure() throws Exception {
        from("direct:input").routeId("Test")
           .log("Inside Camel Route Received Payload ==> ${body}")
           /*
            * If you use the below Bean EIP then it uses reflection to invoke the method at runtime
            * Avoiding Reflection will have some improvement in terms of boot up, execution time and max memory used
            *
            */
           //.bean(greetService,"greet(${body.name})")
           .setBody().body(Person.class, p -> greetService.greet(p.getName()))
        .end();
    }
}