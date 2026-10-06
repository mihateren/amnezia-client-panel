package org.example.amnezia.controlpanel.api.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.example.amnezia.controlpanel.api.persistence.model.ConfigLink;
import org.example.amnezia.controlpanel.api.persistence.model.Invitation;
import org.example.amnezia.controlpanel.api.persistence.model.RefreshSession;
import org.example.amnezia.controlpanel.api.persistence.repository.ConfigLinkRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.InvitationRepository;
import org.example.amnezia.controlpanel.api.persistence.repository.RefreshSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Checks real SQL and nullable mappings without mocking JDBC. */
class RepositoryTransitionsTest extends BaseComponentTest {
  private final ConfigLinkRepository links;
  private final InvitationRepository invitations;
  private final RefreshSessionRepository sessions;

  @Autowired
  RepositoryTransitionsTest(
      ConfigLinkRepository links,
      InvitationRepository invitations,
      RefreshSessionRepository sessions) {
    this.links = links;
    this.invitations = invitations;
    this.sessions = sessions;
  }

  @Test
  void shouldConsumeConfigLinkOnlyOnceAndRoundTripNullableUsedAt() {
    var now = Instant.parse("2026-01-02T03:04:05.123456789Z");
    var link =
        new ConfigLink(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            now,
            now.plusSeconds(60),
            null);
    links.insert(link);
    assertThat(links.find(link.tokenHash())).contains(link);
    assertThat(links.consume(link.id(), now.plusSeconds(1))).isTrue();
    assertThat(links.consume(link.id(), now.plusSeconds(2))).isFalse();
    assertThat(links.find(link.tokenHash()).orElseThrow().usedAt()).isEqualTo(now.plusSeconds(1));
    assertThat(links.find(UUID.randomUUID().toString())).isEmpty();
  }

  @Test
  void shouldDeleteUnusedInvitationsAndConsumeOnlyOnce() {
    var now = Instant.parse("2026-01-02T03:04:05Z");
    var creator = UUID.randomUUID();
    var consumed =
        new Invitation(
            UUID.randomUUID(), UUID.randomUUID().toString(), "consumed", creator, now, null, null);
    var unused =
        new Invitation(
            UUID.randomUUID(), UUID.randomUUID().toString(), "unused", creator, now, null, null);
    invitations.insert(consumed);
    invitations.insert(unused);
    assertThat(invitations.findUnused(consumed.tokenHash())).contains(consumed);
    assertThat(invitations.consume(consumed.id(), UUID.randomUUID(), now.plusSeconds(1))).isTrue();
    assertThat(invitations.consume(consumed.id(), UUID.randomUUID(), now.plusSeconds(2))).isFalse();
    invitations.deleteUnusedByCreator(creator);
    assertThat(invitations.findUnused(unused.tokenHash())).isEmpty();
    assertThat(invitations.consume(consumed.id(), UUID.randomUUID(), now.plusSeconds(3))).isFalse();
  }

  @Test
  void shouldScopeRefreshSessionBulkRevocationAndDeletionToOwner() {
    var now = Instant.parse("2026-01-02T03:04:05.123456789Z");
    var owner = UUID.randomUUID();
    var first = session(owner, now);
    var second = session(owner, now);
    var other = session(UUID.randomUUID(), now);
    sessions.insert(first);
    sessions.insert(second);
    sessions.insert(other);
    assertThat(sessions.find(first.tokenHash())).contains(first);
    assertThat(sessions.revoke(first.tokenHash(), now.plusSeconds(1))).isTrue();
    assertThat(sessions.revoke(first.tokenHash(), now.plusSeconds(2))).isFalse();
    sessions.revokeUser(owner, now.plusSeconds(3));
    assertThat(sessions.find(first.tokenHash()).orElseThrow().revokedAt())
        .isEqualTo(now.plusSeconds(1));
    assertThat(sessions.find(second.tokenHash()).orElseThrow().revokedAt())
        .isEqualTo(now.plusSeconds(3));
    assertThat(sessions.find(other.tokenHash())).contains(other);
    sessions.deleteUser(owner);
    assertThat(sessions.find(first.tokenHash())).isEmpty();
    assertThat(sessions.find(second.tokenHash())).isEmpty();
    assertThat(sessions.find(other.tokenHash())).contains(other);
  }

  private static RefreshSession session(UUID owner, Instant now) {
    return new RefreshSession(
        UUID.randomUUID(), owner, UUID.randomUUID().toString(), now, now.plusSeconds(60), null);
  }
}
