param([string]$Maven = 'mvn.cmd', [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
$stateDirectory = Join-Path $projectRoot 'target/local-demo'
New-Item -ItemType Directory -Force $stateDirectory | Out-Null
$stateFile = Join-Path $stateDirectory 'processes.json'
foreach ($port in @(5173, 8081, 8082, 8083)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use. Stop the existing demo with scripts/stop-local.ps1, or inspect that service first."
    }
}
if (-not $SkipBuild) {
    & $Maven -B -ntp -DskipTests install dependency:build-classpath '-Dmdep.outputFile=target/local-classpath.txt' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Java build failed' }
    if (-not (Test-Path 'ui-dashboard/node_modules/vite/bin/vite.js')) {
        Push-Location ui-dashboard
        try { npm.cmd ci --ignore-scripts --no-audit --no-fund; if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' } }
        finally { Pop-Location }
    }
}
$javaExecutable = (Get-Command java.exe).Source
$nodeExecutable = (Get-Command node.exe).Source
$started = [System.Collections.Generic.List[object]]::new()
function Save-State { ConvertTo-Json -InputObject @($started.ToArray()) | Set-Content $stateFile }
function Start-JavaService([string]$name, [string]$module, [string]$mainClass, [string[]]$appArguments) {
    $moduleDirectory = Join-Path $projectRoot $module
    $dependencies = (Get-Content (Join-Path $moduleDirectory 'target/local-classpath.txt') -Raw).Trim()
    $classPath = "$moduleDirectory/target/test-classes;$moduleDirectory/target/classes;$dependencies"
    $arguments = @('-Xmx512m', '-cp', "`"$classPath`"", $mainClass) + $appArguments
    $process = Start-Process -FilePath $javaExecutable -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $stateDirectory "$name.out.log") -RedirectStandardError (Join-Path $stateDirectory "$name.err.log")
    $started.Add(@{ name=$name; pid=$process.Id; startTime=$process.StartTime.ToUniversalTime().ToString('o') })
    Save-State
}
function Wait-Healthy([string]$url) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        try { $health = Invoke-RestMethod -Uri $url -TimeoutSec 2; if ($health.status -eq 'UP') { return } }
        catch { }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Service did not become healthy: $url. See $stateDirectory."
}
try {
    Start-JavaService 'ingestion' 'payment-ingestion-service' 'com.hvpps.ingestion.LocalDemoApplication' @()
    Wait-Healthy 'http://127.0.0.1:8081/actuator/health'
    $broker = (Get-Content (Join-Path $stateDirectory 'broker.txt') -Raw).Trim()
    Start-JavaService 'processing' 'payment-processing-service' 'com.hvpps.processing.PaymentProcessingApplication' @('--server.address=127.0.0.1', "--spring.kafka.bootstrap-servers=$broker")
    Start-JavaService 'dashboard' 'dashboard-service' 'com.hvpps.dashboard.DashboardServiceApplication' @('--server.address=127.0.0.1')
    Wait-Healthy 'http://127.0.0.1:8082/actuator/health'
    Wait-Healthy 'http://127.0.0.1:8083/actuator/health'
    $previousDemoMode = $env:HVPPS_LOCAL_DEMO
    try {
        $env:HVPPS_LOCAL_DEMO = 'true'
        $ui = Start-Process -FilePath $nodeExecutable -ArgumentList 'node_modules/vite/bin/vite.js' -WorkingDirectory (Join-Path $projectRoot 'ui-dashboard') `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $stateDirectory 'dashboard-ui.out.log') -RedirectStandardError (Join-Path $stateDirectory 'dashboard-ui.err.log')
        $started.Add(@{name='dashboard-ui'; pid=$ui.Id; startTime=$ui.StartTime.ToUniversalTime().ToString('o')})
        Save-State
    } finally { $env:HVPPS_LOCAL_DEMO = $previousDemoMode }
    $ready = $false
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try {
            $response = Invoke-WebRequest 'http://127.0.0.1:5173/api/dashboard/overview' -UseBasicParsing -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Dashboard proxy did not become ready' }
    Write-Output 'Dashboard: http://127.0.0.1:5173'
    Write-Output 'Swagger: http://127.0.0.1:8081/swagger-ui/index.html'
    Write-Output 'Local demo only: embedded Kafka and in-memory H2; data resets on restart.'
} catch {
    foreach ($entry in $started) { Stop-Process -Id $entry.pid -ErrorAction SilentlyContinue }
    throw
}
