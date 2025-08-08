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
package org.acme.netty.custom.correlation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import org.acme.netty.custom.correlation.impl.MyCodecDecoder;
import org.acme.netty.custom.correlation.impl.MyCodecEncoder;
import org.acme.netty.custom.correlation.impl.MyCorrelationManager;
import org.apache.camel.LoggingLevel;

@ApplicationScoped
public class NettyCustomCorrelationProducers {

    @Named("myDecoder")
    MyCodecDecoder produceDecoder() {
        return new MyCodecDecoder();
    }

    @Named("myEncoder")
    MyCodecEncoder produceEncoder() {
        return new MyCodecEncoder();
    }

    @Named("myCorrelationManager")
    MyCorrelationManager produceCorrelationManager() {
        MyCorrelationManager manager = new MyCorrelationManager();

        // set timeout for each request message that did not receive a reply message
        manager.setTimeout(6000);
        // set the logging level when a timeout was hit, ny default its DEBUG
        manager.setTimeoutLoggingLevel(LoggingLevel.INFO);

        return manager;
    }

}
