USE pam;

INSERT INTO users (username, email, display_name)
VALUES
  ('demo_driver', 'demo@example.com', 'Demo Driver'),
  ('track_day', 'track@example.com', 'Track Day');

INSERT INTO user_credentials (user_id, password_hash, password_algo)
VALUES
  (1, '$argon2id$v=19$m=65536,t=3,p=2$demo$hash_demo_1', 'argon2id'),
  (2, '$argon2id$v=19$m=65536,t=3,p=2$demo$hash_demo_2', 'argon2id');

INSERT INTO oauth_identities (user_id, provider, provider_user_id, email_at_provider)
VALUES
  (1, 'google', 'google-demo-uid-1', 'demo@example.com'),
  (2, 'facebook', 'facebook-demo-uid-2', 'track@example.com');

INSERT INTO vehicles (user_id, nickname, make, model, production_year, vin)
VALUES
  (1, 'Daily GTI', 'Volkswagen', 'Golf GTI', 2019, 'WVWZZZ1KZ9W000001'),
  (2, 'Weekend M', 'BMW', 'M240i', 2021, 'WBA1J71010V000002');

INSERT INTO auth_sessions (user_id, refresh_token_hash, device_label, ip_address, user_agent, expires_at)
VALUES
  (1, 'hash_refresh_demo_1', 'Pixel 7', '192.168.1.20', 'Android-App/1.0', DATE_ADD(NOW(), INTERVAL 30 DAY)),
  (2, 'hash_refresh_demo_2', 'Samsung S23', '192.168.1.21', 'Android-App/1.0', DATE_ADD(NOW(), INTERVAL 30 DAY));

INSERT INTO measurement_attempts
  (client_record_id, user_id, vehicle_id, measured_at, measure_type, mode_label, start_strategy, max_speed_kmh, duration_ms, distance_m)
VALUES
  ('11111111-1111-1111-1111-111111111111', 1, 1, NOW(), 'distance', '1 km', 'countdown_then_measure', 118.50, 285430, 1000.00),
  ('22222222-2222-2222-2222-222222222222', 1, 1, NOW(), 'speed_accel', '0-100 km/h', 'armed_wait_for_motion', 102.30, 6120, 142.80),
  ('33333333-3333-3333-3333-333333333333', 2, 2, NOW(), 'distance', '100 m', 'countdown_then_measure', 45.20, 8230, 100.00);

INSERT INTO sync_events (client_record_id, event_type, message)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'insert', 'seed'),
  ('22222222-2222-2222-2222-222222222222', 'insert', 'seed'),
  ('33333333-3333-3333-3333-333333333333', 'insert', 'seed');
