# Backend development

Use the local `backend/agent-skills/java-spring-best-practices/SKILL.md` for all subsequent Java/Spring implementation and refactoring, as requested by the user.

Project requirements take precedence over generic examples in that skill:
- Java 25, Spring Boot 4.1, SQLite with JDBC and Liquibase; no JPA/Hibernate.
- Constructor injection, final dependencies, explicit imports, immutable internal records.
- Persistence belongs under `persistence/{model,repository,query,mapper}`. Repositories use the shared qualified `NamedParameterJdbcTemplate`, named parameters and classpath SQL resources.
- Generated OpenAPI interfaces own route annotations. Generated models stay at the API boundary and are mapped with MapStruct; do not edit generated files.
- Preserve the OpenAPI error envelope and existing behavior. Never log credentials, tokens, private keys or VPN configuration.
- Use real temporary SQLite in integration tests. Fake VPN gateways belong only in test sources.

Before finishing backend changes, run the relevant tests and a clean build. For monitoring changes, validate Compose and verify provisioning/scraping when Docker is available.
