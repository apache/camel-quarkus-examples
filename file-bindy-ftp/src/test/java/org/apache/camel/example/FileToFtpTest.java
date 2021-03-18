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
package org.apache.camel.example;

import java.util.concurrent.TimeUnit;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

@QuarkusTest
@QuarkusTestResource(FtpTestResource.class)
public class FileToFtpTest {

    @Test
    public void testFileToFtp() throws JSchException {
        JSch jsch = new JSch();
        jsch.setKnownHosts(System.getProperty("user.home") + "/.ssh/known_hosts");

        Session session = jsch.getSession("ftpuser", System.getProperty("ftp.host"));
        session.setPort(Integer.parseInt(System.getProperty("ftp.port")));
        session.setPassword("ftppassword");
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);
        Channel sftp = null;
        try {
            sftp = session.openChannel("sftp");
            sftp.connect(5000);

            ChannelSftp channelSftp = (ChannelSftp) sftp;

            await().atMost(10L, TimeUnit.SECONDS).pollDelay(500, TimeUnit.MILLISECONDS).until(() -> {
                try {
                    return channelSftp.ls("uploads/books").size() >= 3;
                } catch (Exception e) {
                    return false;
                }
            });
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }
}
