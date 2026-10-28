package org.example.amnezia.controlpanel.api.controller;

import java.util.List;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.mapper.ServerCatalogMapper;
import org.example.amnezia.controlpanel.api.rest.api.VpnServersApi;
import org.example.amnezia.controlpanel.api.rest.model.CreateVpnServer;
import org.example.amnezia.controlpanel.api.rest.model.ManagedVpnServer;
import org.example.amnezia.controlpanel.api.rest.model.PublicVpnServer;
import org.example.amnezia.controlpanel.api.rest.model.UpdateManagedVpnServer;
import org.example.amnezia.controlpanel.api.service.ServerCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServerCatalogController implements VpnServersApi {
  private final ServerCatalog catalog;
  private final ServerCatalogMapper mapper;
  private final ApiRequestContext context;

  public ServerCatalogController(
      ServerCatalog catalog, ServerCatalogMapper mapper, ApiRequestContext context) {
    this.catalog = catalog;
    this.mapper = mapper;
    this.context = context;
  }

  @Override
  public ResponseEntity<List<PublicVpnServer>> listVpnServers() {
    return ResponseEntity.ok(mapper.publicList(catalog.list(context.actor())));
  }

  @Override
  public ResponseEntity<List<ManagedVpnServer>> listManagedVpnServers() {
    return ResponseEntity.ok(mapper.adminList(catalog.adminList(context.actor())));
  }

  @Override
  public ResponseEntity<ManagedVpnServer> createVpnServer(CreateVpnServer request) {
    return ResponseEntity.status(201)
        .body(
            mapper.server(
                catalog.create(context.actor(), context.validated(mapper.command(request)))));
  }

  @Override
  public ResponseEntity<ManagedVpnServer> updateManagedVpnServer(
      UUID serverId, UpdateManagedVpnServer request) {
    return ResponseEntity.ok(
        mapper.server(
            catalog.update(context.actor(), serverId, context.validated(mapper.command(request)))));
  }
}
