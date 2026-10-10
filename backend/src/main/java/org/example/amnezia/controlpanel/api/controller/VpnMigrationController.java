package org.example.amnezia.controlpanel.api.controller;

import org.example.amnezia.controlpanel.api.mapper.VpnMigrationApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.VpnMigrationApi;
import org.example.amnezia.controlpanel.api.rest.model.VpnMigrationRequest;
import org.example.amnezia.controlpanel.api.rest.model.VpnMigrationStatus;
import org.example.amnezia.controlpanel.api.service.VpnMigrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VpnMigrationController implements VpnMigrationApi {
  private final VpnMigrationService service;
  private final VpnMigrationApiMapper mapper;
  private final ApiRequestContext context;

  public VpnMigrationController(
      VpnMigrationService service, VpnMigrationApiMapper mapper, ApiRequestContext context) {
    this.service = service;
    this.mapper = mapper;
    this.context = context;
  }

  @Override
  public ResponseEntity<VpnMigrationStatus> getVpnMigration() {
    return ResponseEntity.ok(mapper.status(service.status(context.actor())));
  }

  @Override
  public ResponseEntity<VpnMigrationStatus> startVpnMigration(VpnMigrationRequest request) {
    return ResponseEntity.ok(
        mapper.status(service.change(context.actor(), mapper.command(request).generation(), true)));
  }

  @Override
  public ResponseEntity<VpnMigrationStatus> completeVpnMigration(VpnMigrationRequest request) {
    return ResponseEntity.ok(
        mapper.status(
            service.change(context.actor(), mapper.command(request).generation(), false)));
  }
}
