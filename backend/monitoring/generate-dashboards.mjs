import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

// Reproducible, shared visual defaults for the three provisioned dashboards.
const ds = { type: 'prometheus', uid: 'acp-prometheus' };
const scope = 'job=~"$job",instance=~"$instance"';
const m = (name, labels = '') => `${name}{${scope}${labels ? ',' + labels : ''}}`;
const up = m('up');
const live = expr => `(${expr}) and on() (${up} == 1)`;
const vpn = expr => live(`(${expr}) and on() (${m('vpn_server_up')} == 1)`);
const rate = (name, labels = '', window = '$__rate_interval') => `rate(${m(name, labels)}[${window}])`;
const sum = expr => `sum(${expr})`;
const titles = ['Application Overview', 'Database Monitoring', 'VPN Monitoring'];
const uids = ['amnezia-control-panel', 'amnezia-database', 'amnezia-vpn'];
const variable = (name, label, query, multi = false) => ({ name, label, type: 'query', datasource: ds,
  query: { query, refId: name }, definition: query, refresh: 2, sort: 1, multi,
  includeAll: multi, ...(multi ? { allValue: '.*', current: { text: 'All', value: '$__all' } } : {}), options: [] });
const baseVariables = () => [variable('job', 'Job', 'label_values(up{job="amnezia-control-panel"}, job)'),
  variable('instance', 'Instance', 'label_values(up{job=~"$job"}, instance)')];
let id, y, panels;
function panel(title, unit, queries, type = 'timeseries', description = '') {
  const p = { id: id++, title, type, description, datasource: ds,
    targets: queries.map(([expr, legendFormat], i) => ({ refId: String.fromCharCode(65 + i), expr,
      legendFormat, datasource: ds, instant: type !== 'timeseries', range: type === 'timeseries',
      format: type === 'table' ? 'table' : 'time_series', editorMode: 'code' })),
    fieldConfig: { defaults: { unit, noValue: 'Нет данных', min: 0,
      color: { mode: type === 'stat' ? 'thresholds' : 'palette-classic' },
      thresholds: { mode: 'absolute', steps: [{ color: 'green', value: null }] },
      mappings: ['nan', 'null'].map(match => ({ type: 'special', options: { match, result: { text: 'Нет данных', color: 'gray' } } })),
      ...(type === 'timeseries' ? { custom: { drawStyle: 'line', lineInterpolation: 'linear', lineWidth: 2,
        fillOpacity: 8, showPoints: 'never', spanNulls: false, axisBorderShow: false } } : {}) }, overrides: [] },
    options: type === 'stat' ? { colorMode: 'value', graphMode: 'none', textMode: 'auto',
      reduceOptions: { calcs: ['last'], fields: '', values: false }, orientation: 'auto' } :
      type === 'table' ? { showHeader: true, sortBy: [{ displayName: 'Value', desc: true }], cellHeight: 'sm' } :
      { legend: { displayMode: 'table', placement: 'bottom', calcs: ['last'] }, tooltip: { mode: 'multi', sort: 'desc' } } };
  if (type === 'timeseries') p.fieldConfig.overrides = [
    ['/^RX/', 'blue'], ['/^TX/', 'purple'], ['2xx', 'green'], ['3xx', 'blue'],
    ['4xx', 'yellow'], ['5xx', 'red'], ['Online', 'green'], ['Offline', 'yellow']
  ].map(([name, color]) => ({ matcher: { id: name.startsWith('/') ? 'byRegexp' : 'byName', options: name.startsWith('/') ? name.slice(1,-1) : name }, properties: [{ id: 'color', value: { mode: 'fixed', fixedColor: color } }] }));
  if (type === 'table') p.transformations = [{ id: 'organize', options: {
    excludeByName: { Time: true, __name__: true, job: true, instance: true, application: true },
    renameByName: { peer_name: 'Устройство', user_name: 'Пользователь', peer_id: 'ID', uri: 'Маршрут', method: 'Метод', operation: 'Операция', error: 'Ошибка', Value: 'Value' } } }];
  return p;
}
const stat = (title, unit, expr, description) => {
  const shortTitles = {
    'Application • Доступность': 'Приложение', 'Application • Uptime': 'Uptime',
    'HTTP • RPS': 'HTTP RPS', 'HTTP • Ошибки 5xx': 'Ошибки 5xx',
    'HTTP • p95': 'HTTP p95', 'HTTP • p99': 'HTTP p99', 'JVM • Heap': 'Heap JVM',
    'System • CPU процесса': 'CPU процесса', 'Database • Доступность': 'Статус SQLite',
    'SQL • Query Rate': 'SQL RPS', 'SQL • Ошибки': 'Ошибки SQL', 'SQL • p95': 'SQL p95',
    'VPN • Доступность': 'Статус VPN', 'VPN • Всего peers': 'Всего peers',
    'VPN • Online': unit === 'percentunit' ? 'Доля online' : 'Online peers', 'VPN • Offline': 'Offline peers',
    'Traffic • RX от устройств': 'RX от устройств', 'Traffic • TX устройствам': 'TX устройствам',
    'Peers • Без handshake': 'Без handshake'
  };
  return panel(shortTitles[title] ?? title, unit, [[expr, shortTitles[title] ?? title]], 'stat', description);
};
const graph = (title, unit, queries, description) => panel(title, unit, queries, 'timeseries', description);
const table = (title, unit, expr, description) => panel(title, unit, [[expr, '']], 'table', description);
function thresholds(p, warning, critical) {
  p.fieldConfig.defaults.thresholds.steps.push({ color: 'yellow', value: warning }, { color: 'red', value: critical }); return p;
}
function status(title, expr, description) {
  const p = stat(title, 'none', expr, description);
  p.fieldConfig.defaults.thresholds.steps = [{ color: 'red', value: null }, { color: 'green', value: 1 }];
  p.fieldConfig.defaults.mappings.push({ type: 'value', options: { 0: { text: 'DOWN', color: 'red' }, 1: { text: 'UP', color: 'green' } } });
  return p;
}
function row(title, children, collapsed = false, columns = 2, height = 8) {
  const r = { id: id++, type: 'row', title, collapsed, gridPos: { x: 0, y: y++, w: 24, h: 1 }, panels: [] };
  children.forEach((p, i) => { p.gridPos = { x: (i % columns) * (24 / columns), y: y + Math.floor(i / columns) * height, w: 24 / columns, h: height }; });
  panels.push(r);
  if (collapsed) r.panels = children; else { panels.push(...children); y += Math.ceil(children.length / columns) * height; }
}
function begin() { id = 1; y = 0; panels = []; }
function save(index, variables, description) {
  const dashboard = { uid: uids[index], title: titles[index], description, schemaVersion: 41, version: 1,
    editable: false, tags: ['amnezia', 'monitoring'], timezone: 'browser', refresh: '30s',
    time: { from: 'now-1h', to: 'now' }, timepicker: { refresh_intervals: ['30s', '1m', '5m'] },
    graphTooltip: 1, annotations: { list: [] }, templating: { list: [...baseVariables(), ...variables] },
    links: titles.map((title, i) => ({ title, type: 'link', url: `/d/${uids[i]}`, keepTime: true,
      includeVars: true, targetBlank: false, icon: 'dashboard', tooltip: title })), panels };
  writeFileSync(fileURLToPath(new URL(`./grafana/dashboards/${uids[index]}.json`, import.meta.url)), JSON.stringify(dashboard, null, 2) + '\n');
}

