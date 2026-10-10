package org.example.amnezia.controlpanel.api.controller;

import java.util.UUID;
import org.example.amnezia.controlpanel.api.mapper.DeviceApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.DevicesApi;
import org.example.amnezia.controlpanel.api.rest.model.CreateDeviceRequest;
import org.example.amnezia.controlpanel.api.rest.model.Device;
import org.example.amnezia.controlpanel.api.rest.model.DevicePage;
import org.example.amnezia.controlpanel.api.rest.model.UpdateDeviceRequest;
import org.example.amnezia.controlpanel.api.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceController implements DevicesApi {
  private final DeviceService service;
  private final DeviceApiMapper mapper;
  private final ApiRequestContext context;

  public DeviceController(
      DeviceService service, DeviceApiMapper mapper, ApiRequestContext context) {
    this.service = service;
    this.mapper = mapper;
    this.context = context;
  }

  @Override
  public ResponseEntity<Device> createDevice(CreateDeviceRequest request) {
    var command = context.validated(mapper.command(request));
    return ResponseEntity.status(201)
        .body(mapper.device(service.create(context.actor(), command.name(), command.icon())));
  }

  @Override
  public ResponseEntity<Device> updateMyDevice(UUID deviceId, UpdateDeviceRequest request) {
    var command = context.validated(mapper.command(request));
    return ResponseEntity.ok(mapper.device(service.update(context.actor(), deviceId, command)));
  }

  @Override
  public ResponseEntity<DevicePage> getMyDevices(Integer page, Integer size) {
    return ResponseEntity.ok(mapper.page(service.list(context.actor(), page, size)));
  }

  @Override
  public ResponseEntity<Device> getMyDevice(UUID deviceId) {
    return ResponseEntity.ok(mapper.device(service.get(context.actor(), deviceId)));
  }

  @Override
  public ResponseEntity<Void> deleteMyDevice(UUID deviceId) {
    service.delete(context.actor(), deviceId);
    return ResponseEntity.noContent().build();
  }
}
