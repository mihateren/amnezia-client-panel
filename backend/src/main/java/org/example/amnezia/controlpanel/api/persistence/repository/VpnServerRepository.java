package org.example.amnezia.controlpanel.api.persistence.repository;

import java.util.Optional;
import org.example.amnezia.controlpanel.api.persistence.model.VpnServer;

public interface VpnServerRepository {
  Optional<VpnServer> find();

  void save(VpnServer server);
}
