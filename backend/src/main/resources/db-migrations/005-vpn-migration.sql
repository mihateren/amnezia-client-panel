--liquibase formatted sql
--changeset codex:007-vpn-migration
CREATE TABLE vpn_migration (
 id INTEGER PRIMARY KEY CHECK (id = 1),
 generation INTEGER NOT NULL CHECK (generation > 0),
 maintenance INTEGER NOT NULL CHECK (maintenance IN (0, 1))
);
INSERT INTO vpn_migration (id, generation, maintenance) VALUES (1, 1, 0);
ALTER TABLE devices ADD COLUMN generation INTEGER NOT NULL DEFAULT 1 CHECK (generation > 0);
DROP INDEX idx_devices_vpn_ip;
CREATE UNIQUE INDEX idx_devices_generation_vpn_ip ON devices(generation, vpn_ip);
