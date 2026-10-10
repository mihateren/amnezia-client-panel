# Monitoring dashboards

Telegram notifications and the eleven alert rules are documented in [ALERTING.md](ALERTING.md).

Exactly three dashboards are provisioned in the existing Amnezia folder, using the
existing `acp-prometheus` datasource and directory provider:

| Dashboard | UID | Investigation and diagnosis |
| --- | --- | --- |
| Application Overview | `amnezia-control-panel` (preserved) | HTTP, JVM, System |
| Database Monitoring | `amnezia-database` | SQL Performance, SQL Errors, Storage, connection pool |
| VPN Monitoring | `amnezia-vpn` | Traffic, Peers, Server Health |

The generator shares layout, spacing, units, no-data handling, status colors and
navigation. KPI rows use four columns, charts use two; secondary rows are collapsed.
Dashboard links preserve time and the single selected job/instance. URI and peer
filters are local to relevant panels. Main VPN peer counts always describe the whole
server, not the selected subset of managed devices.

## Changes and audit findings

- Replaced the combined tabbed dashboard with three independently linked dashboards.
- Added application uptime, error ratio, HTTP percentiles, status classes, endpoint
  rankings, GC and class/thread diagnostics. Retained useful memory/CPU/RSS panels.
- Moved SQLite pool/storage diagnostics out of the application dashboard. Added
  JDBC rate, percentiles, operation rankings, error categories and busy/locked errors.
- VPN panels distinguish server availability, absent handshake and unknown runtime.
  Added online/offline KPI counts, traffic estimates, peer rankings and peer tables.
- Removed unconditional `or vector(0)` fallbacks which hid missing exporters.
  Legitimate zero-error fallbacks are tied to an existing request/query counter.
- Replaced latency divisions by clamped tiny denominators with positive-count
  guards. Percentiles preserve `le` and use five-minute windows; sample counts expose
  sparse traffic. Actuator polling is excluded from HTTP panels.
- Removed cross-instance mixing by selecting one instance throughout. Rates are
  computed before aggregating counters. Rankings use instant queries, not a changing
  top-k set reduced across the whole dashboard range.
- Preserved `db_errors_total` as a separate application-level error series because
  it can count the same exception in more than one handler.
- Preserved all VPN collection code, meter names, labels and meanings. RX/TX are
  gauges, not Prometheus counters. Their one-minute difference is explicitly an
  estimate; negative differences are omitted. Current cumulative bytes are never
  presented as exact traffic during the selected period.

## Instrumentation

`sqlite_query_seconds_count`, `_sum`, `_bucket` measure calls through the shared
qualified `NamedParameterJdbcTemplate`. A delegating `JdbcOperations` proxy retains
the existing template, transaction behavior, exception translation and return values.
Timing includes connection wait and result mapping. The labels are limited to five
operation categories and six error categories; SQL text/parameters are never labels.
Migration/direct JDBC calls outside this template are not instrumented.

`sqlite_database_up` and `sqlite_database_checked_timestamp_seconds` describe the
existing scheduled storage read. Unknown/stale checks do not show UP. Storage still
uses allocated SQLite main-file pages and table/index payload; WAL/SHM are excluded.

Micrometer HTTP and SQL histogram buckets are enabled with expected values from
1ms to 30s. Prometheus scrape/auth configuration and Grafana provisioning paths and
datasource UID are unchanged. No dependencies, business logic or API contracts were
changed by this monitoring work.

## Reproduction

From the repository root:

```powershell
node backend/monitoring/generate-dashboards.mjs
node backend/monitoring/validate-dashboards.mjs
docker run --rm --mount "type=bind,source=$((Resolve-Path backend/build/monitoring-check).Path),target=/checks,readonly" --entrypoint /bin/promtool prom/prometheus:v3.13.3 test rules /checks/promql-tests.json
./backend/scripts/smoke-compose.ps1
```

From `backend`: `./gradlew.bat clean build`.

Validation covers three unique UIDs, navigation, instance filters, 90 panel queries,
actual histogram export, real temporary SQLite successes/constraint/busy failures,
and PromQL fixtures for absent targets, idle/low RPS, traffic reset and stale DB checks.
The Compose smoke test uses disposable credentials and an unreachable test SSH host;
it does not contact a real VPN server. It checks provisioning, authenticated scraping,
every panel query, missing-instance behavior and backend restart, then removes its
own containers and volumes.

## Limits

Verification on 2026-10-08: clean backend build passed (62 tests, zero failures);
all seven promtool scenarios passed; Compose provisioning/scraping and 90 panel
queries passed. The three dashboards and navigation/collapsible rows were inspected
in Grafana 13.2.2 at 1366×768 with an unavailable test target. Empty values are gray,
scrape failure is DOWN. Real VPN traffic rendering was not visually verified against
a production server; no production VPN was contacted.

- Histogram quantiles are estimates, particularly at low traffic; values outside
  the configured expected bounds have coarser resolution.
- No existing VPN metric records poll completion time or distinguishes collection
  errors from other SSH failures. These cannot be inferred reliably from handshake.
- A VPN reset followed by enough traffic between snapshots can evade the negative
  difference check. Exact reset-corrected traffic requires a future metric change.
- The diagnostic handshake selector defaults to 180s and must match a customized
  backend `HANDSHAKE_THRESHOLD`. Online counts use the backend's actual setting.
- SQLite read availability is not a write probe. The 120s freshness threshold must
  be revisited if the storage polling interval is changed substantially.
- RSS/file descriptors depend on OS support. Unknown values remain unknown.

## Design references

The metric groups were compared with [JVM Micrometer](https://grafana.com/grafana/dashboards/4701-jvm-micrometer/),
[Spring Boot HTTP](https://grafana.com/grafana/dashboards/20820-spring-boot-http/), and
[Spring Boot Statistics](https://grafana.com/grafana/dashboards/6756-spring-boot-statistics/).
Queries are adapted to this application's actual meters rather than copied from
these templates.
