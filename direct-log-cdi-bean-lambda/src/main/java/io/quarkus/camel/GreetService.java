package io.quarkus.camel;

import javax.enterprise.context.ApplicationScoped;


//@RegisterForReflection is required if you want to lookup this bean via Bean component / EIP in your camel route
// and want to deploy your app as Native executable.
@ApplicationScoped
public class GreetService {

    public String greet(String name){
        return String.format("Hello %s ! How are you? from GreetService",name);
    }
}
