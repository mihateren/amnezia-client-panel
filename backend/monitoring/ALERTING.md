# Grafana → Telegram

Alerting uses the existing Grafana scheduler and Prometheus datasource; no additional
service is needed. Rules, receiver and notification policy are provisioned from
`grafana/provisioning/alerting/amnezia.json`. Edit `generate-alerting.mjs` and regenerate
with `node backend/monitoring/generate-alerting.mjs` from the repository root.

## Secrets and deployment

Copy `backend/monitoring/telegram.example` to the Git-ignored `secrets/telegram.env`
and fill `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID`. Channel IDs must use Bot API
format (usually `-100...`); the bot must be a channel administrator with posting rights.
Only Grafana loads this dedicated env file. The token is not in committed provisioning.
Override its path using `TELEGRAM_ENV_FILE`. Do not print `docker compose config` or
container environment to shared logs; use `docker compose config --quiet` for validation.

Set `GRAFANA_ROOT_URL` in `.env` to the URL users can open from notifications. The
current local installation uses `http://127.0.0.1:13000`; this is not reachable from
a phone or another machine. Configure the actual Grafana external URL when deploying.

```powershell
node backend/monitoring/generate-alerting.mjs
docker compose config --quiet
docker compose up -d --no-deps grafana
```

Grafana restart reloads file provisioning and the secret env file. Existing Grafana
admin credentials in its data volume are independent of the Telegram token. This
file owns the notification policy tree; consolidate future policy edits here.

## Rules

| Rule | Condition | Pending period |
| --- | --- | --- |
| Application unavailable | Prometheus scrape down or target absent | 2 min |
| VPN unavailable | VPN UP=0 or missing VPN metric while app is up | 2 min |
| SQLite unavailable or stale | Storage read failed, metrics absent, or check older than 120s | 2 min |
| HTTP errors | 5xx >5%, at least 20 requests / 5 min | 5 min |
| HTTP latency | p95 >1s, at least 20 requests / 5 min | 10 min |
| Heap | Used/max >90% | 10 min |
| CPU | Process CPU >90% | 10 min |
| SQL errors | At least 3 failed operations / 5 min | 2 min |
| SQLite locks | BUSY/LOCKED errors within 5 min | 1 min |
| SQL latency | p95 >200ms, at least 20 operations / 5 min | 5 min |
| Pool timeout | Connection timeout within 5 min | 1 min |

Rules evaluate every 30s per instance. Component rules are suppressed when the
application scrape is down. No-data for traffic/latency rules is normal; application
availability reports absent scrape data as an alert. Query failures produce
DatasourceError notifications grouped by datasource rather than individual rule.
Database health requires the new `sqlite_database_up` and
`sqlite_database_checked_timestamp_seconds` meters; deploy the monitoring backend
before interpreting a missing-metrics notification as a database incident.

Notifications wait 30s to group alerts, update at 5-minute intervals, and repeat
unresolved alerts every 4 hours. Recovery notifications are enabled. Messages use
Telegram HTML: a status heading, one-line summary and a clickable panel link.
Dynamic text and URLs are HTML-escaped. Detailed diagnostics remain in Grafana.
Rules explicitly provision `__dashboardUid`/`__panelId` annotations for panel links.
No alerts are based on
offline peers or idle traffic; both are normal for VPN clients. Grafana itself cannot
notify if Grafana or the whole host is down; that requires external monitoring.

## Validation

```powershell
node backend/monitoring/validate-alerting.mjs
docker run --rm --mount "type=bind,source=$((Resolve-Path backend/build/monitoring-check).Path),target=/checks,readonly" --entrypoint /bin/promtool prom/prometheus:v3.13.3 test rules /checks/alert-tests.json
```

The Compose smoke script validates the eleven alert queries and receiver using an
isolated invalid Telegram credential; it never sends to the real channel. For an
end-to-end delivery check use Grafana's contact-point Test button. Grafana 13 exposes
the API at `/apis/notifications.alerting.grafana.app/v1beta1/namespaces/default/receivers/{name}/test`
with `{integration, alert}`; the legacy test endpoint has been removed.

References: [Grafana file provisioning](https://grafana.com/docs/grafana/latest/alerting/set-up/provision-alerting-resources/file-provisioning/),
[Telegram channel ID conversion](https://core.telegram.org/api/bots/ids).
