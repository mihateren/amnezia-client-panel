package org.example.amnezia.controlpanel.api.mapper;

import org.example.amnezia.controlpanel.api.dto.AuthDtos;
import org.example.amnezia.controlpanel.api.rest.model.AuthResponse;
import org.example.amnezia.controlpanel.api.rest.model.CurrentUser;
import org.example.amnezia.controlpanel.api.rest.model.LoginRequest;
import org.example.amnezia.controlpanel.api.rest.model.RegisterUserRequest;
import org.example.amnezia.controlpanel.api.rest.model.TokenResponse;
import org.mapstruct.Mapper;

@Mapper(config = ApiMapperConfig.class)
public interface AuthApiMapper {
  AuthDtos.Credentials credentials(LoginRequest request);

  AuthDtos.Credentials credentials(RegisterUserRequest request);

  AuthResponse response(AuthDtos.AuthResponse result);

  CurrentUser user(AuthDtos.CurrentUser user);

  TokenResponse token(AuthDtos.TokenResponse token);
}
