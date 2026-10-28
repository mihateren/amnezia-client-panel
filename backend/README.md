# Amnezia Control Panel

Java 25 / Spring Boot 4.1, Spring JDBC, SQLite, Liquibase, Spring Security,
JWT, Micrometer / Prometheus. React 19 / TypeScript / Vite frontend. One backend instance. Run Gradle commands from this backend directory.

## Run locally

Install JDK 25. Use the Linux Docker image for password-based SSH (OpenSSH and
sshpass are bundled). Add prepared Amnezia servers through the admin UI; development
and CI need no VPN credentials. Unit/component tests use a test-only VPN gateway.

PowerShell (secrets are generated in memory, not printed):
```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:ENCRYPTION_KEY = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:BOOTSTRAP_ADMIN_NICKNAME = 'admin'
$env:BOOTSTRAP_ADMIN_PASSWORD = Read-Host 'Initial administrator password (12+ characters)' -MaskInput
$env:PUBLIC_URL = 'https://panel.example.com'
./gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

Keep encryption and JWT keys in your secret store and reuse them on subsequent starts.
Generating a different encryption key makes existing device configurations unreadable.
Bootstrap creates one ADMIN only when the users table is empty. Remove bootstrap
credentials after the first successful startup. Local API listens on port 8080.
Use HTTPS via a reverse proxy for browser refresh cookies; Secure is never disabled.
Spring Boot does not automatically load a local .env for bootRun; export its values.

Linux/macOS: use `./gradlew` instead of `./gradlew.bat`.

## Tests and build

```powershell
./gradlew.bat clean test
./gradlew.bat clean build
./gradlew.bat check
./gradlew.bat testcontainersTest
```

Tests generate their own secrets, use a temporary real SQLite database, execute
Liquibase, start real HTTP endpoints, and replace only the VDS gateway with a test fake.
No production VDS or Docker daemon is required for the main suite.
Report: `build/reports/tests/test/index.html`.

`testcontainersTest` requires Docker and runs the retained component/query suite
inside a disposable `eclipse-temurin:25-jre` Linux container. It uses the same
SQLite JDBC driver, real temporary database files, Liquibase migrations and
classpath SQL resources as the host suite. Docker failures fail this task;
container tests are not silently skipped. No production VPS is contacted.
Report: `build/reports/tests/testcontainersTest/index.html`.

The audit and verification record is in
[refactoring-report.md](docs-java/refactoring-report.md); the protected VPN/database
monitoring baseline is in
[refactoring-observability.md](docs-java/refactoring-observability.md).

## Docker

Run Compose from the repository root (parent of backend). Dockerfile and monitoring files live here in backend.

```sh
cp ../.env.example ../.env
# Fill .env with keys and bootstrap credentials,
# METRICS_TOKEN (openssl rand -hex 32), and a strong GRAFANA_ADMIN_PASSWORD.
docker compose config --quiet
docker compose up --build -d
docker compose logs -f backend
```

The service binds 127.0.0.1:8080, runs as UID 10001, and stores SQLite in the named
`panel-data` volume. Put an HTTPS reverse proxy in front. Passwords are decrypted
only for each SSH operation into private temporary files on tmpfs; commands and
logs contain no passwords. The prepared Amnezia container needs `sh`, `awg`,
`awg-quick`, `sha256sum`, `base64`, `mktemp`, `cp`, `mv`, and `cut`.
The healthcheck probes application liveness; overall health also checks SQLite and VPN.

## Configuration

See [.env.example](../.env.example). Required: independent JWT_SECRET and
ENCRYPTION_KEY (base64, exactly 32 bytes), PUBLIC_URL, METRICS_TOKEN and
GRAFANA_ADMIN_PASSWORD. For first startup set bootstrap nickname/password.
Keep ENCRYPTION_KEY and the SQLite volume together across deployments and backups.
VPN server IP/login/password belong in the admin UI, not environment or CI secrets.
The panel automatically discovers one running amnezia-awg* container, its interface,
subnet and UDP port. It does not install or upgrade Amnezia.

## API

The contract is [docs-java/openapi/openapi.yaml](docs-java/openapi/openapi.yaml).

- Auth: POST /api/v1/auth/login, /refresh, /logout; GET /api/v1/auth/me.
- Invitations: POST /api/v1/admin/invitations; GET /api/v1/invitations/{token};
  POST /api/v1/invitations/{token}/register.
- Devices: GET/POST /api/v1/devices; GET/DELETE /api/v1/devices/{deviceId}.
- Config: GET /api/v1/devices/{deviceId}/config?format=TEXT|JSON; GET /config/file;
  GET /config/qr; POST /config/link; GET /api/v1/config/{token}.
- Admin users: GET /api/v1/admin/users; GET/PATCH/DELETE /api/v1/admin/users/{userId}.
- Admin devices: GET /api/v1/admin/devices; GET/DELETE /api/v1/admin/devices/{deviceId}.
- Admin: GET /api/v1/admin/audit, /dashboard, /vpn.
- GET /actuator/health and /actuator/health/liveness are public without details;
  GET /actuator/prometheus requires an ADMIN Bearer JWT or the dedicated METRICS_TOKEN.
  The scrape token is accepted only for GET /actuator/prometheus and grants no API access.

Example login:
```http
POST /api/v1/auth/login
Content-Type: application/json

