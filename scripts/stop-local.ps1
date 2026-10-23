$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$stateFile = Join-Path $projectRoot 'target/local-demo/processes.json'
if (-not (Test-Path $stateFile)) { Write-Output 'No local demo process record found.'; exit 0 }
$entries = Get-Content $stateFile -Raw | ConvertFrom-Json
[array]::Reverse($entries)
foreach ($entry in $entries) {
    $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
    if ($null -eq $process) { continue }
    if ($process.StartTime.ToUniversalTime().ToString('o') -ne $entry.startTime) {
        Write-Warning "PID $($entry.pid) has been reused; leaving it untouched."
        continue
    }
    Stop-Process -Id $entry.pid
    Write-Output "Stopped $($entry.name)."
}
