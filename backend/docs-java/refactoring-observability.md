# Observability refactoring baseline

Baseline commit: `acead04e03d9763df006dc27538f0d9c540f42df`. Source audit and read-only Prometheus check; no production changes. All 36 dashboard queries executed successfully; scrape target UP. Runtime sample values and label values intentionally omitted.

## Protected contract

VPN and SQLite/database metrics are frozen: names, types, label keys/values, registration, collection, calculations, semantics, units, PromQL, Grafana panels and thresholds. Preserve `vpn_peers_total` as a gauge, including MetricsConfig naming override and Prometheus client 1.8.0 build override.

Core: VpnMonitor, PeerRuntime, DatabaseStorageMetrics, DatabaseMetricRepository/Query, MetricPeer/TableStorage mappers and models, database/table storage SQL and device/find-metric-peers SQL. Shared areas: DeviceRepository.metricPeers, DeviceQuery, RowValues, SqlResourceLoader; SSH/runtime collection; error counter sites; datasource/Hikari properties; scrape security and infrastructure. Shared code changes require targeted semantic review even when unrelated methods are refactored.

Grafana uses v2beta1 TabsLayout. Protected panel IDs: **1–6,10,16–24**, including whole panel definitions and related rows/layout. Application HTTP/JVM panels 7–9,11–15 are separate. Existing tabs and default collapsed sections remain. No tracked alerts/recording rules exist.

Build/catalog baseline hashes may change for test-only dependencies/tasks; such differences must be explicitly reviewed. Do not change runtime metric dependency versions or registrations.

## Sanitized exported names/types/label keys

Types refer to the Prometheus metric family; `_count`/`_sum` are summary samples. Common application/job/instance labels are included, without values.

| Exported name | Family type | Label keys |
|---|---|---|
| `db_errors_total` | counter | application, instance, job |
| `hikaricp_connections` | gauge | application, instance, job, pool |
| `hikaricp_connections_acquire_seconds_count` | summary | application, instance, job, pool |
| `hikaricp_connections_acquire_seconds_max` | gauge | application, instance, job, pool |
| `hikaricp_connections_acquire_seconds_sum` | summary | application, instance, job, pool |
| `hikaricp_connections_active` | gauge | application, instance, job, pool |
| `hikaricp_connections_creation_seconds_count` | summary | application, instance, job, pool |
| `hikaricp_connections_creation_seconds_max` | gauge | application, instance, job, pool |
| `hikaricp_connections_creation_seconds_sum` | summary | application, instance, job, pool |
| `hikaricp_connections_idle` | gauge | application, instance, job, pool |
| `hikaricp_connections_max` | gauge | application, instance, job, pool |
| `hikaricp_connections_min` | gauge | application, instance, job, pool |
| `hikaricp_connections_pending` | gauge | application, instance, job, pool |
| `hikaricp_connections_timeout_total` | counter | application, instance, job, pool |
| `hikaricp_connections_usage_seconds_count` | summary | application, instance, job, pool |
| `hikaricp_connections_usage_seconds_max` | gauge | application, instance, job, pool |
| `hikaricp_connections_usage_seconds_sum` | summary | application, instance, job, pool |
| `sqlite_database_allocated_bytes` | gauge | application, instance, job |
| `sqlite_table_allocated_bytes` | gauge | application, instance, job, table |
| `sqlite_table_payload_bytes` | gauge | application, instance, job, table |
| `ssh_errors_total` | counter | application, instance, job |
| `vpn_apply_errors_total` | counter | application, instance, job |
| `vpn_peer_last_handshake_timestamp` | gauge | application, instance, job, peer_id, peer_name, user_name |
| `vpn_peer_rx_bytes` | gauge | application, instance, job, peer_id, peer_name, user_name |
| `vpn_peer_tx_bytes` | gauge | application, instance, job, peer_id, peer_name, user_name |
| `vpn_peers_active` | gauge | application, instance, job |
| `vpn_peers_total` | gauge | application, instance, job |
| `vpn_server_up` | gauge | application, instance, job |

`vpn_rollback_errors_total` is a lazy counter with the common labels; absent at baseline until the first failed compensation. Future presence alone is not a regression. No jdbc_* series exported at baseline; Hikari is the active datasource metric contract.

