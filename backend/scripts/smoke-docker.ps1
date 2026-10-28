param([string]$Image = 'amnezia-control-panel:local-test')
$ErrorActionPreference = 'Stop'
$testId = [Guid]::NewGuid().ToString('N').Substring(0,12)
$containerName = "amnezia-smoke-$testId"
$volumeName = "amnezia-smoke-$testId"
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:ENCRYPTION_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:BOOTSTRAP_ADMIN_NICKNAME = 'smoke-admin'
$env:BOOTSTRAP_ADMIN_PASSWORD = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
function Wait-ForApplication([string]$BaseUrl) {
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            $health = Invoke-RestMethod "$BaseUrl/actuator/health/liveness" -TimeoutSec 2
            if ($health.status -eq 'UP') { return }
        } catch {}
        Start-Sleep -Seconds 1
    }
    throw 'Container did not become live'
}
try {
    $null = docker run --detach --name $containerName --read-only --cap-drop ALL --security-opt no-new-privileges:true --tmpfs /tmp:uid=10001,gid=10001,mode=0700,exec --mount "type=volume,source=$volumeName,target=/data" --publish 127.0.0.1::8080 --env JWT_SECRET --env ENCRYPTION_KEY --env BOOTSTRAP_ADMIN_NICKNAME --env BOOTSTRAP_ADMIN_PASSWORD --env SPRING_PROFILES_ACTIVE=prod --env SQLITE_URL=jdbc:sqlite:/data/panel.db $Image
    if ($LASTEXITCODE -ne 0) { throw 'Docker run failed' }
    $mapping = docker port $containerName 8080/tcp
    $baseUrl = 'http://127.0.0.1:' + ($mapping.Split(':')[-1]).Trim()
    Wait-ForApplication $baseUrl
    $body = @{ nickname=$env:BOOTSTRAP_ADMIN_NICKNAME; password=$env:BOOTSTRAP_ADMIN_PASSWORD } | ConvertTo-Json
    $login = Invoke-RestMethod "$baseUrl/api/v1/auth/login" -Method Post -ContentType application/json -Body $body
    if ($login.user.role -ne 'ADMIN') { throw 'Bootstrap login failed' }
    $headers = @{ Authorization = 'Bearer ' + $login.accessToken }
    $me = Invoke-RestMethod "$baseUrl/api/v1/auth/me" -Headers $headers
    $metrics = Invoke-WebRequest "$baseUrl/actuator/prometheus" -Headers $headers
    if (-not $metrics.Content.Contains('vpn_peers_total')) { throw 'Prometheus metric missing' }
    $null = docker restart $containerName
    if ($LASTEXITCODE -ne 0) { throw 'Container restart failed' }
    $mapping = docker port $containerName 8080/tcp
    $baseUrl = 'http://127.0.0.1:' + ($mapping.Split(':')[-1]).Trim()
    Wait-ForApplication $baseUrl
    $afterRestart = Invoke-RestMethod "$baseUrl/api/v1/auth/me" -Headers $headers
    if ($afterRestart.id -ne $me.id) { throw 'Persistent SQLite state was lost' }
    $logs = (docker logs $containerName 2>&1 | Out-String)
    foreach ($secret in @($env:JWT_SECRET,$env:ENCRYPTION_KEY,$env:BOOTSTRAP_ADMIN_PASSWORD,$login.accessToken)) {
        if ($logs.Contains($secret)) { throw 'Secret appeared in container logs' }
    }
    if (-not $logs.Contains('correlation_id')) { throw 'Structured correlation logging missing' }
    Write-Output 'PASS: non-root read-only container, migrations, bootstrap login, JWT, Prometheus, persistent restart, secret-free logs'
} finally {
    $null = docker rm --force $containerName 2>&1
    $null = docker volume rm $volumeName 2>&1
    Remove-Item Env:JWT_SECRET,Env:ENCRYPTION_KEY,Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}

