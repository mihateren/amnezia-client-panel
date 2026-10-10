--liquibase formatted sql
--changeset codex:008-vpn-server-settings
ALTER TABLE vpn_migration ADD COLUMN original_ip TEXT;
CREATE TABLE vpn_server_settings (
 id INTEGER PRIMARY KEY CHECK (id = 1),
 ip TEXT NOT NULL,
 ssh_port INTEGER NOT NULL CHECK (ssh_port BETWEEN 1 AND 65535),
 username TEXT NOT NULL,
 private_key_enc TEXT,
 host_key TEXT,
 vpn_port INTEGER NOT NULL CHECK (vpn_port BETWEEN 1 AND 65535)
);
