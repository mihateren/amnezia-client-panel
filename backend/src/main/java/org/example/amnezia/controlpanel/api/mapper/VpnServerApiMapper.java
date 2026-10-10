package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.dto.VpnServerDtos;
import org.example.amnezia.controlpanel.api.rest.model.UpdateVpnServerRequest;
import org.example.amnezia.controlpanel.api.rest.model.VpnServerSettingsResponse;
import org.mapstruct.Mapper;

@Mapper(config = ApiMapperConfig.class)
public interface VpnServerApiMapper {
  VpnServerDtos.Update command(UpdateVpnServerRequest value);

  VpnServerSettingsResponse settings(VpnServerDtos.Settings value);
}
