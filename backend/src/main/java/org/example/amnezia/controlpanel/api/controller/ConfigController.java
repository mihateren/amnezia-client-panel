package org.example.amnezia.controlpanel.api.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.mapper.ConfigApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.DeviceConfigurationApi;
import org.example.amnezia.controlpanel.api.rest.model.ConfigFormat;
import org.example.amnezia.controlpanel.api.rest.model.ConfigLinkResponse;
import org.example.amnezia.controlpanel.api.rest.model.CreateConfigLinkRequest;
import org.example.amnezia.controlpanel.api.service.ConfigService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class ConfigController implements DeviceConfigurationApi {
  private final ConfigService service;
  private final ConfigApiMapper mapper;
  private final ApiRequestContext context;
  private final JsonMapper json;

  public ConfigController(
      ConfigService service, ConfigApiMapper mapper, ApiRequestContext context, JsonMapper json) {
    this.service = service;
    this.mapper = mapper;
    this.context = context;
    this.json = json;
  }

  @Override
  public ResponseEntity<String> getDeviceConfig(UUID deviceId, ConfigFormat format) {
    var text = service.config(context.actor(), deviceId, false);
    // The generated interface selects the first (text/plain) schema for this mixed-media response.
    return format == ConfigFormat.TEXT
        ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(text)
        : ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.writeValueAsString(mapper.config(text)));
  }

  @Override
  public ResponseEntity<Resource> downloadDeviceConfig(UUID deviceId) {
    var text = service.config(context.actor(), deviceId, true);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"device-"
                + deviceId
                + service.extension(context.actor(), deviceId)
                + "\"")
        .body(new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public ResponseEntity<Resource> getDeviceConfigQr(UUID deviceId) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .body(new ByteArrayResource(service.qr(context.actor(), deviceId)));
  }

  @Override
  public ResponseEntity<ConfigLinkResponse> createDeviceConfigLink(
      UUID deviceId, CreateConfigLinkRequest request) {
    return ResponseEntity.ok(
        mapper.link(
            service.link(
                context.actor(), deviceId, request == null ? null : request.getExpiresIn())));
  }

  @Override
  public ResponseEntity<String> importConfigByLink(String token) {
    return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(service.importLink(token));
  }
}
