package io.quarkus.camel;

import io.quarkus.amazon.lambda.test.LambdaClient;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;

@QuarkusTest
public class AWSLambdaHandlerTest {

    @Inject
    GreetService greetService;

    @BeforeAll
    public static void setup() {
        GreetService mock = Mockito.mock(GreetService.class);
        Mockito.when(mock.greet("Stu")).thenReturn("Hello Stu ! How are you? from GreetService");
        QuarkusMock.installMockForType(mock, GreetService.class);
    }

    @Test
    public void testSimpleLambdaSuccess() throws Exception {
        Person in = new Person();
        in.setName("Stu");
        String out = LambdaClient.invoke(String.class, in);
        Assertions.assertEquals("Hello Stu ! How are you? from GreetService", out);
    }
}