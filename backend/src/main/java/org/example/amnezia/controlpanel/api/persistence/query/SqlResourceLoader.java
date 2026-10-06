package org.example.amnezia.controlpanel.api.persistence.query;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

public final class SqlResourceLoader {
  private SqlResourceLoader() {}

  public static String load(String path) {
    try (var stream = new ClassPathResource(path).getInputStream()) {
      var sql = IOUtils.toString(stream, StandardCharsets.UTF_8);
      if (sql.isBlank()) throw new IllegalStateException("Empty SQL resource: " + path);
      return sql;
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load SQL resource: " + path, exception);
    }
  }
}
