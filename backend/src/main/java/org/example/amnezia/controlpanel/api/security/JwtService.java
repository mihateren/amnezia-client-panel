package org.example.amnezia.controlpanel.api.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final String ISSUER = "amnezia-control-panel";
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;
  private final Clock clock;
  private final AppProperties config;

  public JwtService(AppProperties config, Clock clock) {
    this.config = config;
    this.clock = clock;
    var key = new SecretKeySpec(Secrets.key(config.jwtSecret()), "HmacSHA256");
    encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    var verifier = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    verifier.setJwtValidator(new JwtIssuerValidator(ISSUER));
    decoder = verifier;
  }

  public String issue(UUID userId) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(config.accessTtl()))
            .id(UUID.randomUUID().toString())
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  public UUID verify(String token) {
    try {
      var jwt = decoder.decode(token);
      var now = clock.instant();
      if (jwt.getExpiresAt() == null
          || !jwt.getExpiresAt().isAfter(now)
          || jwt.getIssuedAt() == null
          || jwt.getIssuedAt().isAfter(now.plusSeconds(30))) throw ApiException.unauthorized();
      return UUID.fromString(jwt.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      throw ApiException.unauthorized();
    }
  }
}
