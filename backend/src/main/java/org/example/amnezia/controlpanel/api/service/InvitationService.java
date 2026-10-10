package org.example.amnezia.controlpanel.api.service;

import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.INVITATION_CREATED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.INVITATION_USED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.USER_CREATED;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.SUCCESS;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.config.db.DatabaseBeans;
import org.example.amnezia.controlpanel.api.dto.AuthDtos;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.Invitation;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.InvitationRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InvitationService {
  private final InvitationRepository invitations;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final AuthService auth;
  private final AuditService audit;
  private final AppProperties config;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final ReentrantLock lock;

  public InvitationService(
      InvitationRepository invitations,
      UserRepository users,
      PasswordEncoder passwords,
      AuthService auth,
      AuditService audit,
      AppProperties config,
      Clock clock,
      @Qualifier(DatabaseBeans.SQLITE_TX_TEMPLATE_BEAN) TransactionTemplate tx,
      ReentrantLock lock) {
    this.invitations = invitations;
    this.users = users;
    this.passwords = passwords;
    this.auth = auth;
    this.audit = audit;
    this.config = config;
    this.clock = clock;
    this.tx = tx;
    this.lock = lock;
  }

  public record Created(UUID id, String internalName, String invitationUrl, Instant createdAt) {
    @Override
    public String toString() {
      return "Created[id=" + id + "]";
    }
  }

  public Created create(UUID actor, String internalName) {
    lock.lock();
    try {
      if (auth.active(actor).role() != User.Role.ADMIN) throw ApiException.unauthorized();
      return tx.execute(
          status -> {
            var token = Secrets.token();
            var item =
                new Invitation(
                    UUID.randomUUID(),
                    Secrets.hash(token),
                    internalName,
                    actor,
                    clock.instant(),
                    null,
                    null);
            invitations.insert(item);
            audit.record(INVITATION_CREATED, SUCCESS, actor, "INVITATION", item.id());
            return new Created(
                item.id(),
                internalName,
                config.publicUrl() + "/api/v1/invitations/" + token,
                item.createdAt());
          });
    } finally {
      lock.unlock();
    }
  }

  public void validate(String token) {
    if (token.length() > 128) throw ApiException.notFound();
    invitations.findUnused(Secrets.hash(token)).orElseThrow(ApiException::notFound);
  }

  public AuthService.LoginResult register(String token, AuthDtos.Credentials request) {
    if (token.length() > 128) throw ApiException.notFound();
    var passwordHash = passwords.encode(request.password());
    lock.lock();
    try {
      return tx.execute(
          status -> {
            var invite =
                invitations
                    .findUnused(Secrets.hash(token))
                    .orElseThrow(
                        () ->
                            ApiException.conflict(
                                "INVITATION_INVALID", "Invitation is invalid or used"));
            var now = clock.instant();
            var user =
                new User(
                    UUID.randomUUID(),
                    invite.internalName(),
                    request.nickname(),
                    passwordHash,
                    User.Role.USER,
                    User.Status.ACTIVE,
                    now,
                    now,
                    null);
            if (!invitations.consume(invite.id(), user.id(), now))
              throw ApiException.conflict("INVITATION_USED", "Invitation already used");
            users.insert(user);
            audit.record(INVITATION_USED, SUCCESS, user.id(), "INVITATION", invite.id());
            audit.record(USER_CREATED, SUCCESS, user.id(), "USER", user.id());
            return auth.session(user);
          });
    } finally {
      lock.unlock();
    }
  }
}
