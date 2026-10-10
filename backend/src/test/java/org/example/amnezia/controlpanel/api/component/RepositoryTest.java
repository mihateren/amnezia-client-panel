package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.amnezia.controlpanel.api.dto.AuthDtos;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.RefreshSessionRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.example.amnezia.controlpanel.api.service.AuthService;
import org.example.amnezia.controlpanel.api.service.InvitationService;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class RepositoryTest extends BaseComponentTest {
  @Autowired
  RepositoryTest(
      UserRepository users,
      @org.springframework.beans.factory.annotation.Qualifier(
              org.example.amnezia.controlpanel.api.config.db.DatabaseBeans
                  .SQLITE_NAMED_JDBC_TEMPLATE_BEAN)
          NamedParameterJdbcTemplate jdbc,
      TransactionTemplate tx,
      InvitationService invitations,
      AuthService auth,
      RefreshSessionRepository sessions,
      org.springframework.security.crypto.password.PasswordEncoder passwords,
      @org.springframework.beans.factory.annotation.Qualifier(
              org.example.amnezia.controlpanel.api.config.db.DatabaseBeans.SQLITE_LIQUIBASE_BEAN)
          liquibase.integration.spring.SpringLiquibase liquibase) {
    this.users = users;
    this.jdbc = jdbc;
    this.tx = tx;
    this.invitations = invitations;
    this.auth = auth;
    this.sessions = sessions;
    this.passwords = passwords;
    this.liquibase = liquibase;
  }

  private final UserRepository users;
  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final InvitationService invitations;
  private final AuthService auth;
  private final RefreshSessionRepository sessions;
  private final org.springframework.security.crypto.password.PasswordEncoder passwords;

  private final liquibase.integration.spring.SpringLiquibase liquibase;

  private User user(String name) {
    var now = Instant.now();
    return new User(
        UUID.randomUUID(),
        "internal-" + UUID.randomUUID(),
        name,
        "hashed-test-value",
        User.Role.USER,
        User.Status.ACTIVE,
        now,
        now,
        null);
  }

  @Test
  void shouldPersistUpdatePaginateEnforceUniqueAndRollback() {
    var prefix = "repository-" + UUID.randomUUID();
    var first = user(prefix + "-1");
    users.insert(first);
    users.insert(user(prefix + "-2"));
    assertThat(users.find(first.id())).contains(first);
    assertThat(users.list(null, prefix, 1, 0)).hasSize(1);
    assertThat(users.count(null, prefix)).isEqualTo(2);
    assertThat(users.list(null, prefix, 1, 1).getFirst().id())
        .isNotEqualTo(users.list(null, prefix, 1, 0).getFirst().id());
    assertThatThrownBy(() -> users.insert(user(first.nickname())))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    users.status(first.id(), User.Status.DISABLED, Instant.now());
    assertThat(users.find(first.id()).orElseThrow().status()).isEqualTo(User.Status.DISABLED);
    var rolledBack = user(prefix + "-rollback");
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      users.insert(rolledBack);
                      throw new IllegalStateException("Rollback");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(users.find(rolledBack.id())).isEmpty();
    users.delete(first.id());
    assertThat(users.find(first.id())).isEmpty();
  }

  @Test
  void shouldSearchLiteralWildcardsAndPreserveNullableActivity() {
    var prefix = "literal-" + UUID.randomUUID();
    var percent = user(prefix + "%value");
    var underscore = user(prefix + "_value");
    var backslash = user(prefix + "\\value");
    users.insert(percent);
    users.insert(underscore);
    users.insert(backslash);
    users.insert(user(prefix + "Xvalue"));
    assertThat(users.list(null, prefix + "%", 10, 0)).containsExactly(percent);
    assertThat(users.count(null, prefix + "%")).isEqualTo(1);
    assertThat(users.list(null, prefix + "_", 10, 0)).containsExactly(underscore);
    assertThat(users.list(null, prefix + "\\", 10, 0)).containsExactly(backslash);
    assertThat(users.find(percent.id()).orElseThrow().lastActivityAt()).isNull();
    var activity = Instant.parse("2026-01-02T03:04:05.123456789Z");
    users.activity(percent.id(), activity);
    assertThat(users.find(percent.id()).orElseThrow().lastActivityAt()).isEqualTo(activity);
    users.status(percent.id(), User.Status.DISABLED, activity);
    assertThat(users.list(User.Status.ACTIVE, prefix + "%", 10, 0)).isEmpty();
    assertThat(users.count(User.Status.DISABLED, prefix + "%")).isEqualTo(1);
  }

  @Test
  void shouldRejectInvalidEnumsAndHaveNoForeignKeys() {
    var sample = user("constraints-" + UUID.randomUUID());
    users.insert(sample);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    TestQuery.INVALID_USER_ROLE.query(),
                    new MapSqlParameterSource("id", sample.id().toString())))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (var table :
        List.of(
            "users", "invitations", "devices", "config_links", "audit_log", "refresh_sessions")) {
      assertThat(
              jdbc.queryForList(
                  TestQuery.FOREIGN_KEYS.query(), new MapSqlParameterSource("table", table)))
          .isEmpty();
    }
  }

  @Test
  void shouldApplyMigrationsAgainWithoutChangingSchema() throws Exception {
    var before =
        jdbc.queryForObject(
            TestQuery.CHANGELOG_COUNT.query(), new MapSqlParameterSource(), Long.class);
    liquibase.afterPropertiesSet();
    assertThat(
            jdbc.queryForObject(
                TestQuery.CHANGELOG_COUNT.query(), new MapSqlParameterSource(), Long.class))
        .isEqualTo(before);
    assertThat(
            jdbc.queryForList(
                TestQuery.TABLE_NAMES.query(), new MapSqlParameterSource(), String.class))
        .contains("refresh_sessions");
  }

  @Test
  void shouldAllowOneConcurrentRegistrationAndRollbackInvitationOnDuplicateNickname()
      throws Exception {
    var now = Instant.now();
    var admin =
        new User(
            UUID.randomUUID(),
            "admin-internal-" + UUID.randomUUID(),
            "admin-" + UUID.randomUUID(),
            passwords.encode("test-password-long"),
            User.Role.ADMIN,
            User.Status.ACTIVE,
            now,
            now,
            null);
    users.insert(admin);
    var invite = invitations.create(admin.id(), "concurrent-" + UUID.randomUUID());
    var token = invite.invitationUrl().substring(invite.invitationUrl().lastIndexOf('/') + 1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 3; i++)
        tasks.add(
            executor.submit(
                () -> {
                  try {
                    invitations.register(
                        token,
                        new AuthDtos.Credentials(
                            "race-" + UUID.randomUUID(), "test-password-long"));
                    return true;
                  } catch (org.example.amnezia.controlpanel.api.exception.ApiException e) {
                    return false;
                  }
                }));
      int success = 0;
      for (var task : tasks) if (task.get(10, TimeUnit.SECONDS)) success++;
      assertThat(success).isEqualTo(1);
    }
    var retry = invitations.create(admin.id(), "retry-" + UUID.randomUUID());
    var retryToken = retry.invitationUrl().substring(retry.invitationUrl().lastIndexOf('/') + 1);
    assertThatThrownBy(
            () ->
                invitations.register(
                    retryToken, new AuthDtos.Credentials(admin.nickname(), "test-password-long")))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    invitations.validate(retryToken);
    var registered =
        invitations.register(
            retryToken,
            new AuthDtos.Credentials("retry-" + UUID.randomUUID(), "test-password-long"));
    assertThat(registered.response().user().role()).isEqualTo(User.Role.USER);
    assertThat(
            jdbc.queryForObject(
                TestQuery.RAW_REFRESH_TOKEN_COUNT.query(),
                new MapSqlParameterSource("tokenHash", registered.refreshToken()),
                Long.class))
        .isZero();
    assertThat(sessions.find(Secrets.hash(registered.refreshToken()))).isPresent();
  }
}
