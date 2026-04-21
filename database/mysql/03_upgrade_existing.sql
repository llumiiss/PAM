-- Uruchom ręcznie, jeśli masz już istniejący wolumen MySQL i chcesz dołożyć nowe kolumny/tabele.
USE pam;

SET @has_client_record_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'measurement_attempts'
    AND COLUMN_NAME = 'client_record_id'
);
SET @sql_add_client_record_id := IF(
  @has_client_record_id = 0,
  'ALTER TABLE measurement_attempts ADD COLUMN client_record_id CHAR(36) NULL AFTER id',
  'SELECT 1'
);
PREPARE stmt_add_client_record_id FROM @sql_add_client_record_id;
EXECUTE stmt_add_client_record_id;
DEALLOCATE PREPARE stmt_add_client_record_id;

SET @has_user_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'measurement_attempts'
    AND COLUMN_NAME = 'user_id'
);
SET @sql_add_user_id := IF(
  @has_user_id = 0,
  'ALTER TABLE measurement_attempts ADD COLUMN user_id BIGINT UNSIGNED NULL AFTER client_record_id',
  'SELECT 1'
);
PREPARE stmt_add_user_id FROM @sql_add_user_id;
EXECUTE stmt_add_user_id;
DEALLOCATE PREPARE stmt_add_user_id;

SET @has_vehicle_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'measurement_attempts'
    AND COLUMN_NAME = 'vehicle_id'
);
SET @sql_add_vehicle_id := IF(
  @has_vehicle_id = 0,
  'ALTER TABLE measurement_attempts ADD COLUMN vehicle_id BIGINT UNSIGNED NULL AFTER user_id',
  'SELECT 1'
);
PREPARE stmt_add_vehicle_id FROM @sql_add_vehicle_id;
EXECUTE stmt_add_vehicle_id;
DEALLOCATE PREPARE stmt_add_vehicle_id;

SET @has_updated_at := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'measurement_attempts'
    AND COLUMN_NAME = 'updated_at'
);
SET @sql_add_updated_at := IF(
  @has_updated_at = 0,
  'ALTER TABLE measurement_attempts ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at',
  'SELECT 1'
);
PREPARE stmt_add_updated_at FROM @sql_add_updated_at;
EXECUTE stmt_add_updated_at;
DEALLOCATE PREPARE stmt_add_updated_at;

UPDATE measurement_attempts
SET client_record_id = UUID()
WHERE client_record_id IS NULL OR client_record_id = '';

ALTER TABLE measurement_attempts
  MODIFY client_record_id CHAR(36) NOT NULL;

SET @has_unique_client_record_id := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'measurement_attempts'
    AND INDEX_NAME = 'uq_measurement_attempts_client_record_id'
);
SET @sql_add_unique_client_record_id := IF(
  @has_unique_client_record_id = 0,
  'ALTER TABLE measurement_attempts ADD UNIQUE KEY uq_measurement_attempts_client_record_id (client_record_id)',
  'SELECT 1'
);
PREPARE stmt_add_unique_client_record_id FROM @sql_add_unique_client_record_id;
EXECUTE stmt_add_unique_client_record_id;
DEALLOCATE PREPARE stmt_add_unique_client_record_id;

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
);

SET @has_role_col := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'role'
);
SET @sql_add_role := IF(
  @has_role_col = 0,
  'ALTER TABLE users ADD COLUMN role ENUM(''user'', ''admin'') NOT NULL DEFAULT ''user'' AFTER display_name',
  'SELECT 1'
);
PREPARE stmt_add_role FROM @sql_add_role;
EXECUTE stmt_add_role;
DEALLOCATE PREPARE stmt_add_role;

CREATE TABLE IF NOT EXISTS user_credentials (
  user_id BIGINT UNSIGNED NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  password_algo VARCHAR(32) NOT NULL DEFAULT 'argon2id',
  password_updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_user_credentials_user FOREIGN KEY (user_id)
    REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS oauth_identities (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  provider ENUM('google', 'facebook') NOT NULL,
  provider_user_id VARCHAR(191) NOT NULL,
  email_at_provider VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_oauth_identity_provider_user (provider, provider_user_id),
  KEY idx_oauth_identity_user (user_id),
  CONSTRAINT fk_oauth_identity_user FOREIGN KEY (user_id)
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

SET @has_password_plain := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user_credentials'
    AND COLUMN_NAME = 'password_plain'
);
SET @sql_add_password_plain := IF(
  @has_password_plain = 0,
  'ALTER TABLE user_credentials ADD COLUMN password_plain VARCHAR(255) NULL AFTER password_hash',
  'SELECT 1'
);
PREPARE stmt_add_password_plain FROM @sql_add_password_plain;
EXECUTE stmt_add_password_plain;
DEALLOCATE PREPARE stmt_add_password_plain;
