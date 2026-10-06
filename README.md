# Amnezia Control Panel

React / TypeScript frontend and Java 25 / Spring Boot 4.1 backend.

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
├── secrets/                 Ignored SSH keys and known_hosts
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

For AmneziaVPN, use **Скачать .conf файл**, **Сканировать QR-код**, or
**Скопировать ключ доступа** in device details. The downloaded .conf contains a
native vpn:// guest profile, preserving all AWG 3.1 fields. QR uses Amnezia's framed
QR series format; scan every displayed part in the app's scanner without closing it.
The client must support the server's AWG version. Each action includes explicit
import instructions in the interface.

If the official client uses an I1 signature absent from the server's awg0.conf,
set VPN_CLIENT_SIGNATURE to that signature in the ignored .env. It is included in
every client export; an I1 already present in the server configuration takes priority.

## Verification

```powershell
./backend/scripts/smoke-compose.ps1
```

This isolated Docker check verifies the frontend SPA and API proxy, real SQLite,
backend authentication, persistence after restart, Prometheus scraping and Grafana
provisioning. It generates disposable credentials and removes only its own test project.
The opt-in VPS browser test and its cleanup behavior are documented in frontend/README.md.
