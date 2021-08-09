package io.quarkus.camel;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.camel.ProducerTemplate;

import javax.inject.Inject;
import javax.inject.Named;

@Named("awsLambdaHandler")
public class AWSLambdaHandler implements RequestHandler<Person, String> {

    @Inject
    ProducerTemplate template;

    @Override
    public String handleRequest(Person input, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Calling Camel Route :)");
        return template.requestBody("direct:input", input, String.class);
    }
}
