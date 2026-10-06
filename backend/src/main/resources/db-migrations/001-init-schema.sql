--liquibase formatted sql

--changeset mihateren:001-initial-schema

-- ============================================================
-- Users
-- Пользователи панели управления.
-- ============================================================

create table users
(
  id               text primary key,
  internal_name    text not null unique,
  nickname         text not null unique,
  password_hash    text not null,
  role             text not null check (role in ('USER', 'ADMIN')),
  status           text not null check (status in ('ACTIVE', 'DISABLED')),
  created_at       text not null,
  updated_at       text not null,
  last_activity_at text
);

-- id — уникальный идентификатор пользователя (UUID).
-- internal_name — внутреннее имя пользователя, задаваемое администратором.
-- nickname — отображаемое имя пользователя.
-- password_hash — хеш пароля.
-- role — роль пользователя: USER или ADMIN.
-- status — состояние пользователя: ACTIVE или DISABLED.
-- created_at — дата и время создания пользователя.
-- updated_at — дата и время последнего изменения пользователя.
-- last_activity_at — дата и время последней активности пользователя.


-- ============================================================
-- Invitations
-- Приглашения для регистрации новых пользователей.
-- ============================================================

create table invitations
(
  id            text primary key,
  token_hash    text not null unique,
  internal_name text not null,
  created_by    text not null,
  created_at    text not null,
  used_at       text,
  used_by       text
);

-- id — уникальный идентификатор приглашения (UUID).
-- token_hash — хеш одноразового токена приглашения.
-- internal_name — внутреннее имя пользователя, которое будет создано по приглашению.
-- created_by — идентификатор администратора, создавшего приглашение.
-- created_at — дата и время создания приглашения.
-- used_at — дата и время использования приглашения.
-- used_by — идентификатор пользователя, зарегистрированного по приглашению.


-- ============================================================
-- Devices
-- VPN-устройства пользователей.
-- ============================================================

create table devices
(
  id                     text primary key,
  user_id                text not null,
  name                   text not null,
  vpn_ip                 text not null,
  client_public_key      text not null unique,
  client_private_key_enc text not null,
  created_at             text not null
);

-- id — уникальный идентификатор устройства (UUID).
-- user_id — идентификатор владельца устройства.
-- name — пользовательское имя устройства.
-- vpn_ip — выделенный устройству VPN-адрес.
-- client_public_key — публичный ключ клиента AmneziaWG.
-- client_private_key_enc — зашифрованный приватный ключ клиента.
-- created_at — дата и время создания устройства.


-- ============================================================
-- Config links
-- Одноразовые/временные ссылки для получения VPN-конфигурации.
-- ============================================================

create table config_links
(
  id         text primary key,
  device_id  text not null,
  token_hash text not null unique,
  created_at text not null,
  expires_at text not null,
  used_at    text
);

-- id — уникальный идентификатор ссылки (UUID).
-- device_id — идентификатор устройства, конфигурация которого доступна по ссылке.
-- token_hash — хеш секретного токена ссылки.
-- created_at — дата и время создания ссылки.
-- expires_at — дата и время истечения срока действия ссылки.
-- used_at — дата и время использования ссылки.


-- ============================================================
-- Audit log
-- Журнал действий пользователей и администраторов.
-- ============================================================

create table audit_log
(
  id             text primary key,
  event_type     text not null,
  result         text not null,
  actor_user_id  text,
  object_type    text,
  object_id      text,
  created_at     text not null,
  correlation_id text
);

-- id — уникальный идентификатор события аудита (UUID).
-- event_type — тип события.
-- result — результат операции, например SUCCESS или FAILURE.
-- actor_user_id — идентификатор пользователя, инициировавшего действие.
-- object_type — тип объекта, над которым выполнено действие.
-- object_id — идентификатор объекта, над которым выполнено действие.
-- created_at — дата и время возникновения события.
-- correlation_id — идентификатор корреляции запроса.


-- ============================================================
-- Indexes
-- ============================================================

create index idx_invitations_created_by on invitations (created_by);

create index idx_invitations_used_by on invitations (used_by);

create index idx_devices_user_id on devices (user_id);

create index idx_config_links_device_id on config_links (device_id);

create index idx_audit_log_created_at on audit_log (created_at);

create index idx_audit_log_actor_user_id on audit_log (actor_user_id);

create index idx_audit_log_object on audit_log (object_type, object_id);