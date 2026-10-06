package org.example.amnezia.controlpanel.api.container;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Reuses the actual migrated SQLite/JDBC suite on Linux without introducing another database. */
@Tag("testcontainers")
class SqliteLinuxTestcontainersTest {
  @TempDir Path temporary;

  @Test
  void shouldPassComponentAndQueryContractsOnLinuxSqlite() throws Exception {
    var hostClasspath = System.getProperty("panel.container.classpath");
    assertThat(hostClasspath)
        .as("testcontainersTest must supply its resolved runtime classpath")
        .isNotBlank();
    var linuxClasspath = new ArrayList<String>();
    try (var container = new GenericContainer<>(DockerImageName.parse("eclipse-temurin:25-jre"))) {
      int index = 0;
      for (var entry : hostClasspath.split(Pattern.quote(File.pathSeparator))) {
        var source = Path.of(entry);
        assertThat(source).as("resolved test runtime entry").exists();
        var target = "/workspace/classpath/" + index++ + (Files.isDirectory(source) ? "/" : ".jar");
        container.withCopyFileToContainer(MountableFile.forHostPath(source), target);
        linuxClasspath.add(target);
      }
      container.withCopyFileToContainer(
          MountableFile.forHostPath(System.getProperty("panel.container.openapi")),
          "/workspace/docs-java/openapi/");
      container.withCommand("sh", "-c", "echo SQLITE_TEST_RUNNER_READY; exec sleep 600");
      container.waitingFor(Wait.forLogMessage(".*SQLITE_TEST_RUNNER_READY.*", 1));
      container.withStartupTimeout(Duration.ofMinutes(2));
      // No Docker availability assumption: startup failure must fail this explicit verification
      // task.
      container.start();
      var command =
          "cd /workspace && exec java --enable-native-access=ALL-UNNAMED "
              + "--sun-misc-unsafe-memory-access=allow -Xshare:off -cp "
              + String.join(":", linuxClasspath)
              + " "
              + LinuxSqliteSuiteLauncher.class.getName();
      var execution = container.execInContainer("sh", "-c", command);
      var reportFile = temporary.resolve("linux-sqlite-summary.properties");
      container.copyFileFromContainer(
          "/workspace/linux-sqlite-summary.properties", reportFile.toString());
      var report = new Properties();
      try (var stream = Files.newInputStream(reportFile)) {
        report.load(stream);
      }
      // Never attach raw container stdout/stderr: failed assertions might contain generated keys.
      assertThat(execution.getExitCode()).as("Linux SQLite suite: %s", report).isZero();
      var found = Long.parseLong(report.getProperty("found"));
      assertThat(found).as("component/query suite must execute real tests").isGreaterThan(0);
      System.out.printf(
          "Linux SQLite/JDBC summary: found=%d, succeeded=%s, failed=%s, aborted=%s, skipped=%s%n",
          found,
          report.getProperty("succeeded"),
          report.getProperty("failed"),
          report.getProperty("aborted"),
          report.getProperty("skipped"));
      assertThat(Long.parseLong(report.getProperty("succeeded"))).isEqualTo(found);
      assertThat(Long.parseLong(report.getProperty("failed"))).isZero();
      assertThat(Long.parseLong(report.getProperty("aborted"))).isZero();
      assertThat(Long.parseLong(report.getProperty("skipped"))).isZero();
    }
  }
}
