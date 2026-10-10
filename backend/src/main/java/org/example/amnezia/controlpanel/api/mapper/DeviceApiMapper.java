package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.dto.ApiCommands;
import org.example.amnezia.controlpanel.api.dto.DeviceResponse;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.rest.model.CreateDeviceRequest;
import org.example.amnezia.controlpanel.api.rest.model.Device;
import org.example.amnezia.controlpanel.api.rest.model.DevicePage;
import org.example.amnezia.controlpanel.api.rest.model.DeviceStatus;
import org.example.amnezia.controlpanel.api.rest.model.UpdateDeviceRequest;
import org.mapstruct.Mapper;

@Mapper(config = ApiMapperConfig.class)
public interface DeviceApiMapper {
  ApiCommands.CreateDevice command(CreateDeviceRequest request);

  ApiCommands.UpdateDevice command(UpdateDeviceRequest request);

  Device device(DeviceResponse result);

  DevicePage page(PageResponse<DeviceResponse> result);

  DeviceResponse.Status status(DeviceStatus status);
}
