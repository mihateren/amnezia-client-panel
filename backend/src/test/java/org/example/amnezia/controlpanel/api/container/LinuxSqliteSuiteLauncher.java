package org.example.amnezia.controlpanel.api.container;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Container entry point; executes the retained suite, emitting only a safe numeric summary. */
public final class LinuxSqliteSuiteLauncher {
  private LinuxSqliteSuiteLauncher() {}

  public static void main(String[] arguments) throws Exception {
    var listener = new SummaryGeneratingListener();
    var report = new Properties();
    var consoleOut = System.out;
    var consoleError = System.err;
    boolean runnerFailed = false;
    // Keep generated credentials and assertions inside the test process, including on failure.
    try (var suppressed = new PrintStream(OutputStream.nullOutputStream())) {
      System.setOut(suppressed);
      System.setErr(suppressed);
      try {
        var request =
            LauncherDiscoveryRequestBuilder.request()
                .selectors(
                    selectPackage("org.example.amnezia.controlpanel.api.component"),
                    selectPackage("org.example.amnezia.controlpanel.api.persistence.query"))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
                .build();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
      } catch (RuntimeException | LinkageError failure) {
        runnerFailed = true;
        report.setProperty("runnerFailure", failure.getClass().getName());
      } finally {
        System.setOut(consoleOut);
        System.setErr(consoleError);
      }
    }
    var summary = listener.getSummary();
    report.setProperty("found", Long.toString(summary.getTestsFoundCount()));
    report.setProperty("succeeded", Long.toString(summary.getTestsSucceededCount()));
    report.setProperty("failed", Long.toString(summary.getTestsFailedCount()));
    report.setProperty("aborted", Long.toString(summary.getTestsAbortedCount()));
    report.setProperty("skipped", Long.toString(summary.getTestsSkippedCount()));
    int failureIndex = 0;
    for (var failure : summary.getFailures()) {
      // MethodSource excludes parameter values that a future display name could expose.
      var source = failure.getTestIdentifier().getSource().orElse(null);
      var location =
          source instanceof MethodSource method
              ? method.getClassName() + "." + method.getMethodName()
              : "container-or-engine";
      report.setProperty(
          "failure." + failureIndex++,
          location + ": " + failure.getException().getClass().getName());
    }
    try (var stream = Files.newOutputStream(Path.of("linux-sqlite-summary.properties"))) {
      report.store(stream, "Linux SQLite/JDBC test summary; no configuration or credentials");
    }
    consoleOut.println("Linux SQLite test counts: " + report);
    System.exit(
        !runnerFailed
                && summary.getTestsFoundCount() > 0
                && summary.getTestsSucceededCount() == summary.getTestsFoundCount()
                && summary.getTotalFailureCount() == 0
            ? 0
            : 1);
  }
}
