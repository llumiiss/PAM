-- PAM schema v2: przygotowane pod historię prób + przyszłych użytkowników i logowanie.
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
  password_algo VARCHAR(32) NOT NULL DEFAULT 'argon2id',
  password_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Sesje logowania / refresh tokeny.
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pojazdy przypisane do użytkownika.
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Historia prób pomiarowych (zapisywana przez backend sync z Androida).
CREATE TABLE IF NOT EXISTS measurement_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_record_id CHAR(36) NOT NULL COMMENT 'UUID z aplikacji; idempotencja uploadu',
  user_id BIGINT UNSIGNED NULL,
  vehicle_id BIGINT UNSIGNED NULL,
  measured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  measure_type ENUM('distance', 'speed_accel') NOT NULL COMMENT 'dystans vs przyspieszenie 0→X',
  mode_label VARCHAR(128) NOT NULL COMMENT 'np. 1 km, 100 m, 0-100 km/h',
  start_strategy VARCHAR(32) NULL COMMENT 'countdown_then_measure | armed_wait_for_motion',
  max_speed_kmh DECIMAL(7, 2) NOT NULL DEFAULT 0.00,
  duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
  distance_m DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_measurement_attempts_client_record_id (client_record_id),
  KEY idx_measurement_attempts_measured_at (measured_at),
  KEY idx_measurement_attempts_type (measure_type),
  KEY idx_measurement_attempts_duration (duration_ms),
  KEY idx_measurement_attempts_distance (distance_m),
  KEY idx_measurement_attempts_user (user_id),
  KEY idx_measurement_attempts_vehicle (vehicle_id),
  CONSTRAINT fk_measurement_attempts_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE SET NULL,
  CONSTRAINT fk_measurement_attempts_vehicle FOREIGN KEY (vehicle_id)
    REFERENCES vehicles (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Pomocniczy log synchronizacji backendu (debug / audyt).
CREATE TABLE IF NOT EXISTS sync_events (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_record_id CHAR(36) NOT NULL,
  event_type ENUM('insert', 'update', 'error') NOT NULL,
  message VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sync_events_client_record_id (client_record_id),
  KEY idx_sync_events_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
