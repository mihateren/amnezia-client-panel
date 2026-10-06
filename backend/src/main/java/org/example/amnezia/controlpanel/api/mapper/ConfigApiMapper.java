package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.rest.model.ConfigLinkResponse;
import org.example.amnezia.controlpanel.api.rest.model.GetDeviceConfig200Response;
import org.example.amnezia.controlpanel.api.service.ConfigService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = ApiMapperConfig.class)
public interface ConfigApiMapper {
  ConfigLinkResponse link(ConfigService.LinkResponse result);

  @Mapping(target = "config", source = "config")
  @Mapping(target = "format", constant = "JSON")
  GetDeviceConfig200Response config(String config);
}
