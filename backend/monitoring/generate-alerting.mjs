import { mkdirSync, writeFileSync } from 'node:fs';

const selector = 'job="amnezia-control-panel"';
const m = (name, extra = '') => `${name}{${selector}${extra ? ',' + extra : ''}}`;
const up = m('up');
const live = expr => `(${expr}) and on(job,instance) (${up} == 1)`;
const aggregate = expr => `sum by (job,instance) (${expr})`;
const count = (name, extra = '') => aggregate(`increase(${m(name, extra)}[5m])`);
const percentile = (name, extra = '') => `histogram_quantile(0.95, sum by (job,instance,le) (rate(${m(name + '_bucket', extra)}[5m])))`;
const http = 'uri!~"/actuator.*"';
const requests = count('http_server_requests_seconds_count', http);
const sql = count('sqlite_query_seconds_count');
const errors = count('sqlite_query_seconds_count', 'error!="none"');
const rule = (uid, title, component, severity, duration, expression, summary, description, dashboard, panelId, noData = 'OK') => ({
  uid: `acp-${uid}`, title, condition: 'B', for: duration, noDataState: noData, execErrState: 'Error',
  labels: { service: 'amnezia-control-panel', component, severity },
  annotations: { summary, description }, dashboardUid: dashboard, panelId,
  data: [{ refId: 'A', datasourceUid: 'acp-prometheus', relativeTimeRange: { from: 600, to: 0 },
    model: { refId: 'A', datasource: { type: 'prometheus', uid: 'acp-prometheus' }, expr: `max by (job,instance) (${expression})`,
      instant: true, range: false, format: 'time_series', intervalMs: 15000, maxDataPoints: 43200 } },
  { refId: 'B', datasourceUid: '__expr__', relativeTimeRange: { from: 0, to: 0 },
    model: { refId: 'B', type: 'threshold', expression: 'A', datasource: { type: '__expr__', uid: '__expr__' },
      conditions: [{ type: 'query', evaluator: { type: 'gt', params: [0.5] }, operator: { type: 'and' },
        query: { params: ['B'] }, reducer: { type: 'last', params: [] } }] } }]
});
const app = 'amnezia-control-panel', db = 'amnezia-database', vpn = 'amnezia-vpn';
const rules = [
  rule('application-down', 'Application unavailable', 'application', 'critical', '2m', `${up} == bool 0`,
    'Приложение недоступно', 'Prometheus не может получить метрики более 2 минут. Проверьте backend, сеть и METRICS_TOKEN.', app, 1, 'Alerting'),
  rule('vpn-down', 'VPN unavailable', 'vpn', 'critical', '2m',
    live(`(${m('vpn_server_up')} == bool 0) or (${up} unless on(job,instance) ${m('vpn_server_up')})`),
    'VPN недоступен', 'SSH/VPN проверка неуспешна или метрика отсутствует более 2 минут при работающем приложении.', vpn, 1),
  rule('database-down', 'SQLite unavailable or stale', 'database', 'critical', '2m',
    live(`(${up} unless on(job,instance) ((${m('sqlite_database_up')} == 1) and on(job,instance) (${m('sqlite_database_checked_timestamp_seconds')} > time() - 120))) or (${up} * 0)`),
    'SQLite недоступна или проверка устарела', 'Чтение SQLite завершилось ошибкой либо проверка старше 120 секунд. Проверьте права, диск и блокировки.', db, 1),
  rule('http-errors', 'HTTP 5xx above 5 percent', 'application', 'warning', '5m',
    live(`((${count('http_server_requests_seconds_count', http + ',status=~"5.."')} / ${requests}) > bool 0.05) and on(job,instance) (${requests} >= 20)`),
    'HTTP: доля 5xx выше 5%', 'Порог держится 5 минут; требуется минимум 20 запросов за 5 минут. Actuator исключён.', app, 4),
  rule('http-latency', 'HTTP p95 above 1 second', 'application', 'warning', '10m',
    live(`(${percentile('http_server_requests_seconds', http)} > bool 1) and on(job,instance) (${requests} >= 20)`),
    'HTTP: p95 выше 1 секунды', 'Порог держится 10 минут; минимум 20 запросов за 5 минут. Проверьте медленные маршруты.', app, 5),
  rule('heap-high', 'JVM heap above 90 percent', 'application', 'warning', '10m',
    live(`(${aggregate(m('jvm_memory_used_bytes', 'area="heap"'))} / ${aggregate(m('jvm_memory_max_bytes', 'area="heap"') + ' > 0')}) > bool 0.9`),
    'JVM: heap выше 90%', 'Порог держится 10 минут. Проверьте GC и рост heap.', app, 7),
  rule('cpu-high', 'Process CPU above 90 percent', 'application', 'warning', '10m', live(`${m('process_cpu_usage')} > bool 0.9`),
    'CPU процесса выше 90%', 'Порог держится 10 минут. Проверьте нагрузку HTTP, GC и фоновые задачи.', app, 8),
  rule('sql-errors', 'Repeated SQL errors', 'database', 'warning', '2m', live(`${errors} >= bool 3`),
    'Повторяющиеся ошибки SQL', 'Не менее 3 ошибок за 5 минут, состояние держится 2 минуты. Подробности по категориям в Database Monitoring.', db, 3),
  rule('sqlite-locks', 'SQLite busy or locked', 'database', 'warning', '1m', live(`${count('sqlite_query_seconds_count', 'error=~"busy|locked"')} > bool 0`),
    'SQLite: BUSY / LOCKED', 'За последние 5 минут были ошибки блокировки. Состояние держится 1 минуту; проверьте длительные транзакции.', db, 3),
  rule('sql-latency', 'SQL p95 above 200 milliseconds', 'database', 'warning', '5m',
    live(`(${percentile('sqlite_query_seconds')} > bool 0.2) and on(job,instance) (${sql} >= 20)`),
    'SQL: p95 выше 200 мс', 'Порог держится 5 минут; минимум 20 операций за 5 минут. Включает ожидание соединения и mapping.', db, 4),
  rule('pool-timeouts', 'SQLite connection timeouts', 'database', 'critical', '1m', live(`${count('hikaricp_connections_timeout_total')} > bool 0`),
    'SQLite: тайм-аут получения соединения', 'За 5 минут возник тайм-аут пула, состояние держится 1 минуту. Проверьте занятость соединения.', db, 3)
];
const message = `{{ if eq .Status "firing" }}🔴 AMNEZIA — ПРОБЛЕМА{{ else }}🟢 AMNEZIA — ВОССТАНОВЛЕНО{{ end }}
{{ range .Alerts }}
{{ .Annotations.summary }}
Уровень: {{ .Labels.severity }} | Компонент: {{ .Labels.component }}
{{ if .Labels.instance }}Instance: {{ .Labels.instance }}{{ end }}
{{ .Annotations.description }}
{{ if .DashboardURL }}{{ .DashboardURL }}{{ else }}{{ .GeneratorURL }}{{ end }}
{{ end }}`;
const config = { apiVersion: 1,
  contactPoints: [{ orgId: 1, name: 'Amnezia Telegram', receivers: [{ uid: 'acp-telegram', type: 'telegram', disableResolveMessage: false,
    settings: { bottoken: '$TELEGRAM_BOT_TOKEN', chatid: '$TELEGRAM_CHAT_ID', parse_mode: 'None', disable_web_page_preview: true, message } }] }],
  policies: [{ orgId: 1, receiver: 'Amnezia Telegram', group_by: ['alertname', 'instance'], group_wait: '30s', group_interval: '5m', repeat_interval: '4h',
    routes: [{ receiver: 'Amnezia Telegram', object_matchers: [['alertname', '=', 'DatasourceError']], group_by: ['alertname', 'datasource_uid'], group_wait: '1m', group_interval: '5m', repeat_interval: '4h' }] }],
  groups: [{ orgId: 1, name: 'Amnezia health', folder: 'Amnezia', interval: '30s', rules }]
};
const directory = new URL('./grafana/provisioning/alerting/', import.meta.url);
mkdirSync(directory, { recursive: true });
writeFileSync(new URL('amnezia.json', directory), JSON.stringify(config, null, 2) + '\n');
console.log(`Generated ${rules.length} Grafana alert rules and Telegram contact point (environment references only).`);
