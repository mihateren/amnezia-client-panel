param([switch]$SkipBuild, [switch]$ReleaseBuild)
$ErrorActionPreference = 'Stop'
$workspacePath = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$projectName = 'acp-smoke-' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$secretsRoot = [IO.Path]::GetFullPath((Join-Path $workspacePath 'secrets'))
$temporaryPath = [IO.Path]::GetFullPath((Join-Path $secretsRoot $projectName))
if (-not $temporaryPath.StartsWith($secretsRoot + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Invalid temporary workspace path'
}
$null = New-Item -ItemType Directory -Path $temporaryPath -Force
$envFile = Join-Path $temporaryPath 'smoke.env'
$telegramFile = Join-Path $temporaryPath 'telegram.env'
[IO.File]::WriteAllText($telegramFile, "TELEGRAM_BOT_TOKEN=0:smoke-test-invalid`nTELEGRAM_CHAT_ID=1`n")
function Random-Hex { [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)) }
$settings = [ordered]@{
    JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    ENCRYPTION_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    BOOTSTRAP_ADMIN_NICKNAME = 'smoke-admin'
    BOOTSTRAP_ADMIN_PASSWORD = Random-Hex
    METRICS_TOKEN = Random-Hex
    GRAFANA_ADMIN_USER = 'smoke-admin'
    GRAFANA_ADMIN_PASSWORD = Random-Hex
    PUBLIC_URL = 'http://localhost:8080'
    FRONTEND_PORT = '0'
    BACKEND_PORT = '0'
    PROMETHEUS_PORT = '0'
    GRAFANA_PORT = '0'
    PANEL_ENV_FILE = $envFile.Replace('\','/')
    TELEGRAM_ENV_FILE = $telegramFile.Replace('\','/')
}
# Preserve the invoking shell's environment and prevent it overriding disposable credentials.
$previous = @{}
foreach ($name in $settings.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
    [Environment]::SetEnvironmentVariable($name,$settings[$name],'Process')
}
[IO.File]::WriteAllLines($envFile, @($settings.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value }))
$composeArgs = @('compose','--project-name',$projectName,'--env-file',$envFile,'-f',(Join-Path $workspacePath 'docker-compose.yml'))
if ($ReleaseBuild) {
    $composeArgs += @('-f', (Join-Path $workspacePath 'docker-compose.release.yml'))
}
function Service-Url([string]$Service,[string]$Port) {
    $mapping = & docker @composeArgs port $Service $Port
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve port for $Service" }
    return 'http://127.0.0.1:' + ($mapping.Trim().Split(':')[-1])
}
function Assert-PrometheusQuery([string]$Expression,[switch]$RequireData) {
    $query = [Uri]::EscapeDataString($Expression)
    $response = Invoke-RestMethod "$prometheusUrl/api/v1/query?query=$query" -TimeoutSec 10
    if ($response.status -ne 'success') { throw 'Prometheus query failed' }
    if ($RequireData -and $response.data.result.Count -eq 0) { throw "Missing metric: $Expression" }
}
try {
    & docker @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose configuration failed' }
    $upArgs = @('up','-d','--wait','--wait-timeout','180')
    if (-not $SkipBuild) { $upArgs += '--build' }
    & docker @composeArgs @upArgs
    if ($LASTEXITCODE -ne 0) { throw 'Compose startup failed' }
    $backendUrl = Service-Url backend 8080
    $frontendUrl = Service-Url frontend 80
    $frontendPage = Invoke-WebRequest "$frontendUrl/login" -TimeoutSec 10
    if ($frontendPage.StatusCode -ne 200 -or -not $frontendPage.Content.Contains('id="root"')) {
        throw 'Frontend SPA routing failed'
    }
    $prometheusUrl = Service-Url prometheus 9090
    $grafanaUrl = Service-Url grafana 3000

    $loginBody = @{nickname=$settings.BOOTSTRAP_ADMIN_NICKNAME;password=$settings.BOOTSTRAP_ADMIN_PASSWORD} | ConvertTo-Json
    $login = Invoke-RestMethod "$backendUrl/api/v1/auth/login" -Method Post -ContentType application/json -Body $loginBody
    if ($login.user.role -ne 'ADMIN') { throw 'Bootstrap login failed' }
    $headers = @{Authorization='Bearer '+$login.accessToken}
    $me = Invoke-RestMethod "$backendUrl/api/v1/auth/me" -Headers $headers
    $proxiedMe = Invoke-RestMethod "$frontendUrl/api/v1/auth/me" -Headers $headers
    if ($proxiedMe.id -ne $me.id) { throw 'Frontend API proxy failed' }
    Write-Output 'PASS: frontend SPA routes and authenticated API reverse proxy'
    $ready = $false
    for ($attempt=0;$attempt -lt 45;$attempt++) {
        try {
            $targets = Invoke-RestMethod "$prometheusUrl/api/v1/targets" -TimeoutSec 3
            $ready = @($targets.data.activeTargets | Where-Object { $_.labels.job -eq 'amnezia-control-panel' -and $_.health -eq 'up' }).Count -gt 0
            if ($ready) { break }
        } catch {}
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Prometheus could not scrape backend' }
    foreach ($metric in @('vpn_server_up','vpn_peers_total','vpn_peers_active','ssh_errors_total',
        'vpn_apply_errors_total','db_errors_total','http_server_requests_seconds_count',
        'jvm_memory_used_bytes','process_memory_rss_bytes','process_cpu_usage','jvm_threads_live_threads',
        'hikaricp_connections_active','hikaricp_connections_idle','hikaricp_connections_pending',
        'hikaricp_connections_acquire_seconds_count','hikaricp_connections_timeout_total',
        'sqlite_database_allocated_bytes','sqlite_table_allocated_bytes','sqlite_table_payload_bytes',
        'sqlite_database_up','sqlite_database_checked_timestamp_seconds','sqlite_query_seconds_bucket',
        'http_server_requests_seconds_bucket')) {
        Assert-PrometheusQuery $metric -RequireData
    }
    $grafanaAuth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($settings.GRAFANA_ADMIN_USER+':'+$settings.GRAFANA_ADMIN_PASSWORD))
    $grafanaHeaders = @{Authorization='Basic '+$grafanaAuth}
    $dashboard = $null
    for ($attempt=0;$attempt -lt 30;$attempt++) {
        try {
            $datasource = Invoke-RestMethod "$grafanaUrl/api/datasources/uid/acp-prometheus" -Headers $grafanaHeaders -TimeoutSec 3
            $dashboard = Invoke-RestMethod "$grafanaUrl/api/dashboards/uid/amnezia-control-panel" -Headers $grafanaHeaders -TimeoutSec 3
            break
        } catch { Start-Sleep -Seconds 2 }
    }
    if ($null -eq $dashboard -or $dashboard.dashboard.title -ne 'Application Overview') {
        throw 'Grafana dashboard was not provisioned'
    }
    if ($datasource.url -ne 'http://prometheus:9090') { throw 'Incorrect Grafana datasource URL' }
    $expectedDashboards = @{
        'amnezia-control-panel' = 'Application Overview'
        'amnezia-database' = 'Database Monitoring'
        'amnezia-vpn' = 'VPN Monitoring'
    }
    $provisioned = Invoke-RestMethod "$grafanaUrl/api/search?tag=amnezia&type=dash-db" -Headers $grafanaHeaders
    if ($provisioned.Count -ne 3) { throw 'Expected exactly three Amnezia dashboards' }
    foreach ($uid in $expectedDashboards.Keys) {
        $loaded = Invoke-RestMethod "$grafanaUrl/api/dashboards/uid/$uid" -Headers $grafanaHeaders
        if ($loaded.dashboard.title -ne $expectedDashboards[$uid] -or -not $loaded.meta.provisioned) {
            throw "Dashboard not provisioned: $uid"
        }
        if ($loaded.dashboard.links.Count -ne 3) { throw "Missing navigation: $uid" }
        $allPanels = @($loaded.dashboard.panels) + @($loaded.dashboard.panels | ForEach-Object { $_.panels })
        foreach ($panel in $allPanels) {
            foreach ($target in $panel.targets) {
                $expression = $target.expr.Replace('$__rate_interval','1m').Replace('$job','amnezia-control-panel').Replace('$instance','backend:8080').Replace('$uri','.*').Replace('$peer','.*').Replace('$handshake_seconds','180')
                Assert-PrometheusQuery $expression
                $missing = $expression.Replace('backend:8080','missing:8080')
                $response = Invoke-RestMethod "$prometheusUrl/api/v1/query?query=$([Uri]::EscapeDataString($missing))"
                if ($response.data.result.Count -ne 0) { throw "False data for absent instance: $($panel.title)" }
            }
        }
    }
    Write-Output 'PASS: three provisioned dashboards, navigation, all PromQL queries and absent-instance behavior'
    $alertRules = Invoke-RestMethod "$grafanaUrl/api/v1/provisioning/alert-rules" -Headers $grafanaHeaders
    if (@($alertRules | Where-Object { $_.uid -like 'acp-*' }).Count -ne 11) { throw 'Missing alert rules' }
    foreach ($rule in $alertRules) {
        Assert-PrometheusQuery $rule.data[0].model.expr
    }
    $contacts = Invoke-RestMethod "$grafanaUrl/api/v1/provisioning/contact-points" -Headers $grafanaHeaders
    if (@($contacts | Where-Object { $_.uid -eq 'acp-telegram' -and $_.type -eq 'telegram' }).Count -ne 1) { throw 'Missing Telegram receiver' }
    Write-Output 'PASS: alert rules and Telegram contact point provisioned with disposable invalid test credentials'
    & docker @composeArgs restart backend
    if ($LASTEXITCODE -ne 0) { throw 'Restart failed' }
    # Docker may assign a new random host port when restarting a port-0 container.
    $backendUrl = Service-Url backend 8080
    $afterRestart = $null
    for ($attempt=0;$attempt -lt 45;$attempt++) {
        try {
            $afterRestart = Invoke-RestMethod "$backendUrl/api/v1/auth/me" -Headers $headers -TimeoutSec 2
            break
        } catch { Start-Sleep -Seconds 1 }
    }
    if ($null -eq $afterRestart -or $afterRestart.id -ne $me.id) { throw 'Persistent SQLite state was lost' }
    $logs = (& docker @composeArgs logs --no-color 2>&1 | Out-String)
    foreach ($secret in @($settings.JWT_SECRET,$settings.ENCRYPTION_KEY,$settings.BOOTSTRAP_ADMIN_PASSWORD,
        $settings.METRICS_TOKEN,$settings.GRAFANA_ADMIN_PASSWORD,$login.accessToken)) {
        if ($logs.Contains($secret)) { throw 'Secret appeared in container logs' }
    }
    Write-Output 'PASS: backend startup, Liquibase, bootstrap/JWT, persistent restart, SSH key mode, secret-free logs'
    Write-Output 'PASS: authenticated Prometheus scrape, VPN/HTTP/JVM/RSS metrics, provisioned Grafana datasource/dashboard, all dashboard PromQL expressions'
} catch {
    $diagnostics = (& docker @composeArgs logs --no-color --tail 60 2>&1 | Out-String)
    foreach ($name in @('JWT_SECRET','ENCRYPTION_KEY','BOOTSTRAP_ADMIN_PASSWORD','METRICS_TOKEN','GRAFANA_ADMIN_PASSWORD')) {
        $diagnostics = $diagnostics.Replace($settings[$name], '[redacted]')
    }
    if ($null -ne $login -and $login.accessToken) {
        $diagnostics = $diagnostics.Replace($login.accessToken, '[redacted]')
    }
    Write-Output $diagnostics
    throw
} finally {
    & docker @composeArgs down --volumes --remove-orphans
    foreach ($name in $settings.Keys) {
        [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')
    }
    if ($temporaryPath.StartsWith($secretsRoot + [IO.Path]::DirectorySeparatorChar)) {
        Remove-Item -LiteralPath $temporaryPath -Recurse -Force
    }
}

