package org.example.amnezia.controlpanel.api.controller;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.mapper.ApiValues;
import org.example.amnezia.controlpanel.api.mapper.AuditApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.AuditApi;
import org.example.amnezia.controlpanel.api.rest.model.AuditEventType;
import org.example.amnezia.controlpanel.api.rest.model.AuditPage;
import org.example.amnezia.controlpanel.api.rest.model.AuditResult;
import org.example.amnezia.controlpanel.api.service.AuditQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditController implements AuditApi {
  private final AuditQueryService service;
  private final AuditApiMapper mapper;
  private final ApiValues values;
  private final ApiRequestContext context;

  public AuditController(
      AuditQueryService service,
      AuditApiMapper mapper,
      ApiValues values,
      ApiRequestContext context) {
    this.service = service;
    this.mapper = mapper;
    this.values = values;
    this.context = context;
  }

  @Override
  public ResponseEntity<AuditPage> getAuditLog(
      Integer page,
      Integer size,
      AuditEventType eventType,
      UUID userId,
      AuditResult result,
      OffsetDateTime from,
      OffsetDateTime to) {
    return ResponseEntity.ok(
        mapper.page(
            service.list(
                context.actor(),
                page,
                size,
                mapper.type(eventType),
                userId,
                mapper.result(result),
                values.toInstant(from),
                values.toInstant(to))));
  }
}
