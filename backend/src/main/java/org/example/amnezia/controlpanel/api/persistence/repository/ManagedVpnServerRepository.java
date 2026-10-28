package org.example.amnezia.controlpanel.api.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.ManagedVpnServer;

public interface ManagedVpnServerRepository {
  List<ManagedVpnServer> all();

  Optional<ManagedVpnServer> find(UUID id);

  void insert(ManagedVpnServer server);

  void update(ManagedVpnServer server);

  void adoptLegacy(UUID serverId, UUID deviceId);
}
