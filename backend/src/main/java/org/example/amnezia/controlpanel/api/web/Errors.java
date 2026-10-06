package org.example.amnezia.controlpanel.api.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.example.amnezia.controlpanel.api.mapper.ErrorApiMapper;
import org.example.amnezia.controlpanel.api.rest.model.ErrorResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class Errors {
  private final Clock clock;
  private final JsonMapper json;
  private final ErrorApiMapper mapper;

  public Errors(Clock clock, JsonMapper json, ErrorApiMapper mapper) {
    this.clock = clock;
    this.json = json;
    this.mapper = mapper;
  }

  public ErrorResponse body(String code, String message) {
    var correlationId = MDC.get("correlation_id");
    return mapper.response(
        new org.example.amnezia.controlpanel.api.dto.ErrorResponse(
            code, message, clock.instant(), correlationId == null ? "background" : correlationId));
  }

  public void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    json.writeValue(response.getOutputStream(), body(code, message));
  }
}
