package org.example.amnezia.controlpanel.api.mapper;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class ApiValues {
  public OffsetDateTime toOffset(Instant value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  public Instant toInstant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  public URI toUri(String value) {
    return value == null ? null : URI.create(value);
  }
}
