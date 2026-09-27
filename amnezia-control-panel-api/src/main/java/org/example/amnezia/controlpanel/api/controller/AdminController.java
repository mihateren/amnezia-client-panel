package org.example.amnezia.controlpanel.api.controller;

import java.util.UUID;
import org.example.amnezia.controlpanel.api.mapper.AdminApiMapper;
import org.example.amnezia.controlpanel.api.mapper.DeviceApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.AdminDashboardApi;
import org.example.amnezia.controlpanel.api.rest.api.AdminDevicesApi;
import org.example.amnezia.controlpanel.api.rest.api.UsersApi;
import org.example.amnezia.controlpanel.api.rest.model.AdminDeviceDetails;
import org.example.amnezia.controlpanel.api.rest.model.AdminDevicePage;
import org.example.amnezia.controlpanel.api.rest.model.AdminUserDetails;
import org.example.amnezia.controlpanel.api.rest.model.DashboardResponse;
import org.example.amnezia.controlpanel.api.rest.model.DeviceStatus;
import org.example.amnezia.controlpanel.api.rest.model.UpdateUserStatusRequest;
import org.example.amnezia.controlpanel.api.rest.model.UserPage;
import org.example.amnezia.controlpanel.api.rest.model.UserStatus;
import org.example.amnezia.controlpanel.api.rest.model.VpnState;
import org.example.amnezia.controlpanel.api.service.AdminService;
import org.example.amnezia.controlpanel.api.service.DeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController implements UsersApi, AdminDevicesApi, AdminDashboardApi {
  private final AdminService service;
  private final DeviceService devices;
  private final AdminApiMapper mapper;
  private final DeviceApiMapper deviceMapper;
  private final ApiRequestContext context;

  public AdminController(
      AdminService service,
      DeviceService devices,
      AdminApiMapper mapper,
      DeviceApiMapper deviceMapper,
      ApiRequestContext context) {
    this.service = service;
    this.devices = devices;
    this.mapper = mapper;
    this.deviceMapper = deviceMapper;
    this.context = context;
  }

  @Override
  public ResponseEntity<UserPage> getUsers(
      Integer page, Integer size, UserStatus status, String search) {
    return ResponseEntity.ok(
        mapper.users(service.users(context.actor(), page, size, mapper.status(status), search)));
  }

  @Override
  public ResponseEntity<AdminUserDetails> getUser(UUID userId) {
    return ResponseEntity.ok(mapper.details(service.detail(context.actor(), userId)));
  }

  @Override
  public ResponseEntity<AdminUserDetails> updateUserStatus(
      UUID userId, UpdateUserStatusRequest request) {
    return ResponseEntity.ok(
        mapper.details(
            service.status(context.actor(), userId, mapper.status(request.getStatus()))));
  }

  @Override
  public ResponseEntity<Void> deleteUser(UUID userId) {
    service.deleteUser(context.actor(), userId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<AdminDevicePage> getAllDevices(
      Integer page, Integer size, UUID userId, DeviceStatus status) {
    return ResponseEntity.ok(
        mapper.devices(
            service.devices(context.actor(), page, size, userId, deviceMapper.status(status))));
  }

  @Override
  public ResponseEntity<AdminDeviceDetails> getAdminDevice(UUID deviceId) {
    return ResponseEntity.ok(mapper.device(service.device(context.actor(), deviceId)));
  }

  @Override
  public ResponseEntity<Void> deleteAdminDevice(UUID deviceId) {
    devices.delete(context.actor(), deviceId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<DashboardResponse> getAdminDashboard() {
    return ResponseEntity.ok(mapper.dashboard(service.dashboard(context.actor())));
  }

  @Override
  public ResponseEntity<VpnState> getVpnState() {
    return ResponseEntity.ok(mapper.vpn(service.vpn(context.actor())));
  }
}
