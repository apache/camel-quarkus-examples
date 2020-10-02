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
package org.acme.timer;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.apache.camel.quarkus.main.CamelMainApplication;

@QuarkusMain
public class Main {

    private static String greeting;

    public static void main(String... args) {

        /* Any custom logic can be implemented here
         * Here, we pass the value of the first argument to Quarkus CDI container so that it can be injected using
         * @Inject @Named("greeting")
         * And we pass the second argument as -durationMaxMessages which is the number of messages that the application
         * will process before terminating */
        if (args.length < 2) {
            throw new IllegalStateException("Expected at least two CLI arguments");
        }
        int i = 0;
        List<String> filteredArgs = new ArrayList<>(args.length);
        greeting = args[i++];
        final String repeatTimes = args[i++];
        filteredArgs.add("-durationMaxMessages");
        filteredArgs.add(repeatTimes);

        for (; i < args.length; i++) {
            filteredArgs.add(args[i++]);
        }

        Quarkus.run(CamelMainApplication.class, filteredArgs.toArray(new String[filteredArgs.size()]));
    }

    @ApplicationScoped
    public static class GreetingProducer {
        @Produces
        @Named("greeting")
        public String greeting() {
            return greeting;
        }
    }
}
