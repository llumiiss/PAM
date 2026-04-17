USE pam;

INSERT INTO measurement_attempts
  (measured_at, measure_type, mode_label, start_strategy, max_speed_kmh, duration_ms, distance_m)
VALUES
  (NOW(), 'distance', '1 km', 'countdown_then_measure', 118.50, 285430, 1000.00),
  (NOW(), 'speed_accel', '0–100 km/h', 'armed_wait_for_motion', 102.30, 6120, 142.80),
  (NOW(), 'distance', '100 m', 'countdown_then_measure', 45.20, 8230, 100.00);