const httpLabels = 'uri=~"$uri",uri!~"/actuator.*"';
const httpCount = sum(rate('http_server_requests_seconds_count', httpLabels));
const httpError = `(${sum(rate('http_server_requests_seconds_count', httpLabels + ',status=~"5.."'))} or (${httpCount} * 0)) / (${httpCount} > 0)`;
const quantile = (name, q, labels = '', groups = '') => `histogram_quantile(${q}, sum by (le${groups ? ',' + groups : ''}) (${rate(name + '_bucket', labels, '5m')}))`;
const latencyNote = 'Histogram за 5 минут. При отсутствии наблюдений — нет данных; при малом числе запросов p95/p99 нестабильны. Actuator исключён.';
const heap = `${sum(m('jvm_memory_used_bytes', 'area="heap"'))} / (${sum(m('jvm_memory_max_bytes', 'area="heap"') + ' > 0')} > 0)`;
begin();
row('Overview', [status('Application • Доступность', up, 'Результат последнего scrape Prometheus; отсутствие target — нет данных.'),
  stat('Application • Uptime', 's', live(m('process_uptime_seconds'))),
  stat('HTTP • RPS', 'reqps', live(httpCount)), thresholds(stat('HTTP • Ошибки 5xx', 'percentunit', live(httpError), 'Нет запросов — нет доли ошибок.'), .01, .05),
  thresholds(stat('HTTP • p95', 's', live(quantile('http_server_requests_seconds', .95, httpLabels)), latencyNote), .5, 1),
  thresholds(stat('HTTP • p99', 's', live(quantile('http_server_requests_seconds', .99, httpLabels)), latencyNote), 1, 2),
  thresholds(stat('JVM • Heap', 'percentunit', live(heap)), .75, .9),
  thresholds(stat('System • CPU процесса', 'percentunit', live(m('process_cpu_usage'))), .7, .9)], false, 4, 4);
