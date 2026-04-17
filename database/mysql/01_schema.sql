-- Baza pod Etap 3 (historia prób) — MySQL / MariaDB (phpMyAdmin na localhost).
-- Uwaga: aplikacja Android (Room) używa SQLite na urządzeniu; to jest osobna baza
-- developerska / serwerowa do podglądu, ręcznej edycji i późnej synchronizacji przez API.

CREATE DATABASE IF NOT EXISTS pam
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pam;

CREATE TABLE IF NOT EXISTS measurement_attempts (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  measured_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  measure_type ENUM('distance', 'speed_accel') NOT NULL COMMENT 'dystans vs przyspieszenie 0→X',
  mode_label VARCHAR(128) NOT NULL COMMENT 'np. 1 km, 100 m, 0–100 km/h',
  start_strategy VARCHAR(32) NULL COMMENT 'countdown_then_measure | armed_wait_for_motion',
  max_speed_kmh DECIMAL(7, 2) NOT NULL DEFAULT 0.00 COMMENT 'maksymalna prędkość w przebiegu próby (km/h)',
  duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'czas pomiaru (ms)',
  distance_m DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT 'przejechany dystans (m)',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_measurement_attempts_measured_at (measured_at),
  KEY idx_measurement_attempts_type (measure_type),
  KEY idx_measurement_attempts_duration (duration_ms),
  KEY idx_measurement_attempts_distance (distance_m)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
