package org.example.amnezia.controlpanel.api.persistence.query;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Loading this bean validates every immutable SQL resource during application startup. */
@Component
public final class QueryCatalog {
  private final List<QueryHolder> queries = all();

  public static List<QueryHolder> all() {
    return Stream.of(
            UserQuery.values(),
            InvitationQuery.values(),
            DeviceQuery.values(),
            ConfigLinkQuery.values(),
            RefreshSessionQuery.values(),
            AuditQuery.values(),
            DatabaseMetricQuery.values())
        .flatMap(Arrays::stream)
        .map(query -> (QueryHolder) query)
        .toList();
  }

  public List<QueryHolder> queries() {
    return queries;
  }
}
