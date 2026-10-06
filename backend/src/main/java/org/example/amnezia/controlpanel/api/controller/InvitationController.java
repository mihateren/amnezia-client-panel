package org.example.amnezia.controlpanel.api.controller;

import org.example.amnezia.controlpanel.api.mapper.AuthApiMapper;
import org.example.amnezia.controlpanel.api.mapper.InvitationApiMapper;
import org.example.amnezia.controlpanel.api.rest.api.InvitationsApi;
import org.example.amnezia.controlpanel.api.rest.model.AuthResponse;
import org.example.amnezia.controlpanel.api.rest.model.CreateInvitationRequest;
import org.example.amnezia.controlpanel.api.rest.model.InvitationResponse;
import org.example.amnezia.controlpanel.api.rest.model.InvitationValidationResponse;
import org.example.amnezia.controlpanel.api.rest.model.RegisterUserRequest;
import org.example.amnezia.controlpanel.api.service.InvitationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController implements InvitationsApi {
  private final InvitationService service;
  private final InvitationApiMapper mapper;
  private final AuthApiMapper authMapper;
  private final ApiRequestContext context;

  public InvitationController(
      InvitationService service,
      InvitationApiMapper mapper,
      AuthApiMapper authMapper,
      ApiRequestContext context) {
    this.service = service;
    this.mapper = mapper;
    this.authMapper = authMapper;
    this.context = context;
  }

  @Override
  public ResponseEntity<InvitationResponse> createInvitation(CreateInvitationRequest request) {
    var command = context.validated(mapper.command(request));
    return ResponseEntity.status(201)
        .body(mapper.response(service.create(context.actor(), command.internalName())));
  }

  @Override
  public ResponseEntity<InvitationValidationResponse> validateInvitation(String token) {
    service.validate(token);
    return ResponseEntity.ok(mapper.validation(true));
  }

  @Override
  public ResponseEntity<AuthResponse> registerUser(String token, RegisterUserRequest request) {
    var result = service.register(token, context.validated(authMapper.credentials(request)));
    return ResponseEntity.status(201)
        .header(HttpHeaders.SET_COOKIE, context.cookie(result.refreshToken()))
        .body(authMapper.response(result.response()));
  }
}
