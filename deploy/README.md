# Production deployment

The current deployment uses `https://panel.31.129.101.114.sslip.io` and
`https://grafana.31.129.101.114.sslip.io`. Both DNS names resolve to the application
VPS. These temporary names depend on sslip.io; replace them with your own DNS
records when available.

Caddy exposes ports 80/443, obtains and renews public TLS certificates and redirects
HTTP to HTTPS. `/api/*` is proxied to the backend on the **same origin** as the SPA.
`PUBLIC_URL` must equal that HTTPS origin. Refresh cookies remain Secure/HttpOnly;
the backend deliberately rejects foreign Origin headers. No wildcard CORS is needed.
Grafana requires its own administrator password; anonymous login and sign-up are
disabled. Backend, Prometheus and direct Grafana ports bind only to loopback.

## CI on GitHub Actions

`.github/workflows/ci.yml` runs on pushes, pull requests and manual dispatch:

1. Java 25 clean Gradle build, unit/integration tests against temporary SQLite,
   plus the Linux Testcontainers SQLite suite; test and coverage reports uploaded.
2. Node 24/pnpm 11 frozen install, generated OpenAPI type drift check, unit tests,
   production build and Chromium browser acceptance tests with mocked API.
3. Disposable Docker Compose smoke: real SQLite, authentication, restart persistence,
   frontend proxy, protected Prometheus scraping, Grafana datasource, three dashboards,
   all dashboard queries and alert provisioning. It never contacts production VPN.
   This job builds runtime images from the JAR/dist artifacts tested above and also
   executes the PromQL fixture suite with promtool; it does not rebuild application code.
4. Only after every check succeeds, package the tested JAR, frontend and deployment
   files into the `panel-release-<commit>` artifact (14-day retention). No secrets.

CI has read-only repository permissions and needs no VPS/GitHub secrets. Publishing
code does **not** automatically deploy it. Live VPN tests are opt-in and never run
in CI. Action versions follow the official [setup-java documentation](https://github.com/actions/setup-java).

## Initial server setup

Install Docker Engine with Compose v2.24.4 or newer. Allow inbound SSH, TCP 80/443
and optionally UDP 443. The VPN UDP port belongs on the separate VPN server.

Create `/opt/amnezia-panel` (0700), unpack the release artifact there, and copy
`.env.example` to `.env` (0600). Generate independent random JWT/encryption keys,
metrics token, panel password and Grafana password. Keep the SQLite volume and
ENCRYPTION_KEY together in backups: existing device keys depend on it.

No VPN credentials or VPN-specific parameters are required in CI, Compose or `.env`.
After signing in, open **VPN-серверы → Добавить сервер** and enter a public display
name, IP, SSH port (default 22), username and password. Amnezia must already be
running in a single `amnezia-awg*` container. The panel detects the interface,
subnet and VPN port. SSH uses password authentication; the host key is pinned
on first registration and verified on subsequent connections. Passwords are
AES-GCM encrypted in SQLite with the application's ENCRYPTION_KEY, never returned
by the API or written to logs.

Admins can rename servers and disable new configurations without stopping existing
connections. Users choose a server and client type: Amnezia VPN downloads `.vpn`;
AmneziaWG downloads `.conf` or displays a QR with the same raw WG configuration.
On adding a previously used server, existing devices are attached only when both
their public key and assigned IP match a peer on that server. Unmatched legacy
devices remain visible and can be recreated after selecting a managed server.

Create `secrets/telegram.env` with placeholders if Telegram is unused. Production
defaults to `GRAFANA_EXECUTE_ALERTS=false`; rules remain provisioned, but notification
execution is disabled until a real destination is configured and authorized.

```sh
cd /opt/amnezia-panel
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.release.yml config --quiet
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.release.yml up --build -d --wait --wait-timeout 180
```

The release override builds small runtime images from the tested binaries; Gradle
and Node never run on the production VPS. The base Compose file alone still supports
source builds for local development. Production container memory is bounded for a
2 GB VPS, logs rotate at 3 × 10 MB per service, Prometheus retains at most 7 days or
512 MB of persisted blocks (WAL/head data may temporarily exceed this).
Grafana has a 768 MB limit and a readiness check with a three-minute initial grace
period. A 384 MB limit caused severe filesystem-cache reclaim and HTTP timeouts
on this VPS even though the process did not get OOM-killed. Check container memory
pressure as well as RSS when diagnosing slow dashboards.
If Ubuntu's default mirror is slow from the VPS, set `UBUNTU_MIRROR` to the
provider's Ubuntu mirror URL. Package signature verification remains enabled.
Only the normal release, updates and security repositories are used with a custom
mirror; optional backports are omitted because provider mirrors may not carry them.

## Updates and recovery

Keep a copy of the previous release archive. Before updating, stop the backend and
back up its entire `panel-data` volume plus `.env`, `secrets/`, Grafana data and Caddy
certificate data to private storage; restart the backend after backup. Never copy a
running SQLite main file alone because committed data can still be in WAL.
Unpack the next CI artifact into the same directory, preserving `.env`, `secrets/`
and Docker volumes, then run the commands above. Do not use `down -v` in production.
For rollback across schema changes, restore the matching database backup and release.

Verify `/login`, login plus session refresh, a disposable device create/revoke cycle,
Grafana password login and Prometheus target health after deployment. Caddy certificate
automation is described in its [official documentation](https://caddyserver.com/docs/automatic-https).

Inspect status without dumping secrets:

```sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.release.yml ps
curl -fsS http://127.0.0.1:9090/api/v1/targets
```

Avoid `docker compose config` without `--quiet` or sharing container environment dumps.
