package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.dto.AdminDtos;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.rest.model.AdminDeviceDetails;
import org.example.amnezia.controlpanel.api.rest.model.AdminDevicePage;
import org.example.amnezia.controlpanel.api.rest.model.AdminUser;
import org.example.amnezia.controlpanel.api.rest.model.AdminUserDetails;
import org.example.amnezia.controlpanel.api.rest.model.DashboardResponse;
import org.example.amnezia.controlpanel.api.rest.model.UserPage;
import org.example.amnezia.controlpanel.api.rest.model.UserStatus;
import org.example.amnezia.controlpanel.api.rest.model.VpnState;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = ApiMapperConfig.class, uses = DeviceApiMapper.class)
public interface AdminApiMapper {
  AdminUser user(AdminDtos.AdminUser result);

  @Mapping(target = ".", source = "user")
  AdminUserDetails details(AdminDtos.UserDetails result);

  @Mapping(target = ".", source = "device")
  AdminDeviceDetails device(AdminDtos.AdminDevice result);

  UserPage users(PageResponse<AdminDtos.AdminUser> result);

  AdminDevicePage devices(PageResponse<AdminDtos.AdminDevice> result);

  User.Status status(UserStatus status);

  DashboardResponse dashboard(AdminDtos.Dashboard result);

  VpnState vpn(AdminDtos.VpnState result);
}
