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

import java.time.Month;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public interface CustomPojoExtractionService {

    @RegisterForReflection(registerFullHierarchy = true)
    class CustomPojo {
        @JsonProperty(required = true)
        public boolean customerSatisfied;
        @JsonProperty(required = true)
        public String customerName;
        @JsonProperty(required = true)
        public Birthday customerBirthday;
        @JsonProperty(required = true)
        public String summary;

        private final static String FORMAT = "\n{\n"
                + "\t\"customerSatisfied\": \"%s\",\n"
                + "\t\"customerName\": \"%s\",\n"
                + "\t\"customerBirthday\": \"%02d %s %04d\",\n"
                + "\t\"summary\": \"%s\"\n"
                + "}\n";

        public String toString() {
            return String.format(Locale.US, FORMAT, this.customerSatisfied, this.customerName, this.customerBirthday.day,
                    Month.of(this.customerBirthday.month), this.customerBirthday.year, this.summary);
        }
    }

    class Birthday {
        @JsonProperty(required = true)
        public int year;
        @JsonProperty(required = true)
        public short month;
        @JsonProperty(required = true)
        public short day;
    }

    String CUSTOM_POJO_EXTRACT_PROMPT = "Extract information about a customer from the transcript delimited by triple backticks: ```{{text}}```."
            + "The customerName field should be formatted as FIRSTNAME LASTNAME, for instance Isaac Newton."
            + "The summary field should concisely relate the customer main ask."
            + "Source any extracted field values from what is explicitly mentioned in the transcript."
            + "Extracted field values should be as accurate as possible.";

    /**
     * The text parameter of this method is automatically injected as {{text}} in the CUSTOM_POJO_EXTRACT_PROMPT.
     */
    @UserMessage(CUSTOM_POJO_EXTRACT_PROMPT)
    CustomPojo extractFromText(@V("text") String text);
}
