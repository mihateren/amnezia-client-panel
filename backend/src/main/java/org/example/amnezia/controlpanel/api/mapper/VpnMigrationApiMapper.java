package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.dto.ApiCommands;
import org.example.amnezia.controlpanel.api.dto.VpnMigrationResponse;
import org.example.amnezia.controlpanel.api.rest.model.VpnMigrationRequest;
import org.example.amnezia.controlpanel.api.rest.model.VpnMigrationStatus;
import org.mapstruct.Mapper;

@Mapper(config = ApiMapperConfig.class)
public interface VpnMigrationApiMapper {
  ApiCommands.VpnMigration command(VpnMigrationRequest value);

  VpnMigrationStatus status(VpnMigrationResponse value);
}
