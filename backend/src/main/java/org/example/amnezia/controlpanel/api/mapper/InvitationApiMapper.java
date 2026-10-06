package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.dto.ApiCommands;
import org.example.amnezia.controlpanel.api.rest.model.CreateInvitationRequest;
import org.example.amnezia.controlpanel.api.rest.model.InvitationResponse;
import org.example.amnezia.controlpanel.api.rest.model.InvitationValidationResponse;
import org.example.amnezia.controlpanel.api.service.InvitationService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = ApiMapperConfig.class)
public interface InvitationApiMapper {
  ApiCommands.CreateInvitation command(CreateInvitationRequest request);

  InvitationResponse response(InvitationService.Created result);

  @Mapping(target = "internalName", ignore = true)
  @Mapping(target = "valid", source = "valid")
  InvitationValidationResponse validation(Boolean valid);
}
