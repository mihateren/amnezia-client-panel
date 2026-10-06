package org.example.amnezia.controlpanel.api.component;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * База для компонентных тестов: поднимает полный Spring-контекст на временной файловой SQLite,
 * чтобы Liquibase прогнал миграции при старте приложения.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(BaseComponentTest.FakeConfiguration.class)
@TestPropertySource(
    properties = {"spring.liquibase.change-log=classpath:/db-migrations/changelog.xml"})
public abstract class BaseComponentTest {
  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class FakeConfiguration {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    org.example.amnezia.controlpanel.api.vpn.FakeVpnGateway fakeVpn() {
      return new org.example.amnezia.controlpanel.api.vpn.FakeVpnGateway();
    }
  }

  @DynamicPropertySource
  static void secrets(DynamicPropertyRegistry registry) {
    registry.add("SQLITE_URL", () -> TEST_DB);
    registry.add("JWT_SECRET", BaseComponentTest::randomKey);
    registry.add("ENCRYPTION_KEY", BaseComponentTest::randomKey);
    registry.add("METRICS_TOKEN", () -> METRICS_TOKEN);
  }

  private static final String TEST_DB = temporaryDatabase();
  protected static final String METRICS_TOKEN =
      java.util.HexFormat.of().formatHex(java.security.SecureRandom.getSeed(32));

  private static String temporaryDatabase() {
    try {
      var file = java.nio.file.Files.createTempFile("amnezia-integration-", ".db");
      file.toFile().deleteOnExit();
      return "jdbc:sqlite:" + file.toAbsolutePath();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static String randomKey() {
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    return java.util.Base64.getEncoder().encodeToString(bytes);
  }
}
