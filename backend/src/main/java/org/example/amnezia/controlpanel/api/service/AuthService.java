package org.example.amnezia.controlpanel.api.service;

import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.ADMIN_LOGIN;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.USER_LOGIN;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.EventType.USER_LOGOUT;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.FAILURE;
import static org.example.amnezia.controlpanel.api.persistence.model.AuditEvent.Result.SUCCESS;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.example.amnezia.controlpanel.api.audit.AuditService;
import org.example.amnezia.controlpanel.api.config.AppProperties;
import org.example.amnezia.controlpanel.api.dto.AuthDtos;
import org.example.amnezia.controlpanel.api.exception.ApiException;
import org.example.amnezia.controlpanel.api.persistence.model.RefreshSession;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.RefreshSessionRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.example.amnezia.controlpanel.api.security.JwtService;
import org.example.amnezia.controlpanel.api.security.Secrets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
  private final UserRepository users;
  private final RefreshSessionRepository sessions;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  private final AuditService audit;
  private final Clock clock;
  private final AppProperties config;
  private final TransactionTemplate tx;
  private final ReentrantLock lock;
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      RefreshSessionRepository sessions,
      PasswordEncoder passwords,
      JwtService jwt,
      AuditService audit,
      Clock clock,
      AppProperties config,
      TransactionTemplate tx,
      ReentrantLock lock) {
    this.users = users;
    this.sessions = sessions;
    this.passwords = passwords;
    this.jwt = jwt;
    this.audit = audit;
    this.clock = clock;
    this.config = config;
    this.tx = tx;
    this.lock = lock;
    this.dummyHash = passwords.encode(Secrets.token());
  }

  public record LoginResult(AuthDtos.AuthResponse response, String refreshToken) {
    @Override
    public String toString() {
      return "LoginResult[redacted]";
    }
  }

  public User active(UUID id) {
    var user = users.find(id).orElseThrow(ApiException::unauthorized);
    if (user.status() != User.Status.ACTIVE) throw ApiException.unauthorized();
    if (user.lastActivityAt() == null
        || user.lastActivityAt().isBefore(clock.instant().minusSeconds(60))) {
      users.activity(id, clock.instant());
    }
    return user;
  }

  public LoginResult login(AuthDtos.Credentials request) {
    lock.lock();
    try {
      var user = users.findByNickname(request.nickname()).orElse(null);
      boolean matches =
          passwords.matches(request.password(), user == null ? dummyHash : user.passwordHash());
      if (user == null || !matches || user.status() != User.Status.ACTIVE) {
        audit.record(
            USER_LOGIN,
            FAILURE,
            user == null ? null : user.id(),
            "USER",
            user == null ? null : user.id());
        throw ApiException.unauthorized();
      }
      return tx.execute(
          status -> {
            users.activity(user.id(), clock.instant());
            audit.record(
                user.role() == User.Role.ADMIN ? ADMIN_LOGIN : USER_LOGIN,
                SUCCESS,
                user.id(),
                "USER",
                user.id());
            return session(user);
          });
    } finally {
      lock.unlock();
    }
  }

  public LoginResult session(User user) {
    var raw = Secrets.token();
    var now = clock.instant();
    sessions.insert(
        new RefreshSession(
            UUID.randomUUID(),
            user.id(),
            Secrets.hash(raw),
            now,
            now.plus(config.refreshTtl()),
            null));
    return new LoginResult(
        new AuthDtos.AuthResponse(jwt.issue(user.id()), AuthDtos.CurrentUser.from(user)), raw);
  }

  public LoginResult refresh(String raw) {
    if (raw == null || raw.length() > 128) throw ApiException.unauthorized();
    lock.lock();
    try {
      return tx.execute(
          status -> {
            var old = sessions.find(Secrets.hash(raw)).orElseThrow(ApiException::unauthorized);
            if (old.revokedAt() != null || !old.expiresAt().isAfter(clock.instant()))
              throw ApiException.unauthorized();
            var user = active(old.userId());
            if (!sessions.revoke(old.tokenHash(), clock.instant()))
              throw ApiException.unauthorized();
            return session(user);
          });
    } finally {
      lock.unlock();
    }
  }

  public void logout(UUID actor, String raw) {
    lock.lock();
    try {
      tx.executeWithoutResult(
          status -> {
            if (raw != null && raw.length() <= 128) {
              sessions
                  .find(Secrets.hash(raw))
                  .filter(s -> s.userId().equals(actor))
                  .ifPresent(s -> sessions.revoke(s.tokenHash(), clock.instant()));
            }
            audit.record(USER_LOGOUT, SUCCESS, actor, "USER", actor);
          });
    } finally {
      lock.unlock();
    }
  }
}
