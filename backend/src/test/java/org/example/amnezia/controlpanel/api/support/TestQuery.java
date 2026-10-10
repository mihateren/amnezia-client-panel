package org.example.amnezia.controlpanel.api.support;

import org.example.amnezia.controlpanel.api.persistence.query.QueryHolder;
import org.example.amnezia.controlpanel.api.persistence.query.SqlResourceLoader;

/** Cached classpath SQL used only by test fixtures and characterization checks. */
public enum TestQuery implements QueryHolder {
  BLOCK_PROFILE_AUDIT("sql/security/block-profile-audit.sql"),
  UNBLOCK_PROFILE_AUDIT("sql/security/unblock-profile-audit.sql"),
  DELETE_VPN_SERVER_SETTINGS("sql/device/delete-vpn-server-settings.sql"),
  FIND_LEGACY_ICON("sql/device/find-legacy-icon.sql"),
  INSERT_LEGACY_DEVICE("sql/device/insert-legacy-device.sql"),
  BLOCK_DEVICE_UPDATE_AUDIT("sql/device/block-update-audit.sql"),
  UNBLOCK_DEVICE_UPDATE_AUDIT("sql/device/unblock-update-audit.sql"),
  INVITATION_TOKEN_HASH("sql/security/invitation-token-hash.sql"),
  EXPIRE_REFRESH_SESSION("sql/security/expire-refresh-session.sql"),
  INSERT_AUDIT("sql/security/insert-audit.sql"),
  UPDATE_AUDIT("sql/security/update-audit.sql"),
  DELETE_AUDIT("sql/security/delete-audit.sql"),
  INVALID_USER_ROLE("sql/repository/invalid-user-role.sql"),
  FOREIGN_KEYS("sql/repository/foreign-keys.sql"),
  CHANGELOG_COUNT("sql/repository/changelog-count.sql"),
  TABLE_NAMES("sql/repository/table-names.sql"),
  RAW_REFRESH_TOKEN_COUNT("sql/repository/raw-refresh-token-count.sql"),
  DELETE_CONFIG_LINKS("sql/device/delete-config-links.sql"),
  DELETE_DEVICES("sql/device/delete-devices.sql"),
  RENAME_DEVICE("sql/device/rename-device.sql"),
  RENAME_USER("sql/device/rename-user.sql"),
  EXPIRE_CONFIG_LINKS("sql/device/expire-config-links.sql"),
  STARTUP_TABLE_NAMES("sql/startup/table-names.sql"),
  CHANGELOG_IDS("sql/startup/changelog-ids.sql"),
  CREATE_METRIC_SAMPLE("sql/metrics/create-sample.sql"),
  INSERT_METRIC_SAMPLE("sql/metrics/insert-sample.sql"),
  INDEX_METRIC_SAMPLE("sql/metrics/index-sample.sql"),
  DROP_METRIC_SAMPLE("sql/metrics/drop-sample.sql"),
  QUOTED_PLACEHOLDERS("sql/parser/quoted-placeholders.sql"),
  POSITIONAL_PLACEHOLDER("sql/parser/positional-placeholder.sql");

  private final String sql;

  TestQuery(String path) {
    this.sql = SqlResourceLoader.load(path);
  }

  @Override
  public String query() {
    return sql;
  }
}
