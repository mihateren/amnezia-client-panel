# Backend refactoring

## Scope and baseline (before production changes)

Checkpoint: `acead04`. Working tree was clean. Backend is a single Gradle module;
frontend is a separate React application. Java 25, Spring Boot 4.1.0, Gradle Wrapper
9.7.1, SQLite JDBC 3.45.1.0, Liquibase 4.25.1, Commons IO 2.20.0, MapStruct 1.6.3.
JUnit 5.13.4 is enforced by the build. Existing API routes/models are generated
from OpenAPI; implementation maps boundary models with MapStruct.

`./backend/gradlew.bat -p backend clean build` passed: 47 tests, no failures,
errors or skipped tests. Component tests use real temporary SQLite files.
The compiler reported existing deprecated API usage; there was no compilation
failure. The Gradle problems report was also generated in the baseline build.
The running Compose application returned health `UP`; Prometheus scrape target
`amnezia-control-panel` was `up`. Existing Compose smoke covers the packaged
application, authentication, persistence across restart, monitoring and Grafana.
There were no Testcontainers dependencies or tests at this checkpoint, despite
the supplied plan describing them as existing. This discrepancy is recorded,
rather than treating the existing SQLite tests as container tests.

Applied guidance: local Java/Spring skill and installed clean-code,
java-development-skill, 311-frameworks-spring-jdbc, observing-spring-apps,
grafana-core/promql and grafana-core/dashboarding. Project requirements override
Maven, JdbcClient, JPA, ProblemDetail, tracing and schema-migration examples.

## Audit and priorities

| Priority | Location | Problem / proposed change | Risk |
| --- | --- | --- | --- |
| MEDIUM | Component tests and QueryHolderTest | Executed SQL and parser fixtures remain in Java. Move unchanged queries into test classpath resources, using the existing Commons IO loader and cached test query constants. | Low; retain every assertion and test scenario. |
| MEDIUM | Build and tests | Missing Testcontainers safety net. Add an explicit Docker-required task that runs real SQLite integration tests on Linux with the same driver/migrations/runtime dependencies. | Test infrastructure only; ordinary tests remain usable without Docker. |
| MEDIUM | AdminService.devices | Repeated owner lookup and device count for every device owned by the same user. Reuse owner views within one invocation. | Preserve authorization, response shape, candidate order, status filtering and pagination. No shared cache. |
| MEDIUM | AuditQueryService.list | Repeated actor lookup, including missing/deleted actors. Reuse lookup results within one page, including absence. | Preserve IDs, nullable nicknames, order and totals. No shared cache. |
| MEDIUM | ConfigService.qr | PNG rendering is mixed with configuration authorization, audit and token operations. Extract one concrete renderer without an interface. | Preserve QR content, 512px dimensions, UTF-8, PNG and error mapping. |
| LOW | AdminService.removeAccess | Complex persistence callback. Private extraction is possible. | Defer: current transaction/remote compensation is explicit and well tested. |
| LOW | FQCN/imports and version catalog | Some verbose type references and unused catalog entries. | Avoid broad cosmetic churn or dependency upgrades. |

No confirmed CRITICAL issue was found in this audit. The architecture already
has thin generated-route controllers, constructor injection, final dependencies,
immutable internal records, redacted sensitive DTOs, MapStruct and dedicated JDBC
repositories. No generic repository or new service hierarchy is warranted.

## JDBC, SQL, SQLite and transactions

Production SQL is already organized under `src/main/resources/sql`, loaded from
classpath through Commons IO using UTF-8 and closed streams. Query enums cache
the text; startup validates the catalog. Repositories share the qualified
NamedParameterJdbcTemplate and bind named values. No production SQL injection
was found. Optional lookup and COUNT handling are appropriate.

TransactionTemplate protects registration, refresh, single-use tokens and VPN
compensation. The shared fair application lock spans remote apply and DB commit;
SQLite uses one pooled connection and an explicit busy timeout. Removing that
lock or adding transaction annotations blindly would change existing behavior.
The schema deliberately has no foreign keys, as existing tests assert. Preserve
the schema and both existing Liquibase migrations.

## Separate bugs and risks (not silently fixed)

