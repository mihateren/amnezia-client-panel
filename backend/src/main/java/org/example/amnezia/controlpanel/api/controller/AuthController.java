package org.example.amnezia.controlpanel.api.controller;

import org.example.amnezia.controlpanel.api.dto.AuthDtos;
import org.example.amnezia.controlpanel.api.mapper.AuthApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.AuthenticationApi;
import org.example.amnezia.controlpanel.api.rest.model.AuthResponse;
import org.example.amnezia.controlpanel.api.rest.model.ChangePasswordRequest;
import org.example.amnezia.controlpanel.api.rest.model.CurrentUser;
import org.example.amnezia.controlpanel.api.rest.model.LoginRequest;
import org.example.amnezia.controlpanel.api.rest.model.TokenResponse;
import org.example.amnezia.controlpanel.api.rest.model.UpdateNicknameRequest;
import org.example.amnezia.controlpanel.api.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthenticationApi {
  private final AuthService service;
  private final AuthApiMapper mapper;
  private final ApiRequestContext context;

  public AuthController(AuthService service, AuthApiMapper mapper, ApiRequestContext context) {
    this.service = service;
    this.mapper = mapper;
    this.context = context;
  }

  @Override
  public ResponseEntity<AuthResponse> login(LoginRequest request) {
    var result = service.login(context.validated(mapper.credentials(request)));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, context.cookie(result.refreshToken()))
        .body(mapper.response(result.response()));
  }

  @Override
  public ResponseEntity<TokenResponse> refreshToken() {
    var result = service.refresh(context.refresh());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, context.cookie(result.refreshToken()))
        .body(mapper.token(new AuthDtos.TokenResponse(result.response().accessToken())));
  }

  @Override
  public ResponseEntity<Void> logout() {
    service.logout(context.actor(), context.refresh());
    return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, context.cookie("")).build();
  }

  @Override
  public ResponseEntity<CurrentUser> updateCurrentNickname(UpdateNicknameRequest request) {
    return ResponseEntity.ok(
        mapper.user(
            service.updateNickname(context.actor(), context.validated(mapper.nickname(request)))));
  }

  @Override
  public ResponseEntity<AuthResponse> changeCurrentPassword(ChangePasswordRequest request) {
    var result =
        service.changePassword(context.actor(), context.validated(mapper.password(request)));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, context.cookie(result.refreshToken()))
        .body(mapper.response(result.response()));
  }

  @Override
  public ResponseEntity<CurrentUser> getCurrentUser() {
    return ResponseEntity.ok(
        mapper.user(AuthDtos.CurrentUser.from(service.active(context.actor()))));
  }
}
