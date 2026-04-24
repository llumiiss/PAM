USE pam;

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
  ('22222222-2222-2222-2222-222222222222', 3, 'admin', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), 'speed_accel', '0-100 km/h', 'armed_wait_for_motion', 101.10, 5890, 138.40, JSON_ARRAY(JSON_ARRAY(0, 0), JSON_ARRAY(2000, 50.2), JSON_ARRAY(5890, 100.0))),
  ('33333333-3333-3333-3333-333333333333', 1, 'lumis', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), 'distance', '500 m', 'countdown_then_measure', 92.40, 18420, 500.00, JSON_ARRAY(JSON_ARRAY(0, 0), JSON_ARRAY(3000, 42.3), JSON_ARRAY(18420, 92.4))),
  ('44444444-4444-4444-4444-444444444444', 2, 'hania', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), 'speed_accel', '0-75 km/h', 'armed_wait_for_motion', 77.20, 5120, 97.00, JSON_ARRAY(JSON_ARRAY(0, 0), JSON_ARRAY(2500, 49.4), JSON_ARRAY(5120, 75.0)));