* **HIGH, potential, not reproduced:** OpenSshExecutor cleanup destroys the
  process after closing the executor. Interruption could leave close waiting on
  blocked process I/O. Requires a controlled-process regression test and a
  separate cleanup fix; no production change in this refactoring.
* **MEDIUM, confirmed SQLite experiment:** user/device/audit queries order TEXT
  `created_at` from variable-precision Instant strings. A whole-second timestamp
  sorts before a newer fractional timestamp in descending order. Fixing this
  requires an explicit precision/compatibility decision and separate regression
  tests; query semantics remain unchanged here.
* **MEDIUM, potential:** oversized chunked bodies may return 400 while bodies
  with known Content-Length return 413. Requires a real chunked HTTP test and a
  separate API-status decision.
* **MEDIUM, unmeasured:** login password verification holds the shared mutation
  lock. Moving it requires security and concurrency analysis, not just cleanup.

## Observability audit and protected boundary

Review covers HTTP/JVM metrics, HTTP route normalization and Grafana filtering,
histogram/rate aggregation, custom metrics, scrape authorization and dashboards.
Existing dashboard uses native v2beta1 tabs; preserve its schema. Do not add
redundant HTTP metrics, tracing infrastructure or panels merely for quantity.

VPN and database metrics are protected, including their transitive collection
dependencies, naming convention, Prometheus client override, datasource setup,
SQL, RowMappers, peer status calculation, dashboard panels, queries and alerts.
Only test fixture SQL may be moved, with unchanged query contents and metric
assertions. No metric production dependency is being refactored.

**PROTECTED METRIC — MANUAL REVIEW REQUIRED:** `db_errors` increments in both
VpnMutation and WebExceptionHandler. One SQL failure may be counted twice and a
non-SQL persistence callback failure may count as a DB error. Recommendation:
review counting ownership separately. Preserve the current contract here.

## Iterations and verification

The execution record below is updated after each independently verified block.
Final verification includes clean build, all host tests, Docker-required
Testcontainers tests, Compose smoke, API/migrations, classpath SQL and unchanged
protected sources/configuration. No public API, schema or security change is
intended.

### Block 1 — test SQL resources

Problem: executable SQL and SQL parser samples are embedded in Java tests.
Priority: MEDIUM. Location: component tests and QueryHolderTest.
Why: test SQL should follow the same classpath organization as production SQL.
Proposed change: cached test query enum using SqlResourceLoader; move statements
without changing clauses or assertions. Replace trusted dynamic PRAGMA with the
equivalent bound table-valued PRAGMA query.
Behavior affected: fixture setup and verification only; production is unchanged.
Risk: low. Tests protecting behavior: all 47 existing host tests.

### Block 2 — Linux SQLite Testcontainers safety net

Problem: the Testcontainers layer described in the plan is absent.
Priority: MEDIUM. Location: test-only launcher/container and Gradle test task.
Why: verify the real native SQLite driver, packaged resources, Liquibase,
repositories and transaction tests on the Linux runtime used by deployment.
Proposed change: separate explicit Docker-required task; copy current compiled
classes/resources and runtime dependencies into a disposable Java 25 container,
run existing JDBC/component tests with JUnit Launcher, require nonzero test count
and no skipped/failed tests. Preserve host tests.
Behavior affected: tests only. Risk: Docker/image/dependency availability.
Tests protecting behavior: same retained SQLite integration scenarios on Linux.

### Block 3 — repeated owner/actor lookups

Problem: identical DB lookups are repeated for every device/audit event.
Priority: MEDIUM. Location: AdminService.devices and AuditQueryService.list.
Why: demonstrably duplicated work makes list assembly harder to follow and
multiplies JDBC calls for owners/actors appearing repeatedly in one response.
Proposed change: invocation-local maps, including negative actor lookups; no
cross-request cache, SQL changes or transaction changes.
Behavior affected: preserve response content, order, pagination, status filtering,
authorization and nullable actor names. Risk: low, concurrent calls have separate
maps. Tests protecting behavior: add characterization tests first and run against
the original implementation, retain existing API tests and rerun after changes.

### Observability findings retained for manual review

**PROTECTED METRIC — MANUAL REVIEW REQUIRED:** aggregate health synchronously
polls SSH and DB while holding the refresh lock, which can delay health requests
by the SSH timeout. Existing liveness avoids this path. Changing sampling or
cache behavior would change the protected contract.

