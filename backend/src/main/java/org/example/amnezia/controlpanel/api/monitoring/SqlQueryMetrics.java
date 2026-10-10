package org.example.amnezia.controlpanel.api.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.SqlProvider;

/** Measures each JDBC operation once, including connection acquisition and result mapping. */
public final class SqlQueryMetrics {
  private static final Set<String> TYPES = Set.of("SELECT", "INSERT", "UPDATE", "DELETE");

  private SqlQueryMetrics() {}

  public static JdbcOperations instrument(JdbcOperations delegate, MeterRegistry registry) {
    return (JdbcOperations)
        Proxy.newProxyInstance(
            JdbcOperations.class.getClassLoader(),
            new Class<?>[] {JdbcOperations.class},
            (proxy, method, args) -> {
              String sql = null;
              if (args != null && args.length > 0) {
                if (args[0] instanceof SqlProvider provider) sql = provider.getSql();
                else if (args[0] instanceof String value) sql = value;
              }
              var sample = sql == null ? null : Timer.start(registry);
              var operation = sql == null ? "OTHER" : operation(sql);
              var error = "none";
              try {
                return method.invoke(delegate, args);
              } catch (InvocationTargetException exception) {
                error = error(exception.getCause());
                throw exception.getCause();
              } finally {
                if (sample != null) {
                  sample.stop(
                      Timer.builder("sqlite.query")
                          .description(
                              "JDBC operation including connection wait and result mapping")
                          .tags("operation", operation, "error", error)
                          .register(registry));
                }
              }
            });
  }

  private static String operation(String sql) {
    var first = sql.stripLeading().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
    return TYPES.contains(first) ? first : "OTHER";
  }

  private static String error(Throwable failure) {
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql) {
        return switch (sql.getErrorCode() & 0xff) {
          case 5 -> "busy";
          case 6 -> "locked";
          case 19 -> "constraint";
          default -> "sql";
        };
      }
    }
    return failure instanceof DataIntegrityViolationException ? "constraint" : "other";
  }
}
