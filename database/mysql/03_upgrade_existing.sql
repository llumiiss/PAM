-- Uruchom ręcznie, jeśli masz już istniejący wolumen MySQL i chcesz dołożyć nowe kolumny/tabele.
USE pam;

ALTER TABLE measurement_attempts
  ADD COLUMN client_record_id CHAR(36) NULL AFTER id,
  ADD COLUMN user_id BIGINT UNSIGNED NULL AFTER client_record_id,
  ADD COLUMN vehicle_id BIGINT UNSIGNED NULL AFTER user_id,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

UPDATE measurement_attempts
SET client_record_id = UUID()
WHERE client_record_id IS NULL OR client_record_id = '';

ALTER TABLE measurement_attempts
  MODIFY client_record_id CHAR(36) NOT NULL;

ALTER TABLE measurement_attempts
  ADD UNIQUE KEY uq_measurement_attempts_client_record_id (client_record_id);

CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_email (email)
);

CREATE TABLE IF NOT EXISTS user_credentials (
  user_id BIGINT UNSIGNED NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  password_algo VARCHAR(32) NOT NULL DEFAULT 'argon2id',
  password_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS auth_sessions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  refresh_token_hash VARCHAR(255) NOT NULL,
  device_label VARCHAR(128) NULL,
  ip_address VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NOT NULL,
  revoked_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_auth_sessions_refresh_hash (refresh_token_hash),
  KEY idx_auth_sessions_user (user_id),
  CONSTRAINT fk_auth_sessions_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS vehicles (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  nickname VARCHAR(64) NULL,
  make VARCHAR(64) NULL,
  model VARCHAR(64) NULL,
  production_year SMALLINT UNSIGNED NULL,
  vin VARCHAR(32) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_vehicles_vin (vin),
  KEY idx_vehicles_user (user_id),
  CONSTRAINT fk_vehicles_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sync_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_record_id CHAR(36) NOT NULL,
  event_type ENUM('insert', 'update', 'error') NOT NULL,
  message VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sync_events_client_record_id (client_record_id),
  KEY idx_sync_events_created_at (created_at)
);
