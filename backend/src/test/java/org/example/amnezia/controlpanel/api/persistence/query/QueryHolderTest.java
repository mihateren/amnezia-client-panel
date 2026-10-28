package org.example.amnezia.controlpanel.api.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.amnezia.controlpanel.api.support.TestQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;

class QueryHolderTest {
  private static final Map<QueryHolder, Set<String>> EXPECTED =
      Map.ofEntries(
          entry(VpnMigrationQuery.FIND, ""),
          entry(VpnMigrationQuery.UPDATE, "generation maintenance originalIp"),
          entry(VpnServerQuery.FIND, ""),
          entry(ManagedVpnServerQuery.ALL, ""),
          entry(ManagedVpnServerQuery.FIND, "id"),
          entry(
              ManagedVpnServerQuery.INSERT,
              "id name ip sshPort username passwordEncrypted hostKey container interfaceName subnet"
                  + " vpnPort enabled createdAt"),
          entry(ManagedVpnServerQuery.UPDATE, "id name enabled passwordEncrypted"),
          entry(ManagedVpnServerQuery.ADOPT_LEGACY, "id deviceId"),
          entry(VpnServerQuery.SAVE, "ip sshPort username privateKeyEncrypted hostKey vpnPort"),
          entry(DatabaseMetricQuery.DATABASE_STORAGE, ""),
          entry(DatabaseMetricQuery.TABLE_STORAGE, ""),
          entry(UserQuery.FIND_BY_ID, "userId"),
          entry(UserQuery.FIND_BY_NICKNAME, "nickname"),
          entry(
              UserQuery.INSERT,
              "userId internalName nickname passwordHash role status createdAt updatedAt"),
          entry(UserQuery.COUNT, ""),
          entry(UserQuery.UPDATE_NICKNAME, "userId nickname now"),
          entry(UserQuery.UPDATE_PASSWORD, "userId passwordHash now"),
          entry(UserQuery.UPDATE_ACTIVITY, "userId now"),
          entry(UserQuery.UPDATE_STATUS, "userId status now"),
          entry(UserQuery.DELETE, "userId"),
          entry(UserQuery.FIND_ALL, "status search limit offset"),
          entry(UserQuery.COUNT_FILTERED, "status search"),
          entry(InvitationQuery.INSERT, "id tokenHash internalName createdBy createdAt"),
          entry(InvitationQuery.FIND_UNUSED, "tokenHash"),
          entry(InvitationQuery.CONSUME, "id userId now"),
          entry(InvitationQuery.DELETE_UNUSED_BY_CREATOR, "createdBy"),
          entry(DeviceQuery.FIND_BY_ID, "deviceId"),
          entry(DeviceQuery.FIND_BY_USER_ID, "userId"),
          entry(DeviceQuery.FIND_ALL, ""),
          entry(DeviceQuery.FIND_METRIC_PEERS, ""),
          entry(DeviceQuery.COUNT_BY_USER_ID, "userId"),
          entry(DeviceQuery.COUNT, ""),
          entry(
              DeviceQuery.INSERT,
              "deviceId userId name icon vpnIp publicKey privateKeyEncrypted createdAt generation"
                  + " serverId clientType"),
          entry(DeviceQuery.UPDATE_METADATA, "deviceId name icon"),
          entry(DeviceQuery.DELETE_LINKS, "deviceId"),
          entry(DeviceQuery.DELETE, "deviceId"),
          entry(ConfigLinkQuery.INSERT, "id deviceId tokenHash createdAt expiresAt"),
          entry(ConfigLinkQuery.FIND_BY_TOKEN_HASH, "tokenHash"),
          entry(ConfigLinkQuery.CONSUME, "id now"),
          entry(RefreshSessionQuery.INSERT, "id userId tokenHash createdAt expiresAt"),
          entry(RefreshSessionQuery.FIND_BY_TOKEN_HASH, "tokenHash"),
          entry(RefreshSessionQuery.REVOKE, "tokenHash now"),
          entry(RefreshSessionQuery.REVOKE_USER, "userId now"),
          entry(RefreshSessionQuery.DELETE_USER, "userId"),
          entry(
              AuditQuery.INSERT,
              "id eventType result actorUserId objectType objectId createdAt correlationId"),
          entry(AuditQuery.FIND_ALL, "eventType actorUserId result from to limit offset"),
          entry(AuditQuery.COUNT, "eventType actorUserId result from to"));

  private static Map.Entry<QueryHolder, Set<String>> entry(QueryHolder query, String names) {
    return Map.entry(query, names.isEmpty() ? Set.of() : Set.of(names.split(" ")));
  }

  @Test
  void shouldLoadEveryResourceAndBindExactlyTheRepositoryParameters() {
    assertThat(EXPECTED.keySet()).containsExactlyInAnyOrderElementsOf(QueryCatalog.all());
    EXPECTED.forEach(
        (query, names) -> {
          assertThat(query.query()).as(query.toString()).isNotBlank().isSameAs(query.query());
          var params = new MapSqlParameterSource();
          names.forEach(name -> params.addValue(name, null));
          var parsed = NamedParameterUtils.parseSqlStatement(query.query());
          Set<String> actual =
              NamedParameterUtils.buildSqlParameterList(parsed, params).stream()
                  .map(SqlParameter::getName)
                  .collect(Collectors.toSet());
          assertThat(actual).as(query.toString()).isEqualTo(names);
          NamedParameterUtils.buildValueArray(parsed, params, null);
          assertThat(withoutLiteralsAndComments(query.query())).doesNotContain("?");
        });
  }

  @Test
  void shouldFailFastWithTheMissingOrEmptyResourcePath() {
    assertThatThrownBy(() -> SqlResourceLoader.load("sql/missing-resource.sql"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sql/missing-resource.sql");
    assertThatThrownBy(() -> SqlResourceLoader.load("sql/parser/empty.sql"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sql/parser/empty.sql");
  }

  @Test
  void shouldIgnorePlaceholdersInsideSqlStringsAndComments() {
    assertThat(withoutLiteralsAndComments(TestQuery.QUOTED_PLACEHOLDERS.query()))
        .doesNotContain("?");
    assertThat(withoutLiteralsAndComments(TestQuery.POSITIONAL_PLACEHOLDER.query())).contains("?");
  }

  private static String withoutLiteralsAndComments(String sql) {
    return sql.replaceAll("(?s)'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|--[^\\r\\n]*|/\\*.*?\\*/", " ");
  }
}