row('HTTP', [graph('HTTP • RPS по методам', 'reqps', [[live(`sum by (method) (${rate('http_server_requests_seconds_count', httpLabels)})`), '{{method}}']]),
  graph('HTTP • Статусы ответов', 'reqps', ['2','3','4','5'].map(n => [live(sum(rate('http_server_requests_seconds_count', httpLabels + `,status=~"${n}.."`))), `${n}xx`])),
  graph('HTTP • Latency', 's', [.5,.95,.99].map(q => [live(quantile('http_server_requests_seconds', q, httpLabels)), `p${q*100}`]), latencyNote),
  graph('HTTP • Доля 5xx', 'percentunit', [[live(httpError), '5xx']])]);
row('HTTP • Диагностика', [table('HTTP • Медленные маршруты p95', 's', live(`topk(10, ${quantile('http_server_requests_seconds', .95, httpLabels, 'uri,method')} > 0)`), latencyNote),
  table('HTTP • Популярные маршруты', 'reqps', live(`topk(10, sum by (uri,method) (${rate('http_server_requests_seconds_count', httpLabels)}))`)),
  table('HTTP • Ошибки по маршрутам', 'reqps', live(`topk(10, sum by (uri,method,status) (${rate('http_server_requests_seconds_count', httpLabels + ',status=~"4..|5.."')}) > 0)`)),
  stat('HTTP • Наблюдения за 5 минут', 'short', live(sum(`increase(${m('http_server_requests_seconds_count', httpLabels)}[5m])`)), latencyNote)], true);
row('JVM', [graph('JVM • Heap', 'bytes', ['used','committed','max'].map(n => [live(sum(m(`jvm_memory_${n}_bytes`, 'area="heap"') + (n === 'max' ? ' > 0' : ''))), n])),
  graph('JVM • Non-Heap по пулам', 'bytes', [[live(m('jvm_memory_used_bytes', 'area="nonheap"')), '{{id}}']]),
  graph('JVM • GC пауза', 's', [[live(`${sum(rate('jvm_gc_pause_seconds_sum'))} / (${sum(rate('jvm_gc_pause_seconds_count'))} > 0)`), 'Средняя'], [live(`max(${m('jvm_gc_pause_seconds_max')})`), 'Максимальная в окне Micrometer']]),
  graph('JVM • GC события', 'ops', [[live(`sum by (action,cause) (${rate('jvm_gc_pause_seconds_count')})`), '{{action}} • {{cause}}']]),
  graph('JVM • Потоки', 'short', [['live','Живые'],['daemon','Daemon'],['peak','Пик']].map(([n,l]) => [live(m(`jvm_threads_${n}_threads`)),l])),
  graph('JVM • Состояния потоков', 'short', [[live(m('jvm_threads_states_threads')), '{{state}}']]),
  graph('JVM • Классы', 'short', [[live(m('jvm_classes_loaded_classes')), 'Загружено']]),
  graph('JVM • Heap по пулам', 'bytes', [[live(m('jvm_memory_used_bytes', 'area="heap"')), '{{id}}']])], true);
row('System', [graph('System • CPU', 'percentunit', [[live(m('process_cpu_usage')), 'Процесс'], [live(m('system_cpu_usage')), 'Система']]),
  graph('System • Память процесса', 'bytes', [[live(m('process_memory_rss_bytes')), 'RSS'], [live(sum(m('jvm_buffer_memory_used_bytes'))), 'Буферы JVM']], 'RSS доступен на Linux /proc. Буферы входят в память процесса: серии не складывать.'),
  graph('System • File descriptors', 'short', [[live(m('process_files_open_files')), 'Открыто'], [live(m('process_files_max_files')), 'Лимит']], 'Зависит от поддержки ОС.')], true);
save(0, [variable('uri', 'HTTP URI', `label_values(${m('http_server_requests_seconds_count')}, uri)`, true)], 'Состояние Java-приложения → HTTP → диагностика JVM и системы. Выбран один instance. HTTP-фильтр не влияет на JVM.');

