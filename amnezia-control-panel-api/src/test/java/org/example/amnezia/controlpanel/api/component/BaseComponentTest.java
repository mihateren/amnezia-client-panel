package org.example.amnezia.controlpanel.api.component;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * База для компонентных тестов: поднимает полный Spring-контекст на in-memory SQLite,
 * чтобы Liquibase прогнал миграции при старте приложения.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "SQLITE_URL=jdbc:sqlite:file:panel-it?mode=memory&cache=shared",
        "management.server.port=0",
        "spring.liquibase.change-log=classpath:/db-migrations/changelog.xml"
})
public abstract class BaseComponentTest {
}