-- Reset istniejącej bazy do minimalnego, wspieranego modelu danych.
-- Uwaga: skrypt usuwa stare tabele i dane.

CREATE DATABASE IF NOT EXISTS pam
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pam;

DROP VIEW IF EXISTS vw_measurement_attempts_readable;

DROP TABLE IF EXISTS sync_events;
DROP TABLE IF EXISTS oauth_identities;
DROP TABLE IF EXISTS auth_sessions;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS measurement_attempts;
DROP TABLE IF EXISTS measurement_modes;
DROP TABLE IF EXISTS user_credentials;
DROP TABLE IF EXISTS measurement_methods;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
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

CREATE TABLE user_credentials (
  user_id BIGINT UNSIGNED NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  password_plain VARCHAR(255) NULL,
  password_algo VARCHAR(32) NOT NULL DEFAULT 'argon2id',
  password_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE measurement_methods (
  code VARCHAR(32) NOT NULL,
  label VARCHAR(64) NOT NULL,
  description VARCHAR(255) NULL,
  PRIMARY KEY (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE measurement_modes (
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

CREATE TABLE measurement_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_record_id CHAR(36) NOT NULL,
  user_id BIGINT UNSIGNED NULL,
  owner_username VARCHAR(64) NULL,
  measured_at_epoch_ms BIGINT UNSIGNED NOT NULL,
  measured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  measure_type VARCHAR(32) NOT NULL,
  mode_label VARCHAR(128) NOT NULL,
  start_strategy VARCHAR(32) NULL,
  max_speed_kmh DECIMAL(7, 2) NOT NULL DEFAULT 0.00,
  duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
  distance_m DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
  speed_profile_json JSON NULL,
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

INSERT INTO measurement_methods (code, label, description)
VALUES
  ('distance', 'Dystans', 'Pomiar czasu na zadanym dystansie'),
  ('speed_accel', 'Przyspieszenie', 'Pomiar 0->V do zadanego progu prędkości');

INSERT INTO measurement_modes (
  code, method_code, label, target_distance_m, target_speed_kmh, is_active, sort_order
)
VALUES
  ('distance_100m', 'distance', '100 m', 100.00, NULL, 1, 10),
  ('distance_500m', 'distance', '500 m', 500.00, NULL, 1, 20),
  ('distance_1km', 'distance', '1 km', 1000.00, NULL, 1, 30),
  ('distance_quarter_mile', 'distance', '1/4 mili', 402.34, NULL, 1, 40),
  ('accel_0_50', 'speed_accel', '0-50 km/h', NULL, 50.00, 1, 50),
  ('accel_0_75', 'speed_accel', '0-75 km/h', NULL, 75.00, 1, 60),
  ('accel_0_100', 'speed_accel', '0-100 km/h', NULL, 100.00, 1, 70),
  ('accel_0_120', 'speed_accel', '0-120 km/h', NULL, 120.00, 1, 80);

INSERT INTO users (username, email, display_name, role)
VALUES
  ('lumis', 'lumis@gmail.com', 'Lumis', 'user'),
  ('hania', 'hania@yahoo.com', 'Hania', 'user'),
  ('admin', 'admin@example.com', 'Administrator', 'admin');

INSERT INTO user_credentials (user_id, password_hash, password_plain, password_algo)
VALUES
  (1, '$2a$12$YTIDLd0fRja5/J2XZG4SKeU/T1CfSCKjAdrk8ZCeKYEqd.N8irjSK', '380935', 'bcrypt'),
  (2, '$2a$12$PVePmnTyO1hAdBrk6l4VpOqqEzEpDXT0BsXtgW3.ErWxZsLPL41We', '12345', 'bcrypt'),
  (3, '$2a$12$m5Z0hR8Q/QgkBslAVNHmp.0N50krAdihtkRXUrGQ9/jBR81/gjZqi', 'admin', 'bcrypt');

INSERT INTO measurement_attempts
  (client_record_id, user_id, owner_username, measured_at_epoch_ms, measured_at, measure_type, mode_label, start_strategy, max_speed_kmh, duration_ms, distance_m, speed_profile_json)
VALUES
  ('11111111-1111-1111-1111-111111111111', 3, 'admin', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), 'distance', '1 km', 'countdown_then_measure', 134.20, 246500, 1000.00, JSON_ARRAY(JSON_ARRAY(0, 0), JSON_ARRAY(5000, 65.1), JSON_ARRAY(10000, 98.8))),
  ('22222222-2222-2222-2222-222222222222', 3, 'admin', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), 'speed_accel', '0-100 km/h', 'armed_wait_for_motion', 101.10, 5890, 138.40, JSON_ARRAY(JSON_ARRAY(0, 0), JSON_ARRAY(2000, 50.2), JSON_ARRAY(5890, 100.0)));
