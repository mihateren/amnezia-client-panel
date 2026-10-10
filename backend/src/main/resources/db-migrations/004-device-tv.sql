--liquibase formatted sql
--changeset codex:006-device-tv
CREATE TABLE devices_with_tv (
 id TEXT PRIMARY KEY,
 user_id TEXT NOT NULL,
 name TEXT NOT NULL,
 vpn_ip TEXT NOT NULL,
 client_public_key TEXT NOT NULL UNIQUE,
 client_private_key_enc TEXT NOT NULL,
 created_at TEXT NOT NULL,
 icon TEXT NOT NULL DEFAULT 'LAPTOP' CHECK (icon IN ('DESKTOP','LAPTOP','PHONE','TABLET','ROUTER','TV'))
);
INSERT INTO devices_with_tv (id,user_id,name,vpn_ip,client_public_key,client_private_key_enc,created_at,icon)
 SELECT id,user_id,name,vpn_ip,client_public_key,client_private_key_enc,created_at,icon FROM devices;
DROP TABLE devices;
ALTER TABLE devices_with_tv RENAME TO devices;
CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE UNIQUE INDEX idx_devices_vpn_ip ON devices(vpn_ip);
CREATE INDEX idx_devices_created ON devices(created_at,id);
