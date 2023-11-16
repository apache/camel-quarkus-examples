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
package org.acme.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("reviewService")
@ApplicationScoped
@RegisterForReflection
public class JdbcService {

    @Inject
    @DataSource("target_db")
    AgroalDataSource targetDb;

    String getHotelReviews() throws SQLException {

        StringBuilder sb = new StringBuilder();

        ResultSet rs = targetDb.getConnection().createStatement().executeQuery("SELECT (hotel_name, review) FROM Target");

        while (rs.next()) {
            sb.append(rs.getString(1));
        }

        return sb.toString();
    }
}
