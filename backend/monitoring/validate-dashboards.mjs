import assert from 'node:assert/strict';
import { mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const directory = new URL('./grafana/dashboards/', import.meta.url);
const files = readdirSync(directory).filter(f => f.endsWith('.json'));
assert.equal(files.length, 3);
const dashboards = files.map(f => JSON.parse(readFileSync(new URL(f, directory))));
assert.equal(new Set(dashboards.map(d => d.uid)).size, 3);
const allPanels = dashboards.flatMap(d => d.panels.flatMap(p => [p, ...(p.panels ?? [])]));
let queries = 0;
for (const d of dashboards) {
  const panels = d.panels.flatMap(p => [p, ...(p.panels ?? [])]);
  assert.equal(new Set(panels.map(p => p.id)).size, panels.length);
  assert.deepEqual(d.links.map(l => l.url).sort(), dashboards.map(x => `/d/${x.uid}`).sort());
  assert.equal(d.templating.list.find(v => v.name === 'instance').multi, false);
  assert(panels.some(p => p.type === 'row' && p.collapsed));
  for (const p of panels) {
    assert(p.gridPos.x + p.gridPos.w <= 24);
    for (const target of p.targets ?? []) {
      queries++;
      assert(target.expr.includes('instance=~"$instance"'));
      assert(!/\b(rate|increase)\(vpn_peer_/.test(target.expr));
      assert(!target.expr.includes('or vector(0)'));
      assert.equal(target.datasource.uid, 'acp-prometheus');
    }
  }
}
const shortTitles = { 'Application • Доступность': 'Приложение', 'Database • Доступность': 'Статус SQLite',
  'VPN • Доступность': 'Статус VPN', 'VPN • Всего peers': 'Всего peers', 'HTTP • Ошибки 5xx': 'Ошибки 5xx', 'HTTP • p95': 'HTTP p95' };
const expr = title => allPanels.find(p => p.title === (shortTitles[title] ?? title)).targets[0].expr
  .replaceAll('$job', 'amnezia-control-panel').replaceAll('$instance', 'backend:8080')
  .replaceAll('$uri', '.*').replaceAll('$peer', '.*').replaceAll('$__rate_interval', '1m');
const labels = 'job="amnezia-control-panel",instance="backend:8080"';
const series = (name, values, extra = '') => ({ series: `${name}{${labels}${extra ? ',' + extra : ''}}`, values });
const check = (title, samples, transform = x => x) => ({ expr: transform(expr(title)), eval_time: '5m', exp_samples: samples });
const none = title => check(title, []);
const trafficCheck = (samples) => check('Traffic • Скорость RX/TX (оценка)', samples);
const tests = [
  { name: 'Absent exporter never fabricates healthy values', interval: '15s', input_series: [],
    promql_expr_test: ['Application • Доступность','Database • Доступность','VPN • Доступность','VPN • Всего peers','HTTP • Ошибки 5xx'].map(none) },
  { name: 'Idle HTTP produces neither false errors nor latency', interval: '15s', input_series: [
    series('up', '1+0x20'), series('http_server_requests_seconds_count', '0+0x20', 'uri="/api/test",status="200"'),
    ...['0.1','1','+Inf'].map(le => series('http_server_requests_seconds_bucket', '0+0x20', `uri="/api/test",le="${le}"`))],
    promql_expr_test: [none('HTTP • Ошибки 5xx'), check('HTTP • p95', [], e => `(${e}) == (${e})`)] },
  { name: 'Low RPS with no 5xx is a real zero error ratio', interval: '15s', input_series: [
    series('up', '1+0x20'), series('http_server_requests_seconds_count', '0+1x20', 'uri="/api/test",status="200"')],
    promql_expr_test: [check('HTTP • Ошибки 5xx', [{ labels: '{}', value: 0 }])] },
  { name: 'Gauge traffic difference uses elapsed minute', interval: '15s', input_series: [
    series('up', '1+0x20'), series('vpn_server_up', '1+0x20'), series('vpn_peer_rx_bytes', '0+150x20', 'peer_id="one"')],
    promql_expr_test: [trafficCheck([{ labels: '{}', value: 10 }])] },
  { name: 'Observed gauge reset does not produce negative traffic', interval: '15s', input_series: [
    series('up', '1+0x20'), series('vpn_server_up', '1+0x20'), series('vpn_peer_rx_bytes', '10000+0x16 0+100x3', 'peer_id="one"')],
    promql_expr_test: [trafficCheck([])] },
  { name: 'VPN failure hides misleading zero peer counts', interval: '15s', input_series: [
    series('up', '1+0x20'), series('vpn_server_up', '0+0x20'), series('vpn_peers_total', '0+0x20')],
    promql_expr_test: [none('VPN • Всего peers')] },
  { name: 'Stale database check is unknown', interval: '15s', input_series: [
    series('up', '1+0x20'), series('sqlite_database_up', '1+0x20'), series('sqlite_database_checked_timestamp_seconds', '0+0x20')],
    promql_expr_test: [none('Database • Доступность')] }
];
const output = new URL('../build/monitoring-check/', import.meta.url);
mkdirSync(output, { recursive: true });
writeFileSync(new URL('promql-tests.json', output), JSON.stringify({ rule_files: [], evaluation_interval: '15s', tests }, null, 2));
console.log(`Validated ${dashboards.length} dashboards, ${queries} queries; PromQL fixtures: ${fileURLToPath(output)}promql-tests.json`);
