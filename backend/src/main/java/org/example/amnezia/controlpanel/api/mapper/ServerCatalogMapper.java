package org.example.amnezia.controlpanel.api.mapper;

import java.util.List;
import org.example.amnezia.controlpanel.api.dto.ManagedServerDtos;
import org.example.amnezia.controlpanel.api.rest.model.CreateVpnServer;
import org.example.amnezia.controlpanel.api.rest.model.ManagedVpnServer;
import org.example.amnezia.controlpanel.api.rest.model.PublicVpnServer;
import org.example.amnezia.controlpanel.api.rest.model.UpdateManagedVpnServer;
import org.mapstruct.Mapper;

@Mapper(config = ApiMapperConfig.class)
public interface ServerCatalogMapper {
  ManagedServerDtos.Create command(CreateVpnServer request);

  ManagedServerDtos.Update command(UpdateManagedVpnServer request);

  ManagedVpnServer server(ManagedServerDtos.AdminServer result);

  List<ManagedVpnServer> adminList(List<ManagedServerDtos.AdminServer> result);

  List<PublicVpnServer> publicList(List<ManagedServerDtos.PublicServer> result);
}
