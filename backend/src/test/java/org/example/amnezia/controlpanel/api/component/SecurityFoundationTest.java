package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.dto.AuthDtos;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.security.JwtService;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.service.AuthService;
import org.example.amnezia.controlpanel.api.service.InvitationService;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityFoundationTest extends BaseComponentTest {
  @Autowired
  SecurityFoundationTest(
      Secrets secrets,
      JwtService jwt,
      UserRepository users,
      InvitationService invitations,
      AuthService auth,
      PasswordEncoder passwords,
      @org.springframework.beans.factory.annotation.Qualifier(
              org.example.amnezia.controlpanel.api.config.db.DatabaseBeans
                  .SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbc) {
    this.secrets = secrets;
    this.jwt = jwt;
    this.users = users;
    this.invitations = invitations;
    this.auth = auth;
    this.passwords = passwords;
    this.jdbc = jdbc;
  }

  private final Secrets secrets;
  private final JwtService jwt;
  private final UserRepository users;
  private final InvitationService invitations;
  private final AuthService auth;
  private final PasswordEncoder passwords;
  private final NamedParameterJdbcTemplate jdbc;

  @Test
  void shouldBindCiphertextToDeviceAndRejectTampering() {
    var id = UUID.randomUUID();
    var encrypted = secrets.encrypt("private-key", id);
    assertThat(encrypted).doesNotContain("private-key");
    assertThat(secrets.decrypt(encrypted, id)).isEqualTo("private-key");
    assertThatThrownBy(() -> secrets.decrypt(encrypted, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> secrets.decrypt(encrypted + "x", id))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldVerifySignatureAndRejectMalformedJwt() {
    var id = UUID.randomUUID();
    var token = jwt.issue(id);
    assertThat(jwt.verify(token)).isEqualTo(id);
    assertThatThrownBy(() -> jwt.verify(token.substring(0, token.lastIndexOf('.') + 1) + "invalid"))
        .isInstanceOf(org.example.amnezia.controlpanel.api.exception.ApiException.class);
    assertThatThrownBy(() -> jwt.verify("not-a-jwt")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldRegisterOnceRotateRefreshAndRevokeOnLogout() {
    var now = Instant.now();
    var admin =
        new User(
            UUID.randomUUID(),
            "internal-" + UUID.randomUUID(),
            "admin-" + UUID.randomUUID(),
            passwords.encode("test-password-long"),
            User.Role.ADMIN,
            User.Status.ACTIVE,
            now,
            now,
            null);
    users.insert(admin);
    var invitation = invitations.create(admin.id(), "person-" + UUID.randomUUID());
    var token =
        invitation.invitationUrl().substring(invitation.invitationUrl().lastIndexOf('/') + 1);
    var credentials = new AuthDtos.Credentials("user-" + UUID.randomUUID(), "test-password-long");
    var registration = invitations.register(token, credentials);
    assertThatThrownBy(() -> invitations.register(token, credentials))
        .isInstanceOf(RuntimeException.class);
    var user = users.find(registration.response().user().id()).orElseThrow();
    assertThat(user.passwordHash()).isNotEqualTo(credentials.password());
    assertThat(passwords.matches(credentials.password(), user.passwordHash())).isTrue();
    assertThat(
            jdbc.queryForObject(
                TestQuery.INVITATION_TOKEN_HASH.query(),
                new MapSqlParameterSource("id", invitation.id().toString()),
                String.class))
        .isEqualTo(Secrets.hash(token));
    var rotated = auth.refresh(registration.refreshToken());
    assertThatThrownBy(() -> auth.refresh(registration.refreshToken()))
        .isInstanceOf(RuntimeException.class);
    auth.logout(user.id(), rotated.refreshToken());
    assertThatThrownBy(() -> auth.refresh(rotated.refreshToken()))
        .isInstanceOf(RuntimeException.class);
    var login = auth.login(credentials);
    assertThat(login.response().user().id()).isEqualTo(user.id());
    jdbc.update(
        TestQuery.EXPIRE_REFRESH_SESSION.query(),
        new MapSqlParameterSource("expiresAt", Instant.EPOCH.toString())
            .addValue("tokenHash", Secrets.hash(login.refreshToken())));
    assertThatThrownBy(() -> auth.refresh(login.refreshToken()))
        .isInstanceOf(RuntimeException.class);
    users.status(user.id(), User.Status.DISABLED, now);
    assertThatThrownBy(() -> auth.login(credentials)).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldMakeAuditAppendOnly() {
    var id = UUID.randomUUID().toString();
    jdbc.update(
        TestQuery.INSERT_AUDIT.query(),
        new MapSqlParameterSource("id", id).addValue("createdAt", Instant.now().toString()));
    assertThatThrownBy(
            () -> jdbc.update(TestQuery.UPDATE_AUDIT.query(), new MapSqlParameterSource("id", id)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc.update(TestQuery.DELETE_AUDIT.query(), new MapSqlParameterSource("id", id)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
