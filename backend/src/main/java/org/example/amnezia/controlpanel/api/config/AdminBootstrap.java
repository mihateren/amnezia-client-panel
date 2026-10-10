package org.example.amnezia.controlpanel.api.config;

import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.USER_CREATED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.SUCCESS;

import java.time.Clock;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@DependsOn("sqlite.Liquibase")
public class AdminBootstrap implements ApplicationRunner {
  private final AppProperties config;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final AuditService audit;
  private final Clock clock;
  private final TransactionTemplate tx;

  public AdminBootstrap(
      AppProperties config,
      UserRepository users,
      PasswordEncoder passwords,
      AuditService audit,
      Clock clock,
      @Qualifier(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN) TransactionTemplate tx) {
    this.config = config;
    this.users = users;
    this.passwords = passwords;
    this.audit = audit;
    this.clock = clock;
    this.tx = tx;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (users.count() != 0 || config.bootstrapNickname().isBlank()) return;
    if (config.bootstrapNickname().length() < 3
        || config.bootstrapNickname().length() > 64
        || config.bootstrapPassword().length() < 12
        || config.bootstrapPassword().length() > 256)
      throw new IllegalArgumentException("Bootstrap credentials do not meet length requirements");
    tx.executeWithoutResult(
        status -> {
          var now = clock.instant();
          var user =
              new User(
                  UUID.randomUUID(),
                  "Administrator",
                  config.bootstrapNickname(),
                  passwords.encode(config.bootstrapPassword()),
                  User.Role.ADMIN,
                  User.Status.ACTIVE,
                  now,
                  now,
                  null);
          users.insert(user);
          audit.record(USER_CREATED, SUCCESS, user.id(), "USER", user.id());
        });
  }
}
