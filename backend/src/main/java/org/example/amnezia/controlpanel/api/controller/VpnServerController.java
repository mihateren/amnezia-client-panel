package org.example.amnezia.controlpanel.api.controller;

import org.example.amnezia.controlpanel.api.mapper.VpnServerApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.VpnServerApi;
import org.example.amnezia.controlpanel.api.rest.model.UpdateVpnServerRequest;
import org.example.amnezia.controlpanel.api.rest.model.VpnServerSettingsResponse;
import org.example.amnezia.controlpanel.api.service.VpnServerSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VpnServerController implements VpnServerApi {
  private final VpnServerSettings service;
  private final VpnServerApiMapper mapper;
  private final ApiRequestContext context;

  public VpnServerController(
      VpnServerSettings service, VpnServerApiMapper mapper, ApiRequestContext context) {
    this.service = service;
    this.mapper = mapper;
    this.context = context;
  }

  @Override
  public ResponseEntity<VpnServerSettingsResponse> getVpnServerSettings() {
    return ResponseEntity.ok(mapper.settings(service.get(context.actor())));
  }

  @Override
  public ResponseEntity<VpnServerSettingsResponse> updateVpnServerSettings(
      UpdateVpnServerRequest request) {
    return ResponseEntity.ok(
        mapper.settings(service.save(context.actor(), mapper.command(request))));
  }
}
