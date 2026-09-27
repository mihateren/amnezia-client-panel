package org.example.amnezia.controlpanel.api.web;

import io.micrometer.core.instrument.MeterRegistry;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.rest.model.ErrorResponse;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class WebExceptionHandler {
  private final MeterRegistry metrics;
  private final Errors errors;

  public WebExceptionHandler(MeterRegistry metrics, Errors errors) {
    this.metrics = metrics;
    this.errors = errors;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> application(ApiException e) {
    return response(e.status().value(), e.code(), e.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<?> validation(Exception e) {
    return response(400, "VALIDATION_ERROR", "Invalid request");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(Exception e) {
    return response(409, "CONFLICT", "Resource conflict");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> forbidden(Exception e) {
    return response(403, "FORBIDDEN", "Access denied");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<?> missing(Exception e) {
    return response(404, "NOT_FOUND", "Resource not found");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> method(Exception e) {
    return response(405, "METHOD_NOT_ALLOWED", "Method not allowed");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> failure(Exception e) {
    if (e instanceof DataAccessException) metrics.counter("db_errors").increment();
    LoggerFactory.getLogger(getClass())
        .error("Request failed: type={}", e.getClass().getSimpleName());
    return response(500, "INTERNAL_ERROR", "Internal error");
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> mediaType(Exception e) {
    return response(415, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type");
  }

  private ResponseEntity<ErrorResponse> response(int status, String code, String message) {
    return ResponseEntity.status(status).body(errors.body(code, message));
  }
}
