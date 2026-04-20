PAM MySQL schema v2 - opis tabel
================================

1) users
---------
- id: PK
- username: unikalny login
- email: unikalny email
- display_name: nazwa wyswietlana
- is_active: 1/0
- created_at, updated_at

2) user_credentials
-------------------
- user_id: PK + FK -> users.id
- password_hash: hash hasla
- password_algo: np. argon2id
- password_updated_at

3) auth_sessions
----------------
- id: PK
- user_id: FK -> users.id
- refresh_token_hash: unikalny hash tokenu odswiezania
- device_label, ip_address, user_agent
- issued_at, expires_at, revoked_at

4) vehicles
-----------
- id: PK
- user_id: FK -> users.id
- nickname, make, model, production_year, vin
- created_at

5) measurement_attempts
-----------------------
- id: PK
- client_record_id: unikalny UUID z aplikacji (idempotencja uploadu)
- user_id: opcjonalny FK -> users.id
- vehicle_id: opcjonalny FK -> vehicles.id
- measured_at
- measure_type: distance | speed_accel
- mode_label: np. 1 km, 0-100 km/h
- start_strategy: countdown_then_measure | armed_wait_for_motion
- max_speed_kmh, duration_ms, distance_m
- created_at, updated_at

6) sync_events
--------------
- id: PK
- client_record_id
- event_type: insert | update | error
- message
- created_at

Przykladowe zapytania
=====================

-- Ostatnie proby:
SELECT id, client_record_id, measured_at, measure_type, mode_label, duration_ms, distance_m
FROM measurement_attempts
ORDER BY measured_at DESC
LIMIT 20;

-- Najlepsze czasy 0-100:
SELECT mode_label, MIN(duration_ms) AS best_ms
FROM measurement_attempts
WHERE measure_type = 'speed_accel'
GROUP BY mode_label
ORDER BY best_ms ASC;

-- Proby dla danego usera:
SELECT u.username, m.mode_label, m.duration_ms, m.max_speed_kmh
FROM measurement_attempts m
JOIN users u ON u.id = m.user_id
WHERE u.username = 'demo_driver'
ORDER BY m.measured_at DESC;