const sqlRate = sum(rate('sqlite_query_seconds_count'));
const sqlErrors = `${sum(rate('sqlite_query_seconds_count', 'error!="none"'))} or (${sqlRate} * 0)`;
const dbFresh = `${m('sqlite_database_checked_timestamp_seconds')} > time() - 120`;
begin();
row('Overview', [status('Database • Доступность', live(`${m('sqlite_database_up')} and on() (${dbFresh})`), 'Результат чтения SQLite раз в 30 секунд. Старше 120 секунд — нет данных; это не проверка возможности записи.'),
  stat('SQL • Query Rate', 'ops', live(sqlRate)), thresholds(stat('SQL • Ошибки', 'ops', live(sqlErrors)), .01, 1),
  thresholds(stat('SQL • p95', 's', live(quantile('sqlite_query_seconds', .95))), .1, 1)], false, 4, 4);
row('SQL Performance', [graph('SQL • Операции', 'ops', [[live(`sum by (operation) (${rate('sqlite_query_seconds_count')})`), '{{operation}}']]),
  graph('SQL • Latency', 's', [.5,.95,.99].map(q => [live(quantile('sqlite_query_seconds', q)), `p${q*100}`]), '5 минут, включая ожидание соединения и mapping. Нет операций — нет latency.'),
  table('SQL • Медленные категории p95', 's', live(quantile('sqlite_query_seconds', .95, '', 'operation') + ' > 0')),
  stat('SQL • Наблюдения за 5 минут', 'short', live(sum(`increase(${m('sqlite_query_seconds_count')}[5m])`))) ]);
row('SQL Errors', [graph('SQL • Типы ошибок', 'ops', [[live(`sum by (error) (${rate('sqlite_query_seconds_count', 'error!="none"')})`), '{{error}}']], 'Одна ошибка на JDBC-вызов. none/busy/locked/constraint/sql/other — ограниченный набор категорий.'),
  graph('SQL • Блокировки SQLite', 'ops', [[live(`${sum(rate('sqlite_query_seconds_count', 'error=~"busy|locked"'))} or (${sqlRate} * 0)`), 'BUSY / LOCKED']]),
  graph('Database • Ошибки на уровне приложения', 'ops', [[live(sum(rate('db_errors_total'))), 'Ошибки БД']], 'Существующий счётчик ошибок обработчиков и фоновых задач; может учитывать одно исключение несколько раз. Не складывать с SQL errors.'),
  graph('Database • Тайм-ауты соединений', 'ops', [[live(sum(rate('hikaricp_connections_timeout_total'))), 'Тайм-ауты']])], true);
row('Storage', [graph('Storage • Выделенные страницы SQLite', 'bytes', [[live(m('sqlite_database_allocated_bytes') + ` and on() (${dbFresh})`), 'SQLite']], 'page_count × page_size. Не включает отдельные WAL/SHM-файлы; это не суммарный размер файлов на диске.'),
  graph('Storage • Таблицы и индексы', 'bytes', [[live(m('sqlite_table_allocated_bytes') + ` and on() (${dbFresh})`), '{{table}} • allocated'], [live(m('sqlite_table_payload_bytes') + ` and on() (${dbFresh})`), '{{table}} • payload']])], true);
row('SQL Performance • Соединения', [graph('Database • Пул SQLite', 'short', ['active','idle','max','pending'].map(n => [live(m(`hikaricp_connections_${n}`)), n])),
  graph('Database • Ожидание и удержание соединения', 's', ['acquire','usage'].map(n => [live(`${sum(rate(`hikaricp_connections_${n}_seconds_sum`))} / (${sum(rate(`hikaricp_connections_${n}_seconds_count`))} > 0)`), n]))], true);
save(1, [], 'SQLite: доступность → производительность JDBC → ошибки и хранение. SQL labels не содержат текст запросов или параметры.');