## Semantics

Server gauges describe successful runtime polling and peer counts (including unmanaged peers). Peer gauges expose epoch-second last handshake and absolute RX/TX bytes with peer_id, peer_name and admin-set user_name; unknown runtime => NaN, absent peer => 0. Active means a nonfuture handshake younger than configured threshold. Renaming/deleting a device or user reconciles labels/meters. Storage gauges measure main-file pages including free pages, and table+index pages/payload via dbstat; WAL/journal excluded. Table labels disappear after table deletion. Do not compare literal changing runtime values as regression criteria.

## Manual review findings

All remain **PROTECTED METRIC — MANUAL REVIEW REQUIRED**, with no implementation changes:

- **MEDIUM:** db_errors can increment in VpnMutation and again in WebExceptionHandler for the same escaping DataAccessException; mutation path also counts any persistence callback RuntimeException. Deduplication/classification would change semantics.
- **MEDIUM:** overall VPN health synchronously performs SSH/DB reconciliation; SSH timeout may delay health. Liveness uses a separate path. Caching would change collection timing.
- **LOW:** per-device UUID/name/user labels create expected cardinality/churn; these labels are required by the existing dashboard.
- **LOW:** DB timeout/error rate panels use unit short rather than ops; descriptions correctly explain frequency, but units are protected.

Other audit conclusions: standard normalized HTTP metrics and mean latency are coherent; no custom raw dynamic URL tags found. No Testcontainers existed in baseline (real temporary SQLite component tests remain). Unused catalog aliases are declarations, not installed dependencies; actual Commons IO, ZXing, MapStruct, Lombok and Linux JVM RSS binder are used. No new observability stack is proposed.

## SHA256 file baseline

Full-file hashes are a safety signal. Core must match; shared-file differences require review of protected regions. These are raw workspace bytes at baseline, so line-ending-only changes also register.

