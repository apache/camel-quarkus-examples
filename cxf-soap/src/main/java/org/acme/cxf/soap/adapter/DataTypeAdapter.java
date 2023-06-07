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
package org.acme.cxf.soap.adapter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DataTypeAdapter {

    private DataTypeAdapter() {
    }

    public static LocalDate parseDate(String s) {
        if (s == null) {
            return null;
        }
        return LocalDate.parse(s);
    }

    public static String printDate(LocalDate dt) {
        if (dt == null) {
            return null;
        }

        return dt.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public static LocalTime parseTime(String s) {
        if (s == null) {
            return null;
        }

        return LocalTime.parse(s);
    }

    public static String printTime(LocalTime dt) {
        if (dt == null) {
            return null;
        }

        return dt.format(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    public static LocalDateTime parseDateTime(String s) {
        if (s == null) {
            return null;
        }

        return LocalDateTime.parse(s);
    }

    public static String printDateTime(LocalDateTime dt) {
        if (dt == null) {
            return null;
        }

        return dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
