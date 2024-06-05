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
import groovy.json.JsonOutput;

final int MAX_GROUPS = 10
final List<Map<String, String>> GROUPS = new ArrayList<>()
int groupId = 0

// Distribute example projects across a bounded set of test groups and output as JSON
new File(".").eachFileRecurse { file ->
    if (file.getName() == "pom.xml" && file.getParentFile().getParentFile().getName() == ".") {
        if (GROUPS[groupId] == null) {
            GROUPS[groupId] = [:]
            GROUPS[groupId].name = "group-${String.format("%02d", groupId + 1)}"
            GROUPS[groupId].tests = ""
        }

        String separator = GROUPS[groupId].tests == "" ? "" : ","

        GROUPS[groupId].tests = "${GROUPS[groupId].tests}${separator}${file.parentFile.name}"

        groupId += 1;
        if (groupId == MAX_GROUPS) {
            groupId = 0
        }
    }
}

print JsonOutput.toJson(["include": GROUPS])