| File | SHA256 |
|---|---|
| `backend/build.gradle.kts` | `1b7c40455fc2b5d16484287e13fee964bb88f8e0c59fa9c1ccbd79eb93703dc9` |
| `backend/gradle/libs.versions.toml` | `90ec241f88550bb3c32a14ceaa89f7131a8721d4b3dc59024e71785bfbf490cc` |
| `backend/monitoring/grafana/dashboards/amnezia-control-panel.json` | `df3203aada03063c25ac2ff4a1c991ff4e7aaade7bbe506bcba1e5e67c68af7a` |
| `backend/monitoring/grafana/provisioning/dashboards/dashboards.yml` | `75391406f29f40a7229360547f7ed295e0cb01842baf42c1c088f780d152fde3` |
| `backend/monitoring/grafana/provisioning/datasources/prometheus.yml` | `89bf8cf1590042f4c6b2880125e2c626e55022b4a9c888df8b594f436ce9d94f` |
| `backend/monitoring/prometheus/prometheus.yml` | `037b5ad51870ff1486a0dae1932265f65690654af47aebb172bf65005c4b7499` |
| `backend/monitoring/prometheus/start.sh` | `9dbf5b9041256bf035ccbaaebfa4198aa3210960e55ef9a964cee869a20693ef` |
| `backend/scripts/smoke-compose.ps1` | `3ec73e86b97f5fae8c451eef044322f67f91d056f061ed9e90ab5afc99053a03` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/config/AppProperties.java` | `cce9d1f79c1600938a00dc2eb664e7547ae0e5b29a301f000b2ec4e9ec449e71` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/config/db/DatabasePins.java` | `d711444183395273536ceb2dbbe6ca82b919c055e047913a8895406ea37deb94` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/config/db/SqliteConfig.java` | `577091ff1ce682e45e1a0b4c8c8b955648a3b876b9121efda45bb87af34914ae` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/config/MetricsConfig.java` | `da0d6a62a5f2e3d11e2ce8bd41b54bfd61c8f4a404eeb2ae9c198ae2e52dc57f` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/config/MetricsProperties.java` | `32a4adf373179b3c0982fa6a8b2d99cfdf7e13c6b3c682b545040f8484aba8f6` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/monitoring/DatabaseStorageMetrics.java` | `f0deb3d787cd243f1dd6f5ae9c98bce400aa64f5df2920fbcb9f56ff1335290c` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/mapper/MetricPeerRowMapper.java` | `cf447dfd47235d51de498b8c19c010421fd15addc8e13e11dbdb96b4ab5f11a5` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/mapper/RowValues.java` | `ff4c10fd12683f8143ee900e2783a2614ba8a29041de8b7eafeaa124103561e7` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/mapper/TableStorageRowMapper.java` | `474baca4055df7e4f7809fe93bc3f21ba16f78a4abf8d3d93148c5299279d81b` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/model/DatabaseStorage.java` | `780cb37a30bc98a135808d6b102282d417380a1f40daf0086ad259ebc3dbb39c` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/model/MetricPeer.java` | `4665b649d8a4eec5f8c1e5537943463e495ac654eb84c5b65bf17e867cdc33be` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/model/TableStorage.java` | `0db6a79c97c1191468951167a65bd59651e8b26c7b97b0f0dacf9f3059cea1c3` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/query/DatabaseMetricQuery.java` | `b169f5a9a125cd718ae2f7c5ceb51484f05bf61be1704a9d9b42e463430ef22f` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/query/DeviceQuery.java` | `5c0bd47fb35a71b34e79046459f7174d4e3053933f8dc019298b9765ad3f96f5` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/query/SqlResourceLoader.java` | `3b2e9b94a71adba7ca07819c9659258d5be544546b3cb5d1066a14e91752a1c0` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/repository/DatabaseMetricRepository.java` | `f6bf9f36a19b47ba9c11088e9bc4ea1a4125cb10cf3041f191445701a1bff8af` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/persistence/repository/DeviceRepository.java` | `7c7cd4e2c6523ae8305804d22e3f19b2192db595cc6006bc56e5665f5dc020e2` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/security/SecurityConfig.java` | `c80b709adde2d7bd8c009042ce7120105ac0ca161823bf2cec8586768c6b87c3` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/ssh/OpenSshExecutor.java` | `812d0a9fc761208829214606e283fd888c1aa68457e95a54668ac9d84af13e6e` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/vpn/PeerRuntime.java` | `d3f88a2b132730584d76bcc10286884188391ba3f5d5bfa12834c46831009e3c` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/vpn/SshVpnGateway.java` | `e132707e762144363cf52e6e4d721aebe81d8ec79d99626022d885fdd8707d4e` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/vpn/VpnMonitor.java` | `4fafcb9a542f574a66333c7380e5d89a8661e0b123ef8888cf8efeb155d21132` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/vpn/VpnMutation.java` | `9eef3d08b5a2888eeca41b6ae4d0dbf667bf0b7f01c83b2182242b3bd6577546` |
| `backend/src/main/java/org/example/amnezia/controlpanel/api/web/WebExceptionHandler.java` | `eae23099e9747532a8963ec71219a7e2dd9e40e31df516f6e9ef7aafac1ef146` |
| `backend/src/main/resources/application.yml` | `6014d721211e016a901957d7b405cae65364ce61b7de2c147e173a1b031c079e` |
| `backend/src/main/resources/sql/device/find-metric-peers.sql` | `451f86ca3cf23f6d83a1a8ded4fe3bf8033b86995831b7b5ae969010a1e07383` |
| `backend/src/main/resources/sql/metrics/database-storage.sql` | `7c4ddd19bb17f3186d50f33c9c40dd2890469ef6199f6adb8690253130bb763c` |
| `backend/src/main/resources/sql/metrics/table-storage.sql` | `e942e131e27f45563d00fc8fc5d05300b83c7c250859c27e1a2843bb80b06e88` |
| `docker-compose.yml` | `e2306c02a74d7030e6bd4aa8d41d0660a4e7768f60c077c0030cc706c57152e6` |

## Verification after refactoring

Run relevant real-SQLite, peer lifecycle and metric contract tests, clean build, isolated Compose smoke when Docker is available, and compare the source/dashboard snapshot with live metric names/types/label-key sets. Runtime values require controlled behavior tests. Review lazy metrics and temporary staleness without hiding genuine missing series. All credentials/configs remain excluded from output.