{"nickname":"admin","password":"your-initial-password"}
```

Use the returned accessToken in the Authorization: Bearer header. Refresh token is
only in the HttpOnly; Secure; SameSite=Strict cookie scoped to /api/v1/auth.
Refresh rotates the token atomically; logout revokes it. Logout does not revoke
already-issued access tokens; their TTL is short. Disabled/deleted accounts fail
each authenticated request immediately.

## Decisions and consistency

- Controllers route requests; services own workflows; typed JDBC repositories own SQL.
  Immutable records are used for configuration, DTOs and model snapshots. The local
  java-spring-best-practices skill is applied; the original JDBC/SQLite and OpenAPI
  requirements take precedence over its JPA and ProblemDetail examples.
- Passwords: Spring Security PBKDF2-HMAC-SHA256 with random salts.
- Access JWT: HS256, fixed issuer, signature and expiration validation.
- Refresh / invitation / config-link tokens: 256 random bits, only SHA-256 hashes in SQLite.
- Device private keys: X25519 generated locally, AES-256-GCM encrypted with per-value
  nonce and device UUID as authenticated data. Client private keys never go to VDS.
- One fair application ReentrantLock spans fresh config read, IP allocation, apply
  and database commit. IP allocation respects existing external peer CIDR ranges.
- SSH uses password authentication with the first observed host key pinned. Secrets travel
  in encrypted SSH stdin, never process arguments. Config replacement checks the
  original SHA-256, writes on the same filesystem, and applies via awg syncconf
  using awg-quick strip. A failed apply restores the old file and runtime config.
  A failed DB commit attempts VPN compensation.
- Disable/delete removes all managed peers in one apply, then deletes devices and
  their config links and revokes sessions in one DB transaction. Failure preserves
  database state. Re-enabling creates no peers automatically. ADMIN accounts are
  protected from disable/delete to avoid administrative lockout.
- Import links are single-use. They are consumed only after successful config
  generation; a lost HTTP response still consumes the link.
- Runtime status is read from AWG; no runtime state is stored in SQLite. On SSH
  failure, device status is UNKNOWN (as permitted by OpenAPI).
- Audit is append-only, enforced by SQLite triggers; user deletion keeps history.
  Every request has a validated/generated X-Correlation-ID shared with MDC/audit.
- Login/registration are rate-limited per remote IP (30/minute). Body limit is 16 KiB.
  Configure equivalent limits at the reverse proxy; forwarded IPs are not trusted.
- Per-peer metrics have `peer_id`, `peer_name`, and `user_name` labels (the admin's
  internal user name). They are recreated after a rename and removed after device
  deletion. Keys and VPN configurations are never metric labels. Prometheus client
  1.8 preserves the required vpn_peers_total gauge name.

## Database

`db-migrations/001-init-schema.sql`: users, invitations, devices, config_links, audit_log.
`002-refresh-and-integrity.sql`: refresh_sessions, unique VPN IP index, indexes and
append-only audit triggers. Existing initial migration was preserved. No foreign keys.

## Persistence and API architecture

All database snapshots, repositories, queries and ResultSet mappers are grouped under
`persistence/{model,repository,query,mapper}`. User, Invitation, Device, ConfigLink,
RefreshSession and Audit repositories share one `NamedParameterJdbcTemplate`;
constructor parameters use `@Qualifier(DatabaseBeans.SQLITE_NAMED_JDBC_TEMPLATE_BEAN)`.
Repository contracts live in `persistence/repository`; JDBC implementations live in
`persistence/repository/impl` and retain their original Spring bean names. Services depend
on the contracts. `SqliteConfig` owns JDBC, Liquibase and transaction infrastructure;
`DatabaseBeans` centralizes their names, including the qualified transaction template.
Parameters are passed as `MapSqlParameterSource` using `QueryParameter` constants.

The six corresponding Query enums implement `QueryHolder`. Their 32 SQL files live in
`src/main/resources/sql/{user,invitation,device,config-link,refresh-session,audit}`.
`SqlResourceLoader` reads classpath resources with Apache Commons IO once when each
enum initializes; `QueryCatalog` eagerly initializes all enums during startup.
Missing or empty SQL stops startup with its resource path. QueryHolderTest checks
every query, exact parameter names and absence of positional placeholders.
The architecture refactor does not add or modify a database migration.

`compileJava` depends on `openApiGenerate`; generated sources in
`build/generated/openapi/src/main/java` are compiled automatically on a clean build.
AuthController implements AuthenticationApi; InvitationController implements InvitationsApi;
DeviceController implements DevicesApi; ConfigController implements DeviceConfigurationApi;
AdminController implements UsersApi, AdminDevicesApi and AdminDashboardApi;
AuditController implements AuditApi. Spring Actuator remains the implementation of the
health/Prometheus routes documented by generated HealthApi.

MapStruct Spring mappers (AuthApiMapper, InvitationApiMapper, DeviceApiMapper,
ConfigApiMapper, AdminApiMapper, AuditApiMapper and ErrorApiMapper) convert boundary models.
Services and persistence never import generated classes. Internal records remain immutable;
API methods inherit route/parameter/validation annotations from generated interfaces.
The mixed text/JSON config operation generates a String return type; ConfigController
serializes the mapped JSON envelope explicitly when format=JSON, preserving both media types.

The local Java/Spring skill is recorded in [AGENTS.md](AGENTS.md) for subsequent work.

## Prometheus and Grafana

Compose starts backend, Prometheus and Grafana on the same internal Docker network.
Host endpoints bind to loopback: backend :8080, Prometheus :9090, Grafana
[http://localhost:3000](http://localhost:3000). Override BACKEND_PORT, PROMETHEUS_PORT
or GRAFANA_PORT in .env if needed. Sign in with GRAFANA_ADMIN_USER / GRAFANA_ADMIN_PASSWORD.
Grafana initializes these credentials on its first start; changing .env does not reset
the password in an existing Grafana volume.

Provisioning automatically installs the Prometheus datasource (`http://prometheus:9090`,
UID `acp-prometheus`) and exactly three dashboards in the Amnezia folder:

