package org.example.amnezia.controlpanel.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLimitsFilter extends OncePerRequestFilter {
  private static final int MAX_BODY = 16 * 1024;
  private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Errors errors;

  private record Window(long minute, int count) {}

  public RequestLimitsFilter(Clock clock, Errors errors) {
    this.clock = clock;
    this.errors = errors;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (req.getContentLengthLong() > MAX_BODY) {
      errors.write(res, 413, "PAYLOAD_TOO_LARGE", "Request body too large");
      return;
    }
    if ("POST".equals(req.getMethod())
        && (req.getRequestURI().equals("/api/v1/auth/login")
            || req.getRequestURI().endsWith("/register"))) {
      long minute = clock.instant().getEpochSecond() / 60;
      attempts.entrySet().removeIf(entry -> entry.getValue().minute() < minute);
      if (attempts.size() >= 10000 && !attempts.containsKey(req.getRemoteAddr())) {
        errors.write(res, 429, "RATE_LIMITED", "Too many attempts");
        return;
      }
      var window =
          attempts.compute(
              req.getRemoteAddr(),
              (key, old) ->
                  new Window(minute, old == null || old.minute() != minute ? 1 : old.count() + 1));
      if (window.count() > 30) {
        res.setHeader("Retry-After", "60");
        errors.write(res, 429, "RATE_LIMITED", "Too many attempts");
        return;
      }
    }
    chain.doFilter(
        new HttpServletRequestWrapper(req) {
          @Override
          public ServletInputStream getInputStream() throws IOException {
            var original = super.getInputStream();
            return new ServletInputStream() {
              private int bytes;

              @Override
              public int read() throws IOException {
                int value = original.read();
                if (value != -1 && ++bytes > MAX_BODY)
                  throw new IOException("Request body limit exceeded");
                return value;
              }

              @Override
              public boolean isFinished() {
                return original.isFinished();
              }

              @Override
              public boolean isReady() {
                return original.isReady();
              }

              @Override
              public void setReadListener(ReadListener listener) {
                original.setReadListener(listener);
              }
            };
          }
        },
        res);
  }
}
