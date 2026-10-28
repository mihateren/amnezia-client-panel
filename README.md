# Amnezia Control Panel

React / TypeScript frontend and Java 25 / Spring Boot 4.1 backend.

Production HTTPS deployment, release artifacts and GitHub Actions CI are documented
in [deploy/README.md](deploy/README.md).

```text
amnezia-client-panel/
├── backend/
│   ├── src/                 Java implementation, tests, SQL and migrations
│   ├── gradle/              Wrapper and dependency catalog
│   ├── docs-java/openapi/   API contract
│   ├── docker/              Backend entrypoint
│   ├── monitoring/          Prometheus and Grafana provisioning
│   ├── scripts/             Integration smoke checks
│   ├── agent-skills/        Java/Spring development guide
│   ├── local/               Ignored local tools and runtime files
│   ├── Dockerfile
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── frontend/                Independent React application and Docker image
├── secrets/                 Ignored deployment secrets
├── docker-compose.yml       Shared application stack
├── .env.example             Shared environment template
└── .env                     Ignored local settings and generated credentials
```

## Run with Docker

Run from this repository root:

```sh
cp .env.example .env
# Configure the environment and secret file paths.
docker compose config --quiet
docker compose up --build -d
```

Local instance: http://127.0.0.1:15173/login. Administrator nickname and password are
in the ignored `.env` (BOOTSTRAP_ADMIN_NICKNAME and BOOTSTRAP_ADMIN_PASSWORD).
Never commit this file or the secrets directory. The VPS password is not stored.

PUBLIC_URL must exactly match the browser origin, including scheme, hostname and port.
`http://localhost:15173` and `http://127.0.0.1:15173` are different origins.
After changing PUBLIC_URL, apply it with:

```sh
docker compose up -d --no-build --no-deps --force-recreate backend
```

Production needs HTTPS in front of the frontend proxy. Secure refresh cookies remain
enabled. Access tokens are kept only in browser memory.

## Independent development

Backend commands run inside backend:

```sh
cd backend
./gradlew.bat clean build
./gradlew.bat bootRun
```

Linux/macOS: use `./gradlew`. Export environment variables before bootRun; Spring Boot
does not load the shared root `.env` automatically. See backend/README.md for details.

Frontend commands run inside frontend:

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
pnpm build
pnpm test
pnpm test:e2e
pnpm api:generate
```

Node 24 / pnpm 11. Vite serves on 5173 and proxies `/api` to 127.0.0.1:8080.
Set API_TARGET to change the backend target and match PUBLIC_URL to the browser address.
Browser acceptance tests use Microsoft Edge; start the dev server before running them.

The Russian interface covers invitation registration, sign-in/session refresh/logout,
user and admin device management, configuration text/JSON/file/QR/single-use links,
user search/status/delete, VPN health, dashboard and filtered/paginated audit.
Existing external VPN peers are counted by the server but are not imported as accounts.

Users select a named VPN server above their devices and choose an application before
creating a connection. **Amnezia VPN** exports only a `.vpn` guest profile;
**AmneziaWG** exports a raw `.conf` file or a QR encoding the same configuration.
Admin **VPN-серверы** supports adding prepared Amnezia servers with IP, login and
password, renaming them (for example, «Рекомендуемый»), and disabling new
configurations without interrupting existing connections. Passwords are encrypted
in SQLite and never returned by the API. No VPN credentials are required in CI.

## Verification

```powershell
./backend/scripts/smoke-compose.ps1
```

This isolated Docker check verifies the frontend SPA and API proxy, real SQLite,
backend authentication, persistence after restart, Prometheus scraping and Grafana
provisioning. It generates disposable credentials and removes only its own test project.
The opt-in VPS browser test and its cleanup behavior are documented in frontend/README.md.
