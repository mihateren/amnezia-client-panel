import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';

const config = JSON.parse(readFileSync(new URL('./grafana/provisioning/alerting/amnezia.json', import.meta.url)));
const rules = config.groups.flatMap(g => g.rules);
assert.equal(rules.length, 11);
assert.equal(new Set(rules.map(r => r.uid)).size, 11);
assert.equal(config.contactPoints[0].receivers[0].settings.bottoken, '$TELEGRAM_BOT_TOKEN');
assert.equal(config.contactPoints[0].receivers[0].settings.chatid, '$TELEGRAM_CHAT_ID');
assert.equal(config.contactPoints[0].receivers[0].disableResolveMessage, false);
assert.equal(config.contactPoints[0].receivers[0].settings.parse_mode, 'HTML');
assert(config.contactPoints[0].receivers[0].settings.message.includes('.PanelURL | html'));
for (const rule of rules) {
  assert(rule.for && rule.for !== '0s');
  assert.equal(rule.data[0].datasourceUid, 'acp-prometheus');
  assert.equal(rule.annotations.__dashboardUid, rule.dashboardUid);
  assert.equal(rule.annotations.__panelId, String(rule.panelId));
  assert(!rule.data[0].model.expr.includes('$'));
  const dashboard = JSON.parse(readFileSync(new URL(`./grafana/dashboards/${rule.dashboardUid}.json`, import.meta.url)));
  assert(dashboard.panels.flatMap(p => [p, ...(p.panels ?? [])]).some(p => p.id === rule.panelId));
}
const labels = 'job="amnezia-control-panel",instance="backend:8080"';
const series = (metric, values, extra = '') => ({ series: `${metric}{${labels}${extra ? ',' + extra : ''}}`, values });
const check = (uid, fires) => ({ expr: `(${rules.find(r => r.uid === 'acp-' + uid).data[0].model.expr}) > 0.5`,
  eval_time: '5m', exp_samples: fires ? [{ labels: `{${labels}}`, value: 1 }] : [] });
const up = series('up', '1+0x20');
const tests = [
  { name: 'Healthy SQLite', input_series: [up, series('sqlite_database_up', '1+0x20'), series('sqlite_database_checked_timestamp_seconds', '0+15x20')], promql_expr_test: [check('database-down', false)] },
  { name: 'Missing SQLite metrics', input_series: [up], promql_expr_test: [check('database-down', true)] },
  { name: 'Stale successful SQLite check', input_series: [up, series('sqlite_database_up', '1+0x20'), series('sqlite_database_checked_timestamp_seconds', '0+0x20')], promql_expr_test: [check('database-down', true)] },
  { name: 'SQLite read failure', input_series: [up, series('sqlite_database_up', '0+0x20'), series('sqlite_database_checked_timestamp_seconds', '0+15x20')], promql_expr_test: [check('database-down', true)] },
  { name: 'Backend down suppresses dependent component alerts', input_series: [series('up', '0+0x20'), series('vpn_server_up', '0+0x20'), series('sqlite_database_up', '0+0x20')], promql_expr_test: [check('application-down', true), check('database-down', false), check('vpn-down', false)] },
  { name: 'Missing VPN metric while backend is up', input_series: [up], promql_expr_test: [check('vpn-down', true)] },
  { name: 'Single HTTP error does not trigger low-RPS ratio alert', input_series: [up, series('http_server_requests_seconds_count', '0+0x16 1+0x3', 'uri="/api/test",status="500"')], promql_expr_test: [check('http-errors', false)] },
  { name: 'Sustained HTTP errors trigger', input_series: [up, series('http_server_requests_seconds_count', '0+2x20', 'uri="/api/test",status="500"')], promql_expr_test: [check('http-errors', true)] },
  { name: 'Healthy HTTP does not trigger', input_series: [up, series('http_server_requests_seconds_count', '0+2x20', 'uri="/api/test",status="200"')], promql_expr_test: [check('http-errors', false)] }
].map(t => ({ interval: '15s', ...t }));
const directory = new URL('../build/monitoring-check/', import.meta.url);
mkdirSync(directory, { recursive: true });
writeFileSync(new URL('alert-tests.json', directory), JSON.stringify({ rule_files: [], evaluation_interval: '15s', tests }, null, 2));
console.log('Validated 11 rules, linked panels, secret references; generated 9 PromQL scenarios.');