- **Application Overview** (`/d/amnezia-control-panel`, existing UID preserved): HTTP, JVM and system.
- **Database Monitoring** (`/d/amnezia-database`): SQLite availability, SQL performance/errors and storage.
- **VPN Monitoring** (`/d/amnezia-vpn`): server status, traffic, devices and SSH diagnostics.

Definitions live in `monitoring/grafana/`. Navigation preserves time and shared job/instance variables.
Each dashboard selects one instance, puts KPI stats first and collapses secondary diagnostic rows.
Grafana state is persisted in `grafana-data`; Prometheus uses `prometheus-data` with
15 days of retention. Edit `monitoring/generate-dashboards.mjs` and run
`node backend/monitoring/generate-dashboards.mjs` from the repository root to regenerate committed JSON.

HTTP panels exclude Actuator and use route templates, not individual IDs. HTTP and SQL
histograms use bounded 1ms–30s expected values and a five-minute percentile window.
Zero observations produce no latency; sample-count panels help interpret low-RPS quantiles.
Missing instances produce no data, not artificial zero errors or a healthy status.
Application availability is the latest Prometheus scrape status, not an end-to-end business probe.

The database dashboard includes Hikari connections, wait/usage time and timeouts.
`sqlite_query_seconds_{count,sum,bucket}` instruments calls through the shared named JDBC template,
including connection acquisition and result mapping, once per call. Labels are bounded:
`operation=SELECT|INSERT|UPDATE|DELETE|OTHER`, `error=none|busy|locked|constraint|sql|other`.
SQL text, bind values and exception messages are never labels. Liquibase and direct JDBC calls
outside that template are not included; these are JDBC operation timings, not SQLite engine-only timings.
`db_errors_total` remains an application-level counter and must not be added to query errors
(the same failure may be counted by several existing handlers).
`sqlite_database_up` reports the latest storage read; `sqlite_database_checked_timestamp_seconds`
records its completion. Dashboard health is unknown after 120 seconds without a check; adjust
that threshold if changing `monitoring.storage-poll-delay` substantially. This is not a write probe.
SQLite storage metrics refresh every 30 seconds:
`sqlite_database_allocated_bytes` measures main-file pages (including free pages,
excluding WAL/journals); `sqlite_table_allocated_bytes{table}` measures table and
index pages together; `sqlite_table_payload_bytes{table}` measures useful payload.
Table sizes use read-only SQLite `dbstat`, include automatic/explicit indexes,
exclude SQLite internal tables and never read stored credentials into metrics.
Peer panels are empty before devices exist; when SSH is unavailable VPN shows DOWN
and peer runtime samples are unknown. Existing VPN metrics and collection are unchanged.
RX means devices → server; TX means server → devices. Byte metrics are gauges containing
cumulative snapshots, so traffic speed is an estimate from a one-minute difference, never
`rate()` on a gauge. Observed negative differences after resets are hidden; resets followed by
rapid growth between snapshots cannot be detected. Current byte totals are not traffic over
the selected time range and cover only devices known to the panel. Peer filters affect traffic
and device diagnostics, while total/online/offline KPI counts cover the whole VPN server.
Match the diagnostic handshake threshold variable to backend `HANDSHAKE_THRESHOLD` (default 180s).
Zero handshake means none recorded, not VPN server failure. The existing SSH error counter
also includes commands unrelated to stats; there is no dedicated collection-error or last-poll metric.
RSS uses the standard micrometer-jvm-extras
Linux binder; it is unavailable when running natively on Windows.

