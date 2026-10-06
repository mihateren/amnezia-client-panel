package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.dto.AdminDtos;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.rest.model.AuditEventType;
import org.example.amnezia.controlpanel.api.rest.model.AuditPage;
import org.example.amnezia.controlpanel.api.rest.model.AuditResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = ApiMapperConfig.class)
public interface AuditApiMapper {
  @Mapping(target = "_object", source = "object")
  org.example.amnezia.controlpanel.api.rest.model.AuditEvent event(AdminDtos.AuditResponse result);

  AuditPage page(PageResponse<AdminDtos.AuditResponse> result);

  AuditEvent.EventType type(AuditEventType type);

  AuditEvent.Result result(AuditResult result);
}
