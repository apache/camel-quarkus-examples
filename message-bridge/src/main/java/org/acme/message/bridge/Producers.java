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
package org.acme.message.bridge;

import com.ibm.mq.jakarta.jms.MQXAConnectionFactory;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;
import io.quarkiverse.messaginghub.pooled.jms.PooledJmsWrapper;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.transaction.TransactionManager;
import org.apache.camel.component.jms.JmsComponent;
import org.eclipse.microprofile.config.ConfigProvider;
import org.springframework.transaction.jta.JtaTransactionManager;

public class Producers {
    /**
     * Create a connection factory for IBM MQ.
     * <p/>
     * Since there is no IBM MQ extension for quarkus, we need to create the connection factory manually
     *
     * @param  wrapper wrapper that is used to add pooling capabilities to the connection factory
     * @return         a new connection factory instance
     */
    @Identifier("ibmConnectionFactory")
    public ConnectionFactory createXAConnectionFactory(PooledJmsWrapper wrapper) {
        MQXAConnectionFactory mq = new MQXAConnectionFactory();
        try {
            mq.setHostName(ConfigProvider.getConfig().getValue("ibm.mq.host", String.class));
            mq.setPort(ConfigProvider.getConfig().getValue("ibm.mq.port", Integer.class));
            mq.setChannel(ConfigProvider.getConfig().getValue("ibm.mq.channel", String.class));
            mq.setQueueManager(ConfigProvider.getConfig().getValue("ibm.mq.queueManagerName", String.class));
            mq.setTransportType(WMQConstants.WMQ_CM_CLIENT);
            mq.setStringProperty(WMQConstants.USERID,
                    ConfigProvider.getConfig().getValue("ibm.mq.user", String.class));
            mq.setStringProperty(WMQConstants.PASSWORD,
                    ConfigProvider.getConfig().getValue("ibm.mq.password", String.class));
        } catch (JMSException e) {
            throw new RuntimeException("Unable to create IBM MQ Connection Factory", e);
        }
        return wrapper.wrapConnectionFactory(mq);
    }

    /**
     * Define the JtaTransactionManager instance that is used in jms components.
     *
     * @param  transactionManager transaction manager
     * @return                    JtaTransactionManager instance
     */
    @Singleton
    JtaTransactionManager manager(TransactionManager transactionManager) {
        return new JtaTransactionManager(transactionManager);
    }

    /**
     * Define the "ibmmq" jms component.
     *
     * @param  cf ibm mq connection factory that is automatically injected by Quarkus based on the given identifier
     * @param  tm transaction manager to use
     * @return    a new JmsComponent instance
     */
    @Identifier("ibmmq")
    JmsComponent ibmmq(@Identifier("ibmConnectionFactory") ConnectionFactory cf, JtaTransactionManager tm) {
        JmsComponent ibmmq = new JmsComponent();
        ibmmq.setConnectionFactory(cf);
        ibmmq.setTransactionManager(tm);
        return ibmmq;
    }

    /**
     * Define the "amq" jms component.
     *
     * @param  cf activemq connection factory that is automatically injected by Quarkus based on the given identifier
     * @param  tm transaction manager to use
     * @return    a new JmsComponent instance
     */
    @Identifier("amq")
    JmsComponent amq(ConnectionFactory cf, JtaTransactionManager tm) {
        JmsComponent amq = new JmsComponent();
        amq.setConnectionFactory(cf);
        amq.setTransactionManager(tm);
        return amq;
    }
}
