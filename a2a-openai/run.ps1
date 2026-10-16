#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Windows counterpart of run.sh.
# Starts the two specialist agents and the orchestrator in dev mode, each in its own JVM.
# Logs go to <module>.log and process ids to <module>.pid. Stop everything with .\stop.ps1

Set-Location $PSScriptRoot

if (-not $env:OPENAI_API_KEY) {
    Write-Warning ("OPENAI_API_KEY is not set, the agents will fail to start. Set it to your OpenAI API key, or to any " +
        "non-empty value when OPENAI_BASE_URL points to a local model server, see the README")
}

foreach ($module in 'billing-agent', 'technical-agent', 'orchestrator') {
    Write-Host "starting $module"
    # cmd.exe runs mvn.cmd and sends stdout and stderr to the same log file, as run.sh does.
    # -Ddebug=false: the three dev mode processes would otherwise compete for the same debugger port
    $process = Start-Process -FilePath cmd.exe -WindowStyle Hidden -PassThru `
        -ArgumentList '/c', "mvn -f $module\pom.xml quarkus:dev -Ddebug=false > $module.log 2>&1"
    Set-Content -Path "$module.pid" -Value $process.Id
}

Write-Host "logs: billing-agent.log technical-agent.log orchestrator.log"
