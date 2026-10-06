package org.example.amnezia.controlpanel.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.config.MetricsProperties;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.service.AuthService;
import org.example.amnezia.controlpanel.api.web.Errors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableConfigurationProperties(MetricsProperties.class)
public class SecurityConfig {
  @Bean
  org.springframework.security.core.userdetails.UserDetailsService unusedFormLoginUsers() {
    return username -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "Unsupported login mechanism");
    };
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      JwtService jwt,
      AuthService auth,
      Errors errors,
      AppProperties config,
      MetricsProperties metrics)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .logout(l -> l.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/invitations/**",
                        "/api/v1/config/**",
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus")
                    .hasAnyRole("ADMIN", "METRICS")
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            errors.write(res, 401, "UNAUTHORIZED", "Authentication required"))
                    .accessDeniedHandler(
                        (req, res, ex) -> errors.write(res, 403, "FORBIDDEN", "Access denied")))
        .addFilterBefore(
            new BearerFilter(jwt, auth, errors, config, metrics),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private static final class BearerFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AuthService auth;
    private final Errors errors;
    private final String origin;
    private final byte[] metricsToken;

    BearerFilter(
        JwtService jwt,
        AuthService auth,
        Errors errors,
        AppProperties config,
        MetricsProperties metrics) {
      this.jwt = jwt;
      this.auth = auth;
      this.errors = errors;
      this.metricsToken = metrics.token().getBytes(StandardCharsets.UTF_8);
      var uri = URI.create(config.publicUrl());
      this.origin = uri.getScheme() + "://" + uri.getRawAuthority();
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {
      // Cookie endpoints accept same-origin browser requests only. Bearer tokens are never read
      // from cookies.
      if (!req.getMethod().equals("GET")
          && !req.getMethod().equals("HEAD")
          && ((req.getHeader("Origin") != null && !origin.equals(req.getHeader("Origin")))
              || "cross-site".equals(req.getHeader("Sec-Fetch-Site")))) {
        errors.write(res, 403, "FORBIDDEN", "Cross-origin request denied");
        return;
      }
      var header = req.getHeader("Authorization");
      if (header != null) {
        try {
          if (!header.startsWith("Bearer ") || header.length() > 4096)
            throw ApiException.unauthorized();
          if ("GET".equals(req.getMethod())
              && "/actuator/prometheus".equals(req.getServletPath())
              && metricsToken.length > 0
              && MessageDigest.isEqual(
                  metricsToken, header.substring(7).getBytes(StandardCharsets.UTF_8))) {
            SecurityContextHolder.getContext()
                .setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                        "prometheus", null, List.of(new SimpleGrantedAuthority("ROLE_METRICS"))));
          } else {
            var user = auth.active(jwt.verify(header.substring(7)));
            var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                    user.id(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
          }
        } catch (ApiException e) {
          errors.write(res, 401, "UNAUTHORIZED", "Authentication required");
          return;
        }
      }
      chain.doFilter(req, res);
    }
  }
}
