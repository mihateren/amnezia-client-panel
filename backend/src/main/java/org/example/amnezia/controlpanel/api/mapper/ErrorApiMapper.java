package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.rest.model.ErrorResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = ApiMapperConfig.class)
public interface ErrorApiMapper {
  @Mapping(target = "details", ignore = true)
  ErrorResponse response(org.example.amnezia.controlpanel.api.dto.ErrorResponse result);
}
