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
package org.acme.extraction;

import java.time.LocalDate;
import java.util.Locale;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.apache.camel.jsonpath.JsonPath;

@RegisterAiService
@ApplicationScoped
public interface CustomPojoExtractionService {

    @RegisterForReflection
    static class CustomPojo {
        public boolean customerSatisfied;
        public String customerName;
        public LocalDate customerBirthday;
        public String summary;

        private final static String FORMAT = "\n{\n"
                + "\t\"customerSatisfied\": \"%s\",\n"
                + "\t\"customerName\": \"%s\",\n"
                + "\t\"customerBirthday\": \"%td %tB %tY\",\n"
                + "\t\"summary\": \"%s\"\n"
                + "}\n";

        public String toString() {
            return String.format(Locale.US, FORMAT, this.customerSatisfied, this.customerName, this.customerBirthday,
                    this.customerBirthday, this.customerBirthday, this.summary);
        }
    }

    static final String CUSTOM_POJO_EXTRACT_PROMPT = "Extract information about a customer from the text delimited by triple backticks: ```{text}```."
            + "The customerBirthday field should be formatted as {dateFormat}."
            + "The summary field should concisely relate the customer main ask.";

    /**
     * The text parameter of this method is automatically injected as {text} in the CUSTOM_POJO_EXTRACT_PROMPT. This is
     * made possible as the code is compiled with -parameters argument in the maven-compiler-plugin related section of
     * the pom.xml file. Without -parameters, one would need to use the @V annotation like in the method signature
     * proposed below: extractFromText(@dev.langchain4j.service.V("text") String text);
     *
     * Notice how Camel maps the incoming exchange to the method parameters with annotations like @JsonPath and @Header.
     * More information on the Camel bean parameter binding feature could be found here:
     * https://camel.apache.org/manual/bean-binding.html#_parameter_binding
     */
    @UserMessage(CUSTOM_POJO_EXTRACT_PROMPT)
    @Handler
    CustomPojo extractFromText(@JsonPath("$.content") String text, @Header("expectedDateFormat") String dateFormat);
}
