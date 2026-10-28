--liquibase formatted sql
--changeset codex:009-multiple-vpn-servers
CREATE TABLE vpn_servers (
 id TEXT PRIMARY KEY,
 name TEXT NOT NULL,
 ip TEXT NOT NULL,
 ssh_port INTEGER NOT NULL CHECK (ssh_port BETWEEN 1 AND 65535),
 username TEXT NOT NULL,
 password_enc TEXT NOT NULL,
 host_key TEXT NOT NULL,
 container TEXT NOT NULL,
 interface_name TEXT NOT NULL,
 subnet TEXT NOT NULL,
 vpn_port INTEGER NOT NULL CHECK (vpn_port BETWEEN 1 AND 65535),
 enabled INTEGER NOT NULL CHECK (enabled IN (0,1)),
 created_at TEXT NOT NULL,
 UNIQUE(ip, ssh_port, container)
);
ALTER TABLE devices ADD COLUMN server_id TEXT;
ALTER TABLE devices ADD COLUMN client_type TEXT NOT NULL DEFAULT 'AMNEZIA_VPN'
 CHECK (client_type IN ('AMNEZIA_VPN','AMNEZIA_WG'));
DROP INDEX idx_devices_generation_vpn_ip;
CREATE UNIQUE INDEX idx_devices_server_generation_ip ON devices(COALESCE(server_id,''),generation,vpn_ip);
CREATE INDEX idx_devices_server ON devices(server_id);
