package org.example.amnezia.controlpanel.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ApiRequestContext {
  private final HttpServletRequest request;
  private final Validator validator;
  private final AppProperties config;

  public ApiRequestContext(HttpServletRequest request, Validator validator, AppProperties config) {
    this.request = request;
    this.validator = validator;
    this.config = config;
  }

  public UUID actor() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  public String refresh() {
    var cookies = request.getCookies();
    if (cookies != null)
      for (var cookie : cookies) if ("refresh".equals(cookie.getName())) return cookie.getValue();
    return null;
  }

  public String cookie(String token) {
    return ResponseCookie.from("refresh", token)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(token.isEmpty() ? 0 : config.refreshTtl().toSeconds())
        .build()
        .toString();
  }

  public <T> T validated(T value) {
    var violations = validator.validate(value);
    if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    return value;
  }
}