**PROTECTED METRIC — MANUAL REVIEW REQUIRED:** per-device UUID/name labels have
intentional cardinality and rename churn. Capacity planning is appropriate;
removing labels would break the required peer/user views.

**PROTECTED METRIC — MANUAL REVIEW REQUIRED:** DB rate panels 20/21 use `short`
units. Their descriptions correctly explain rates. Unit changes are protected.

All 36 existing dashboard PromQL queries were successfully executed against the
baseline Prometheus API; this establishes syntax, not nonempty data for every
panel. HTTP URI labels are normalized routes. Rate is applied before aggregation,
Grafana uses its rate interval, and the latency panel describes a mean accurately.
No alert rules are currently provisioned. Preserve that baseline.

### Verified results so far

Block 1 changed: 23 cached test query resources plus the intentional empty-resource
fixture under `src/test/resources/sql`. Production SQL loader and queries remain
untouched. Test-only PRAGMA now binds the trusted table name. Added four direct
real-SQLite cases for literal LIKE wildcards/nullable activity, one-shot config
links, invitation consumption/cleanup and owner-scoped refresh revoke/delete.
Why: separate test SQL and protect previously indirect repository behavior.
Tests executed: Gradle Wrapper `test` — **55 tests PASS**, including the four new
service characterization tests against unchanged service implementations.

Block 3 changed: request-local owner views and Optional actor-name results.
Why: eliminate identical lookups within one response, including negative lookups.
Tests executed after changes: Wrapper `test --tests '*service.AdminServiceTest'
--tests '*service.AuditQueryServiceTest' --tests '*component.ApiSecurityTest'` —
**13 cases PASS**. Tests prove filtering, ordering, pagination, response metadata,
one lookup per repeated owner/actor, and fresh names/counts on the next request.
Result: behavior retained; no shared cache, lock, security or metric changes.

Block 2 changed: test-scoped Testcontainers 2.0.5 (managed by the existing Spring
Boot BOM), JUnit Launcher, an explicit `testcontainersTest` task and a Linux suite
launcher. Main runtime dependencies and Prometheus override are unchanged.
Why: execute the retained real-SQLite integration suite on the deployment OS.
Tests executed: `clean build testcontainersTest` — **PASS**. Linux suite:
**41 found, 41 succeeded, 0 failed/aborted/skipped**. The host suite remains
Docker-independent; the explicit container task fails if Docker is unavailable.
Diagnostics contain numeric counts and failing class/method/exception type only,
without assertion messages, parameter values or raw application output.

## Final technical report

**Summary / Architecture:** preserve the existing layers and generated API
boundary. List assembly now shares repeated owner/actor lookup results within
one invocation. No new production interface, wrapper, generic repository,
service chain or framework was introduced.

**Java:** local typed HashMaps are bounded by the response candidates/page and
are discarded after the call. Actor Optional results also cache absence. Internal
records remain immutable. Four retained service tests cover repeated/missing/null
actors, repeated owners, ordering/filtering/pagination, totals and fresh values on
subsequent calls.

**Spring:** constructor injection/final dependencies remain. Controller routes,
generated models/interfaces, MapStruct mappings, validation, security, exception
envelope and configuration stay unchanged. No unnecessary bean or annotation
migration was performed.

**JDBC / SQL:** the existing qualified NamedParameterJdbcTemplate is retained;
identical owner/count and actor queries are no longer repeated within a list
operation. Production repositories/queries/loader stay unchanged. Full SQL is
absent from Java implementation and test sources. Test SQL is grouped by device,
security, repository, startup, parser and metric fixture under the test classpath;
TestQuery caches text loaded through the existing Commons IO loader. Metric
fixture query contents were checked byte-for-byte against the original literals.

**SQLite / Transactions:** real file-backed SQLite tests exercise migrations,
constraints, CRUD, nullable timestamps, literal LIKE escaping, conditional
single-use updates, owner-scoped revoke/delete, commit/rollback and concurrent
registration. No schema, migration, datasource setting, transaction boundary or
shared lock was changed. The chronological TEXT-ordering bug and potential SSH
interruption cleanup problem remain separate documented issues.

