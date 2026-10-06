package org.example.amnezia.controlpanel.api.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AuditActor;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AuditObject;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AuditResponse;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.AuditRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {
  private final AuditRepository audits;
  private final UserRepository users;
  private final AuthService auth;

  public AuditQueryService(AuditRepository audits, UserRepository users, AuthService auth) {
    this.audits = audits;
    this.users = users;
    this.auth = auth;
  }

  public PageResponse<AuditResponse> list(
      UUID actor,
      int page,
      int size,
      AuditEvent.EventType type,
      UUID userId,
      AuditEvent.Result result,
      Instant from,
      Instant to) {
    if (auth.active(actor).role() != User.Role.ADMIN) throw ApiException.unauthorized();
    PageResponse.validate(page, size);
    if (from != null && to != null && from.isAfter(to)) throw ApiException.invalid();
    var items = audits.list(page, size, type, userId, result, from, to);
    var actors = new HashMap<UUID, Optional<String>>();
    return PageResponse.of(
        items.content().stream()
            .map(
                e ->
                    new AuditResponse(
                        e.id(),
                        e.createdAt(),
                        e.eventType(),
                        e.result(),
                        new AuditActor(
                            e.actorUserId(),
                            e.actorUserId() == null
                                ? null
                                : actors
                                    .computeIfAbsent(
                                        e.actorUserId(), id -> users.find(id).map(User::nickname))
                                    .orElse(null)),
                        new AuditObject(e.objectType(), e.objectId()),
                        e.correlationId()))
            .toList(),
        page,
        size,
        items.totalElements());
  }
}
