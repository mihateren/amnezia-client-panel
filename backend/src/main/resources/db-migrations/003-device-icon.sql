--liquibase formatted sql
--changeset codex:005-device-icon
ALTER TABLE devices ADD COLUMN icon TEXT NOT NULL DEFAULT 'LAPTOP'
CHECK (icon IN ('DESKTOP', 'LAPTOP', 'PHONE', 'TABLET', 'ROUTER'));