const peerLabels = 'peer_id=~"$peer"';
const peer = name => m(name, peerLabels);
const traffic = direction => peer(`vpn_peer_${direction}_bytes`);
const speed = direction => `((${traffic(direction)} - ${traffic(direction)} offset 1m) >= 0) / 60`;
const trafficNote = 'Gauge со снимком накопленных байтов VPN-сервера. Скорость — оценка разности за 1 минуту / 60; отрицательная разность после сброса скрывается. Сброс с быстрым ростом между снимками не обнаружим. Точный трафик за период недоступен без изменения метрик. Только устройства, известные панели.';
const totalPeers = m('vpn_peers_total');
const activePeers = m('vpn_peers_active');
const handshake = peer('vpn_peer_last_handshake_timestamp');
const peerLegend = '{{user_name}} / {{peer_name}} • {{peer_id}}';
begin();
row('Overview', [status('VPN • Доступность', live(m('vpn_server_up')), 'Последняя проверка SSH/VPN. Отсутствие handshake отдельного устройства не означает DOWN сервера.'),
  stat('VPN • Всего peers', 'short', vpn(totalPeers)), stat('VPN • Online', 'short', vpn(activePeers)),
  stat('VPN • Offline', 'short', vpn(`${totalPeers} - ${activePeers}`)),
  stat('VPN • Online', 'percentunit', vpn(`${activePeers} / (${totalPeers} > 0)`)),
  stat('Traffic • RX от устройств', 'bytes', vpn(sum(traffic('rx'))), trafficNote),
  stat('Traffic • TX устройствам', 'bytes', vpn(sum(traffic('tx'))), trafficNote),
  stat('Peers • Без handshake', 'short', vpn(`count(${handshake} == 0) or (count(${handshake} == ${handshake}) * 0)`), '0 timestamp означает, что handshake отсутствует. Фильтр Peer применяется к трафику и таблицам; сводные peers всегда относятся ко всему серверу.')], false, 4, 4);
row('Traffic', [graph('Traffic • Скорость RX/TX (оценка)', 'Bps', [[vpn(sum(speed('rx'))), 'RX • устройства → сервер'], [vpn(sum(speed('tx'))), 'TX • сервер → устройства']], trafficNote),
  graph('Traffic • Снимок накопленных байтов', 'bytes', [[vpn(sum(traffic('rx'))), 'RX • устройства → сервер'], [vpn(sum(traffic('tx'))), 'TX • сервер → устройства']], trafficNote)]);
row('Traffic • Диагностика устройств', [table('Traffic • Top 10 RX (оценка)', 'Bps', vpn(`topk(10, ${speed('rx')})`), trafficNote),
  table('Traffic • Top 10 TX (оценка)', 'Bps', vpn(`topk(10, ${speed('tx')})`), trafficNote),
  graph('Traffic • RX по устройствам', 'Bps', [[vpn(`topk(10, ${speed('rx')})`), peerLegend]], trafficNote),
  graph('Traffic • TX по устройствам', 'Bps', [[vpn(`topk(10, ${speed('tx')})`), peerLegend]], trafficNote)], true);
row('Peers', [graph('Peers • Online / Offline', 'short', [[vpn(activePeers), 'Online'], [vpn(`${totalPeers} - ${activePeers}`), 'Offline']]),
  table('Peers • Возраст handshake', 's', vpn(`time() - (${handshake} > 0)`), 'Только ненулевые timestamps. Online определяется backend по HANDSHAKE_THRESHOLD, по умолчанию 3 минуты.'),
  table('Peers • Без недавнего handshake', 's', vpn(`(time() - (${handshake} > 0)) > $handshake_seconds`), 'Диагностический порог ниже следует согласовать с HANDSHAKE_THRESHOLD backend.'),
  table('Peers • Handshake отсутствует', 'none', vpn(`${handshake} == 0`)),
  table('Peers • Накопленный RX', 'bytes', vpn(traffic('rx')), trafficNote),
  table('Peers • Накопленный TX', 'bytes', vpn(traffic('tx')), trafficNote)], true);
row('Server Health', [graph('VPN • История доступности', 'none', [[live(m('vpn_server_up')), 'VPN']]),
  graph('VPN • Ошибки интеграции', 'ops', ['ssh_errors_total','vpn_apply_errors_total','vpn_rollback_errors_total'].map(n => [live(sum(rate(n))), n.replace('_total','')]), 'SSH включает сбор статистики и другие SSH-команды. Отдельного счётчика ошибок сбора нет. Отсутствующий rollback counter означает нет данных, а не подтверждённый ноль.')], true);
save(2, [variable('peer', 'Peer', `label_values(${m('vpn_peer_last_handshake_timestamp')}, peer_id)`, true),
  { name: 'handshake_seconds', label: 'Handshake threshold (s)', type: 'custom', query: '180,300,600,900', current: { text: '180', value: '180' }, options: ['180','300','600','900'].map(v => ({ text: v, value: v, selected: v === '180' })), multi: false, includeAll: false }],
  'AmneziaWG: состояние → нагрузка → устройства и диагностика SSH. RX/TX относительно сервера. Семантика существующих VPN-метрик сохранена.');