Offline structural validation and PromQL edge cases:

```powershell
node backend/monitoring/validate-dashboards.mjs
docker run --rm --mount "type=bind,source=$((Resolve-Path backend/build/monitoring-check).Path),target=/checks,readonly" --entrypoint /bin/promtool prom/prometheus:v3.13.3 test rules /checks/promql-tests.json
```

The fixtures cover absent exporters, idle/low-RPS HTTP, gauge traffic reset and stale SQLite checks.
The Compose smoke test checks all three provisioned dashboards, navigation, every panel query
and absence of fabricated data for a missing instance.

Prometheus reads its bearer credential from a mode-0600 tmpfs file populated from
METRICS_TOKEN at startup. Rotate this independent token by updating .env and recreating
backend and prometheus. No credentials are stored in provisioning files or dashboard JSON.
The image versions are pinned; update them deliberately with the provisioning smoke test.
Configuration follows [Grafana provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
and [Prometheus authorization](https://prometheus.io/docs/prometheus/latest/configuration/configuration/).

For an isolated Docker verification using generated disposable credentials and random ports:

```powershell
./backend/scripts/smoke-compose.ps1
```

It runs Compose config/up --build, verifies migrations through bootstrap login, persistent
SQLite after restart, authenticated scrape, required metrics, datasource/dashboard provisioning,
and every dashboard PromQL expression. It then removes only its uniquely named test project,
volumes and temporary credentials. No real VDS credentials are used by this startup test.

## Operations and limits

Run exactly one instance. Do not edit the AWG config concurrently with panel mutations.
The compare-before-write check detects prior edits but is not a distributed lock.
A process crash or SSH disconnect after a remote apply can leave an orphan peer or
an uncertain apply result; inspect VDS config and SQLite before retrying uncertain
operations. No automatic destructive reconciliation is performed.

AmneziaWG client config includes standard fields and the AWG obfuscation directives
present on the server, including AWG 3 header protection and timing parameters.
Actual client import and connectivity must be verified against your installed
AmneziaVPN 5.0.0.5 / AWG 3 build and network. The backend neither installs nor upgrades VPN.

Configure reverse proxy access logs to redact /api/v1/config/* and
/api/v1/invitations/* paths and disable request/response body logging: those paths
carry bearer secrets. Runtime threshold/container/path changes require a restart; server IP and SSH credentials can be changed through the admin UI.

## Multiple VPN servers

On `/vpn`, use **Добавить сервер** with a display name, IP, port, login and password.
The name is visible in the user selector and device cards. Edit a server to change
its name or password. A blank password preserves the current one. Disabling a server
only rejects new configurations; existing devices can still export configurations
and be deleted. All changes are audited without credential contents.

Devices retain their server and chosen application for their whole lifetime.
Amnezia VPN exports `.vpn` (compressed native guest profile); AmneziaWG exports
`.conf` and PNG QR containing raw WG text. VPN devices reject QR requests.
IPv4 allocation and remote changes are isolated per server, including overlapping
subnets. User revocation updates every affected server before committing database
changes, with compensation if another server or the database fails.

The migration preserves legacy devices. Registering their existing VPN server
attaches devices only when the peer public key and IP both match. Unmatched legacy
records remain visible under the old migration APIs; new creation always requires
a server and application choice. New managed devices are independent of legacy
maintenance/generation. User device reads and metadata updates remain local and do
not request live SSH telemetry.
