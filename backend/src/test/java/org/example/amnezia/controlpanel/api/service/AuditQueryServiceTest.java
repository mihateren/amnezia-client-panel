package org.example.amnezia.controlpanel.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AuditActor;
import org.example.amnezia.controlpanel.api.dto.AdminDtos.AuditResponse;
import org.example.amnezia.controlpanel.api.dto.PageResponse;
import org.example.amnezia.controlpanel.api.persistence.model.AuditEvent;
import org.example.amnezia.controlpanel.api.persistence.model.User;
import org.example.amnezia.controlpanel.api.persistence.repository.AuditRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;

class AuditQueryServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
  private final AuditRepository audits = mock(AuditRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final AuthService auth = mock(AuthService.class);
  private final AuditQueryService service = new AuditQueryService(audits, users, auth);
  private final UUID admin = UUID.randomUUID();

  @Test
  void shouldPreserveRepeatedMissingNullActorsAndPageMetadata() {
    authorize();
    var actor = user(UUID.randomUUID(), "nickname");
    var missing = UUID.randomUUID();
    var unnamed = user(UUID.randomUUID(), null);
    var events =
        List.of(
            event(actor.id()),
            event(missing),
            event(null),
            event(actor.id()),
            event(missing),
            event(unnamed.id()),
            event(unnamed.id()));
    when(users.find(actor.id())).thenReturn(Optional.of(actor));
    when(users.find(missing)).thenReturn(Optional.empty());
    when(users.find(unnamed.id())).thenReturn(Optional.of(unnamed));
    when(audits.list(2, 7, null, null, null, null, null))
        .thenReturn(PageResponse.of(events, 2, 7, 29));

    var page = service.list(admin, 2, 7, null, null, null, null, null);

    assertThat(page.content())
        .extracting(item -> item.id())
        .containsExactlyElementsOf(events.stream().map(AuditEvent::id).toList());
    assertThat(page.content())
        .extracting(item -> item.actor())
        .containsExactly(
            new AuditActor(actor.id(), "nickname"),
            new AuditActor(missing, null),
            new AuditActor(null, null),
            new AuditActor(actor.id(), "nickname"),
            new AuditActor(missing, null),
            new AuditActor(unnamed.id(), null),
            new AuditActor(unnamed.id(), null));
    assertThat(page.content().getFirst().object().id()).isEqualTo(events.getFirst().objectId());
    assertThat(page.content().getFirst().correlationId()).isEqualTo("request-1");
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(7);
    assertThat(page.totalElements()).isEqualTo(29);
    assertThat(page.totalPages()).isEqualTo(5);
    verify(users).find(actor.id());
    verify(users).find(missing);
    verify(users).find(unnamed.id());
    verify(users, never()).find(null);
  }

  @Test
  void shouldReadUpdatedAndPreviouslyMissingActorsOnTheNextRequest() {
    authorize();
    var actor = user(UUID.randomUUID(), "old-name");
    var missing = UUID.randomUUID();
    var events = List.of(event(actor.id()), event(actor.id()), event(missing), event(missing));
    when(audits.list(
            0,
            10,
            AuditEvent.EventType.USER_LOGIN,
            actor.id(),
            AuditEvent.Result.SUCCESS,
            NOW.minusSeconds(60),
            NOW))
        .thenReturn(PageResponse.of(events, 0, 10, 4));
    when(users.find(actor.id())).thenReturn(Optional.of(actor));
    when(users.find(missing)).thenReturn(Optional.empty());
    assertThat(filtered(actor.id()).content())
        .extracting(item -> item.actor().nickname())
        .containsExactly("old-name", "old-name", null, null);

    when(users.find(actor.id())).thenReturn(Optional.of(user(actor.id(), "new-name")));
    when(users.find(missing)).thenReturn(Optional.of(user(missing, "restored-name")));
    assertThat(filtered(actor.id()).content())
        .extracting(item -> item.actor().nickname())
        .containsExactly("new-name", "new-name", "restored-name", "restored-name");
    verify(users, times(2)).find(actor.id());
    verify(users, times(2)).find(missing);
  }

  private PageResponse<AuditResponse> filtered(UUID actor) {
    return service.list(
        admin,
        0,
        10,
        AuditEvent.EventType.USER_LOGIN,
        actor,
        AuditEvent.Result.SUCCESS,
        NOW.minusSeconds(60),
        NOW);
  }

  private void authorize() {
    when(auth.active(admin))
        .thenReturn(
            new User(
                admin,
                "Admin",
                "admin",
                "hash",
                User.Role.ADMIN,
                User.Status.ACTIVE,
                NOW,
                NOW,
                null));
  }

  private static User user(UUID id, String nickname) {
    return new User(
        id, "Internal name", nickname, "hash", User.Role.USER, User.Status.ACTIVE, NOW, NOW, null);
  }

  private static AuditEvent event(UUID actor) {
    return new AuditEvent(
        UUID.randomUUID(),
        AuditEvent.EventType.USER_LOGIN,
        AuditEvent.Result.SUCCESS,
        actor,
        "USER",
        UUID.randomUUID(),
        NOW,
        "request-1");
  }
}
