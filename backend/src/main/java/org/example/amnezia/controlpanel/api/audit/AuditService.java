package org.example.amnezia.controlpanel.api.audit;

import java.time.Clock;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.repository.AuditRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditRepository repository;
  private final Clock clock;

  public AuditService(AuditRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public void record(
      AuditEvent.EventType event,
      AuditEvent.Result result,
      UUID actor,
      String objectType,
      UUID objectId) {
    repository.append(
        new AuditEvent(
            UUID.randomUUID(),
            event,
            result,
            actor,
            objectType,
            objectId,
            clock.instant(),
            MDC.get("correlation_id")));
  }
}
