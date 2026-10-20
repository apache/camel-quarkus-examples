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

# Windows counterpart of stop.sh.
# Stops the processes started by .\run.ps1 and removes the pid files. The log files are kept.

Set-Location $PSScriptRoot

foreach ($module in 'orchestrator', 'technical-agent', 'billing-agent') {
    if (Test-Path "$module.pid") {
        Write-Host "stopping $module"
        # /T kills the whole tree: cmd.exe, the Maven JVM and the forked dev mode JVM
        taskkill /PID (Get-Content "$module.pid") /T /F | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Remove-Item "$module.pid"
        }
    }
}
