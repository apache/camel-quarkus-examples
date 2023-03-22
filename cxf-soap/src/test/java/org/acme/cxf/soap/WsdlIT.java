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
package org.acme.cxf.soap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
public class WsdlIT extends WsdlTest {

    @Test
    public void wsdlCustomers() throws IOException, SAXException, ParserConfigurationException, TransformerException {
        assertNative("customer");
    }

    @Test
    public void wsdlContact() throws IOException, ParserConfigurationException, SAXException, TransformerException {
        assertNative("contact");
    }

    private void assertNative(String service)
            throws IOException, SAXException, ParserConfigurationException, TransformerException {
        String servedWsdl = getAndSaveServed(service);

        /*
         * We have to compare on DOM level so that different order of XML attributes, etc. does not make the test
         * fail
         */
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setCoalescing(true);
        dbf.setIgnoringElementContentWhitespace(true);
        dbf.setIgnoringComments(true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Path servedJvmPath = Paths.get("target/WsdlTest/" + service + "-served-jvm.wsdl")
                .toAbsolutePath();
        final Document jvmDoc = parse(db, new String(Files.readAllBytes(servedJvmPath), StandardCharsets.UTF_8));
        final Document nativeDoc = parse(db, servedWsdl);

        boolean equal = jvmDoc.isEqualNode(nativeDoc);
        Path nomalizedJvmPath = Paths.get("target/WsdlTest/" + service + "-served-normalized-jvm.wsdl")
                .toAbsolutePath();
        Path nativeNormalizedPath = Paths.get("target/WsdlTest/" + service + "-served-normalized-native.wsdl")
                .toAbsolutePath();
        save(nativeDoc, nomalizedJvmPath);
        save(jvmDoc, nativeNormalizedPath);
        if (!equal) {
            Assertions.fail(
                    "The WSDL documents served in JVM and native are not equal. You may want to compare "
                            + nativeNormalizedPath + " vs. " + nomalizedJvmPath);
        }
    }
}
