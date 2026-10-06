package org.example.amnezia.controlpanel.api.exception;

import org.springframework.http.HttpStatus;

/** Only safe, application-owned messages belong in API exceptions. */
public final class ApiException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static ApiException notFound() {
    return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }

  public static ApiException unauthorized() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
  }

  public static ApiException vpn() {
    return new ApiException(HttpStatus.BAD_GATEWAY, "VPN_UNAVAILABLE", "VPN operation failed");
  }

  public static ApiException ssh() {
    return new ApiException(
        HttpStatus.BAD_GATEWAY, "SSH_OPERATION_FAILED", "Remote VPN operation failed");
  }

  public static ApiException invalid() {
    return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request");
  }

  public static ApiException conflict(String code, String message) {
    return new ApiException(HttpStatus.CONFLICT, code, message);
  }
}
