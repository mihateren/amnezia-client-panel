--liquibase formatted sql
--changeset codex:002-refresh-and-integrity
-- Refresh sessions store a hash, user association and absolute expiry. Consumed tokens remain revoked.
CREATE TABLE refresh_sessions (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL,
 token_hash TEXT NOT NULL UNIQUE,
 created_at TEXT NOT NULL,
 expires_at TEXT NOT NULL,
 revoked_at TEXT
);
-- Lookup and bulk revocation by owner.
CREATE INDEX idx_refresh_user ON refresh_sessions(user_id);
CREATE INDEX idx_refresh_expiry ON refresh_sessions(expires_at);
-- The configured deployment manages a single IPv4 subnet.
CREATE UNIQUE INDEX idx_devices_vpn_ip ON devices(vpn_ip);
CREATE INDEX idx_users_created ON users(created_at,id);
CREATE INDEX idx_devices_created ON devices(created_at,id);
-- Audit history is append-only, including after deleting an account.
--changeset codex:003-audit-immutable splitStatements:false
CREATE TRIGGER audit_no_update BEFORE UPDATE ON audit_log BEGIN SELECT RAISE(ABORT, 'Audit is append-only'); END;
--changeset codex:004-audit-no-delete splitStatements:false
CREATE TRIGGER audit_no_delete BEFORE DELETE ON audit_log BEGIN SELECT RAISE(ABORT, 'Audit is append-only'); END;
