package org.example.amnezia.controlpanel.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component("correlationIdFilter")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var candidate = request.getHeader("X-Correlation-ID");
    var correlationId =
        candidate != null && candidate.matches("[a-zA-Z0-9_-]{1,64}")
            ? candidate
            : UUID.randomUUID().toString();
    MDC.put("correlation_id", correlationId);
    response.setHeader("X-Correlation-ID", correlationId);
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("Referrer-Policy", "no-referrer");
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove("correlation_id");
    }
  }
}
