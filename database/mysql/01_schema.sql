-- PAM minimal schema: tylko dane faktycznie używane przez aplikację.
-- MySQL/MariaDB (pod phpMyAdmin).

CREATE DATABASE IF NOT EXISTS pam
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pam;

-- Użytkownicy aplikacji.
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NULL,
  role ENUM('user', 'admin') NOT NULL DEFAULT 'user',
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Dane logowania (hash hasła trzymamy oddzielnie od profilu użytkownika).
CREATE TABLE IF NOT EXISTS user_credentials (
  user_id BIGINT UNSIGNED NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  password_plain VARCHAR(255) NULL,
  password_algo VARCHAR(32) NOT NULL DEFAULT 'argon2id',
  password_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Metody/typy prób.
CREATE TABLE IF NOT EXISTS measurement_methods (
  code VARCHAR(32) NOT NULL,
  label VARCHAR(64) NOT NULL,
  description VARCHAR(255) NULL,
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Konkretne tryby/presety dostępne w aplikacji (np. 100 m, 1 km, 0-100 km/h).
CREATE TABLE IF NOT EXISTS measurement_modes (
  code VARCHAR(48) NOT NULL,
  method_code VARCHAR(32) NOT NULL,
  label VARCHAR(64) NOT NULL,
  target_distance_m DECIMAL(10,2) NULL,
  target_speed_kmh DECIMAL(7,2) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (code),
  KEY idx_measurement_modes_method (method_code),
  CONSTRAINT fk_measurement_modes_method FOREIGN KEY (method_code)
    REFERENCES measurement_methods (code) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Historia prób pomiarowych (zapisywana przez backend sync z Androida).
CREATE TABLE IF NOT EXISTS measurement_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_record_id CHAR(36) NOT NULL COMMENT 'UUID z aplikacji; idempotencja uploadu',
  user_id BIGINT UNSIGNED NULL,
  owner_username VARCHAR(64) NULL COMMENT 'Snapshot nazwy użytkownika z chwili pomiaru',
  measured_at_epoch_ms BIGINT UNSIGNED NOT NULL COMMENT 'Timestamp UTC z aplikacji (ms)',
  measured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  measure_type VARCHAR(32) NOT NULL COMMENT 'distance | speed_accel',
  mode_label VARCHAR(128) NOT NULL COMMENT 'np. 1 km, 100 m, 0-100 km/h',
  start_strategy VARCHAR(32) NULL COMMENT 'countdown_then_measure | armed_wait_for_motion',
  max_speed_kmh DECIMAL(7, 2) NOT NULL DEFAULT 0.00,
  duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
  distance_m DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
  speed_profile_json JSON NULL COMMENT 'Profil prędkości [[tMs,vKmh], ...]',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_measurement_attempts_client_record_id (client_record_id),
  KEY idx_measurement_attempts_measured_at_epoch_ms (measured_at_epoch_ms),
  KEY idx_measurement_attempts_measured_at (measured_at),
  KEY idx_measurement_attempts_type (measure_type),
  KEY idx_measurement_attempts_duration (duration_ms),
  KEY idx_measurement_attempts_distance (distance_m),
  KEY idx_measurement_attempts_user (user_id),
  KEY idx_measurement_attempts_owner_username (owner_username),
  KEY idx_measurement_attempts_method (measure_type),
  CONSTRAINT fk_measurement_attempts_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_measurement_attempts_method FOREIGN KEY (measure_type)
    REFERENCES measurement_methods (code) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Widok do czytelnego przeglądu historii (UI/admin/debug SQL).
CREATE OR REPLACE VIEW vw_measurement_attempts_readable AS
SELECT
  m.id,
  m.client_record_id,
  COALESCE(m.owner_username, u.username, 'offline') AS owner_name,
  m.measure_type,
  m.mode_label,
  m.start_strategy,
  m.max_speed_kmh,
  m.duration_ms,
  ROUND(m.duration_ms / 1000.0, 2) AS duration_s,
  m.distance_m,
  m.measured_at_epoch_ms,
  m.measured_at,
  m.created_at,
  m.updated_at
FROM measurement_attempts m
LEFT JOIN users u ON u.id = m.user_id;