**Tests / Testcontainers:** all 47 original cases retained; eight meaningful
cases added (four repository and four service cases). Host total: **55**, consisting
of **17 unit/query tests and 38 component tests**. One container orchestration test
executes the **41 component/query cases again on Linux**, using the same compiled
classes, resources, dependencies, SQLite native driver and Liquibase. These are
not 41 additional distinct scenarios. Tests use fake VPN gateways only in test
sources and never connect to the production VPS.

**Observability:** HTTP/JVM/custom metrics, normalized URI labels, rate windows,
mean latency, datasource binding, authenticated scraping, Grafana tabs and all
36 PromQL queries reviewed. Application metrics added/removed/renamed: **none**.
Cardinality/PromQL/panel/alert changes: **none**. Existing monitoring already meets
the inspected application requirements; no extra stack, histogram or panel was
added without an operational need. Protected findings are listed above and in
[refactoring-observability.md](refactoring-observability.md).

**Remaining issues / deliberate boundaries:** the separately documented bugs and
protected metric findings are not silently fixed. AuthService remains cohesive;
its decomposition would add navigation. QR rendering remains a short local
operation with existing integration coverage; no single-use rendering wrapper
was introduced. The current explicit remote-apply/transaction compensation flow
is retained. Unused public legacy Result/Message/exception types and unused catalog
aliases were identified but retained rather than mixing unrelated compatibility
cleanup or dependency changes into the verified list/SQL refactoring.

### Protected metrics verification

Whole-file metric sources, transitive collector dependencies, production SQL,
datasource/security configuration, monitoring provisioning and dashboards match
the baseline hashes. The only reviewed shared-file difference is the Gradle
test-only dependencies/task. All protected panel objects and layout match.
28 live metric names/types/label-key contracts and all 36 dashboard queries were
checked. Time-dependent values are not expected to be numerically identical;
unchanged calculations and retained controlled lifecycle/storage tests protect
their semantics, labels and units.

```text
VPN metrics: UNCHANGED
VPN metric semantics: UNCHANGED
VPN labels: UNCHANGED
VPN PromQL: UNCHANGED
VPN Grafana panels: UNCHANGED
VPN alerts: UNCHANGED

Database metrics: UNCHANGED
Database metric semantics: UNCHANGED
Database labels: UNCHANGED
Database PromQL: UNCHANGED
Database Grafana panels: UNCHANGED
Database alerts: UNCHANGED
```

### Final verification

```text
Gradle build: PASS — ./backend/gradlew.bat -p backend clean build testcontainersTest
Unit tests: PASS — 17 host unit/query tests
Integration tests: PASS — 38 host component tests, real temporary SQLite
Testcontainers tests: PASS — 1 orchestration test, 41 Linux cases
SQL resources / Commons IO loading: PASS — host and Linux suites; no Java SQL literals
Public API / schema / migrations / security source comparison: UNCHANGED
Dependencies: runtime unchanged; only test-scoped Testcontainers/Launcher added
Diff whitespace / imports / new TODOs: PASS; no generated output edited
Application startup / Compose persistence / monitoring: PASS — isolated smoke-compose.ps1
Actuator/Prometheus: PASS — authenticated scrape, metric export and 36 dashboard queries
Grafana provisioning: PASS — datasource/dashboard/tabs; unchanged protected panels/layout
VPN metrics regression check: PASS — protected hashes, panel/layout objects, contracts/tests
Database metrics regression check: PASS — protected hashes, panel/layout objects, contracts/tests
```

Compose smoke built the current sources, verified frontend/API proxy,
Liquibase/bootstrap/JWT, database persistence after restart, SSH key permissions,
secret-free logs, authenticated scraping, VPN/HTTP/JVM/RSS metric export, Grafana
provisioning and every dashboard query. Its disposable project, containers and
volumes were cleaned up. The existing local panel/VPS was not migrated or
modified by this isolated test.

The final source comparison against `acead04` confirms controllers, API mappers,
security, configuration, VPN infrastructure, metric infrastructure, persistence,
production SQL, migrations, OpenAPI and Compose/provisioning are unchanged.
Only the two list service methods and test/build/documentation surfaces changed.
No new regression was found by the completed verification set.
