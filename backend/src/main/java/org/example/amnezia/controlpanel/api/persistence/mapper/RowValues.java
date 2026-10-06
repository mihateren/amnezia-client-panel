package org.example.amnezia.controlpanel.api.persistence.mapper;

import java.time.Instant;
import java.util.UUID;

final class RowValues {
  private RowValues() {}

  static UUID uuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }

  static Instant instant(String value) {
    return value == null ? null : Instant.parse(value);
  }
}
