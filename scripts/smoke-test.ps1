#Requires -Version 7.0
<#
.SYNOPSIS
    Post-deployment smoke test for demo-app running in Kubernetes.
.EXAMPLE
    ./scripts/smoke-test.ps1 -Namespace demo-app -ExpectedVersion 1.1.3
#>
[CmdletBinding()]
param(
    [string]$Namespace = 'demo-app',
    [string]$ExpectedVersion = '',
    [int]$LocalPort = 18080
)

$ErrorActionPreference = 'Stop'
$script:Failures = 0

function Test-Check {
    param([string]$Name, [bool]$Condition)
    if ($Condition) { Write-Host "  [PASS] $Name" -ForegroundColor Green }
    else { Write-Host "  [FAIL] $Name" -ForegroundColor Red; $script:Failures++ }
}

Write-Host "==> Waiting for rollout of deployment/demo-app in namespace '$Namespace'"
kubectl -n $Namespace rollout status deployment/demo-app --timeout=180s
if ($LASTEXITCODE -ne 0) { throw 'Rollout did not complete' }

Write-Host "==> Port-forwarding service/demo-app to localhost:$LocalPort"
$pf = Start-Process kubectl -ArgumentList "-n $Namespace port-forward service/demo-app ${LocalPort}:80" -PassThru -WindowStyle Hidden
try {
    $base = "http://localhost:$LocalPort"
    foreach ($i in 1..30) {
        try { Invoke-RestMethod "$base/actuator/health/readiness" -TimeoutSec 2 | Out-Null; break }
        catch { Start-Sleep -Seconds 1 }
    }

    $pods = kubectl -n $Namespace get pods -l app.kubernetes.io/name=demo-app -o json | ConvertFrom-Json
    $podNames = @($pods.items.metadata.name)
    $nodeNames = @($pods.items.spec.nodeName)

    Write-Host '==> Running checks'
    Test-Check 'liveness probe UP' ((Invoke-RestMethod "$base/actuator/health/liveness").status -eq 'UP')
    Test-Check 'readiness probe UP' ((Invoke-RestMethod "$base/actuator/health/readiness").status -eq 'UP')

    $html = (Invoke-WebRequest "$base/").Content
    Test-Check 'home page shows the app message' ($html -match 'Java web app running on')
    Test-Check 'home page detects Kubernetes' ($html -match 'Running in Kubernetes')

    $info = Invoke-RestMethod "$base/api/info"
    Write-Host "  version=$($info.version) pod=$($info.podName) node=$($info.nodeName) namespace=$($info.podNamespace)"

    if ($ExpectedVersion) {
        Test-Check "version is $ExpectedVersion" ($info.version -eq $ExpectedVersion)
    } else {
        Test-Check "version reported ($($info.version))" ($info.version -and $info.version -ne 'n/a')
    }
    Test-Check 'podName matches a running pod' ($podNames -contains $info.podName)
    Test-Check "nodeName matches the pod's node" ($nodeNames -contains $info.nodeName)
    Test-Check "namespace is $Namespace" ($info.podNamespace -eq $Namespace)
}
finally {
    if ($pf -and -not $pf.HasExited) { Stop-Process -Id $pf.Id }
}

Write-Host ''
if ($script:Failures -gt 0) {
    Write-Host "Smoke test FAILED ($script:Failures check(s))" -ForegroundColor Red
    exit 1
}
Write-Host 'Smoke test PASSED' -ForegroundColor Green
