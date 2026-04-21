"use strict";

const express = require("express");
const cors = require("cors");
const mysql = require("mysql2/promise");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");

const PORT = Number(process.env.PORT || 8090);
const DB_HOST = process.env.DB_HOST || "mysql";
const DB_USER = process.env.DB_USER || "pam";
const DB_PASSWORD = process.env.DB_PASSWORD || "pam_dev";
const DB_NAME = process.env.DB_NAME || "pam";
const JWT_SECRET = process.env.JWT_SECRET || "pam_dev_secret_change_me";
const JWT_TTL = process.env.JWT_TTL || "30d";

function makeAccessToken(user) {
  return jwt.sign(
    { sub: user.id, username: user.username, email: user.email, role: user.role || "user" },
    JWT_SECRET,
    { expiresIn: JWT_TTL }
  );
}

async function optionalAuth(req, _res, next) {
  try {
    const auth = req.headers.authorization || "";
    if (auth.startsWith("Bearer ")) {
      const token = auth.slice("Bearer ".length);
      req.user = jwt.verify(token, JWT_SECRET);
    }
  } catch (_e) {
    req.user = null;
  }
  next();
}

async function requireAuth(req, res, next) {
  await optionalAuth(req, res, () => {});
  if (!req.user?.sub) return res.status(401).json({ error: "unauthorized" });
  return next();
}

async function requireAdmin(req, res, next) {
  await requireAuth(req, res, () => {});
  if (!req.user?.sub) return;
  if (req.user.role !== "admin") return res.status(403).json({ error: "admin only" });
  return next();
}

function isRegistrationEmail(email) {
  const e = String(email).trim();
  return e.includes("@") && e.toLowerCase().includes(".com");
}

async function ensureDefaultAdmin(pool) {
  const passwordHash = await bcrypt.hash("admin", 12);
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const [rows] = await conn.execute(
      `SELECT id FROM users WHERE username = 'admin' LIMIT 1`
    );
    let adminId;
    if (rows.length) {
      adminId = rows[0].id;
      await conn.execute(
        `UPDATE users SET email = ?, display_name = ?, role = 'admin', is_active = 1 WHERE id = ?`,
        ["admin@example.com", "Administrator", adminId]
      );
    } else {
      const [insertResult] = await conn.execute(
        `INSERT INTO users (username, email, display_name, role, is_active) VALUES ('admin', ?, 'Administrator', 'admin', 1)`,
        ["admin@example.com"]
      );
      adminId = insertResult.insertId;
    }

    await conn.execute(
      `INSERT INTO user_credentials (user_id, password_hash, password_algo, password_plain)
       VALUES (?, ?, 'bcrypt', ?)
       ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), password_algo = 'bcrypt', password_plain = VALUES(password_plain), password_updated_at = CURRENT_TIMESTAMP`,
      [adminId, passwordHash, "admin"]
    );
    await conn.commit();
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}

async function main() {
  const pool = mysql.createPool({
    host: DB_HOST,
    user: DB_USER,
    password: DB_PASSWORD,
    database: DB_NAME,
    waitForConnections: true,
    connectionLimit: 10,
  });

  const app = express();
  app.use(cors());
  app.use(express.json({ limit: "64kb" }));

  app.get("/health", (_req, res) => {
    res.json({ ok: true, service: "pam-sync-api" });
  });

  await ensureDefaultAdmin(pool);

  app.post("/api/auth/register", async (req, res) => {
    try {
      const { username, email, password, displayName } = req.body || {};
      if (!username || !email || !password) {
        return res.status(400).json({ error: "username, email, password required" });
      }
      const emailTrim = String(email).trim().toLowerCase();
      if (!isRegistrationEmail(emailTrim)) {
        return res.status(400).json({ error: "email must contain @ and .com" });
      }

      const passwordHash = await bcrypt.hash(String(password), 12);
      const plain = String(password);
      const conn = await pool.getConnection();
      try {
        await conn.beginTransaction();
        const [userResult] = await conn.execute(
          `INSERT INTO users (username, email, display_name, role) VALUES (?, ?, ?, 'user')`,
          [String(username).trim(), emailTrim, displayName ?? null]
        );
        const userId = userResult.insertId;
        await conn.execute(
          `INSERT INTO user_credentials (user_id, password_hash, password_algo, password_plain) VALUES (?, ?, 'bcrypt', ?)`,
          [userId, passwordHash, plain]
        );
        await conn.commit();

        const [rows] = await conn.execute(
          `SELECT id, username, email, display_name AS displayName, role FROM users WHERE id = ?`,
          [userId]
        );
        const user = rows[0];
        const token = makeAccessToken(user);
        return res.status(201).json({ token, user });
      } catch (e) {
        await conn.rollback();
        if (e?.code === "ER_DUP_ENTRY") {
          return res.status(409).json({ error: "username or email already exists" });
        }
        throw e;
      } finally {
        conn.release();
      }
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  app.post("/api/auth/login", async (req, res) => {
    try {
      const { emailOrUsername, password } = req.body || {};
      if (!emailOrUsername || !password) {
        return res.status(400).json({ error: "emailOrUsername and password required" });
      }

      const [rows] = await pool.execute(
        `SELECT u.id, u.username, u.email, u.display_name AS displayName, u.role, c.password_hash AS passwordHash
         FROM users u
         JOIN user_credentials c ON c.user_id = u.id
         WHERE u.email = ? OR u.username = ?
         LIMIT 1`,
        [String(emailOrUsername).trim().toLowerCase(), String(emailOrUsername).trim()]
      );
      if (!rows.length) return res.status(401).json({ error: "invalid credentials" });
      const userRow = rows[0];
      const valid = await bcrypt.compare(String(password), userRow.passwordHash);
      if (!valid) return res.status(401).json({ error: "invalid credentials" });

      const user = {
        id: userRow.id,
        username: userRow.username,
        email: userRow.email,
        displayName: userRow.displayName,
        role: userRow.role,
      };
      const token = makeAccessToken(user);
      return res.json({ token, user });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  app.get("/api/auth/me", requireAuth, async (req, res) => {
    try {
      const [rows] = await pool.execute(
        `SELECT id, username, email, display_name AS displayName, role FROM users WHERE id = ? LIMIT 1`,
        [req.user.sub]
      );
      if (!rows.length) return res.status(404).json({ error: "user not found" });
      return res.json({ user: rows[0] });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  app.get("/api/auth/social/providers", (_req, res) => {
    res.json({
      google: { enabled: false, note: "Wymaga OAuth client ID/secret" },
      facebook: { enabled: false, note: "Wymaga app ID/secret" },
    });
  });

  app.get("/api/admin/users", requireAdmin, async (_req, res) => {
    try {
      const [rows] = await pool.execute(
        `SELECT u.id, u.username, u.email, u.display_name AS displayName, u.role, u.is_active AS isActive,
                (SELECT COUNT(*) FROM measurement_attempts m WHERE m.user_id = u.id) AS attemptsCount,
                c.password_plain AS passwordPlain
         FROM users u
         LEFT JOIN user_credentials c ON c.user_id = u.id
         ORDER BY u.created_at DESC`
      );
      res.json({ users: rows });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  app.delete("/api/admin/users/:id", requireAdmin, async (req, res) => {
    try {
      const targetUserId = Number(req.params.id);
      if (!Number.isFinite(targetUserId) || targetUserId <= 0) {
        return res.status(400).json({ error: "invalid user id" });
      }
      if (targetUserId === Number(req.user.sub)) {
        return res.status(400).json({ error: "cannot delete current admin account" });
      }
      const conn = await pool.getConnection();
      try {
        await conn.beginTransaction();
        await conn.execute(`DELETE FROM measurement_attempts WHERE user_id = ?`, [targetUserId]);
        const [result] = await conn.execute(`DELETE FROM users WHERE id = ?`, [targetUserId]);
        await conn.commit();
        if (result.affectedRows === 0) return res.status(404).json({ error: "user not found" });
        return res.json({ deleted: true });
      } catch (e) {
        await conn.rollback();
        throw e;
      } finally {
        conn.release();
      }
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  app.delete("/api/admin/attempts/:id", requireAdmin, async (req, res) => {
    try {
      const attemptId = Number(req.params.id);
      if (!Number.isFinite(attemptId) || attemptId <= 0) {
        return res.status(400).json({ error: "invalid attempt id" });
      }
      const [result] = await pool.execute(`DELETE FROM measurement_attempts WHERE id = ?`, [attemptId]);
      if (result.affectedRows === 0) return res.status(404).json({ error: "attempt not found" });
      return res.json({ deleted: true });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  app.get("/api/leaderboard", async (_req, res) => {
    try {
      const [top1km] = await pool.execute(
        `SELECT m.id, m.mode_label AS modeLabel, m.duration_ms AS durationMs, m.max_speed_kmh AS maxSpeedKmh,
                u.username
         FROM measurement_attempts m
         LEFT JOIN users u ON u.id = m.user_id
         WHERE m.measure_type = 'distance' AND m.mode_label = '1 km' AND m.user_id IS NOT NULL
         ORDER BY m.duration_ms ASC
         LIMIT 20`
      );
      const [top0100] = await pool.execute(
        `SELECT m.id, m.mode_label AS modeLabel, m.duration_ms AS durationMs, m.max_speed_kmh AS maxSpeedKmh,
                u.username
         FROM measurement_attempts m
         LEFT JOIN users u ON u.id = m.user_id
         WHERE m.measure_type = 'speed_accel'
           AND (m.mode_label = '0-100 km/h' OR m.mode_label = '0–100 km/h')
           AND m.user_id IS NOT NULL
         ORDER BY m.duration_ms ASC
         LIMIT 20`
      );
      res.json({ top1km, top0100 });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  /**
   * Body (camelCase):
   * clientRecordId, measuredAtEpochMs, measureType, modeLabel, startStrategy?,
   * maxSpeedKmh, durationMs, distanceM, userId?, vehicleId?
   */
  app.post("/api/attempts", optionalAuth, async (req, res) => {
    try {
      const {
        clientRecordId,
        measuredAtEpochMs,
        measureType,
        modeLabel,
        startStrategy,
        maxSpeedKmh,
        durationMs,
        distanceM,
        userId,
        vehicleId,
      } = req.body || {};

      if (
        !clientRecordId ||
        measuredAtEpochMs == null ||
        !measureType ||
        !modeLabel ||
        maxSpeedKmh == null ||
        durationMs == null ||
        distanceM == null
      ) {
        return res.status(400).json({ error: "missing required fields" });
      }

      const measuredAt = new Date(Number(measuredAtEpochMs));
      if (Number.isNaN(measuredAt.getTime())) {
        return res.status(400).json({ error: "invalid measuredAtEpochMs" });
      }

      const resolvedUserId = req.user?.sub ? Number(req.user.sub) : (userId ?? null);
      const [result] = await pool.execute(
        `INSERT INTO measurement_attempts
          (client_record_id, user_id, vehicle_id, measured_at, measure_type, mode_label, start_strategy, max_speed_kmh, duration_ms, distance_m)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           id = LAST_INSERT_ID(id),
           user_id = VALUES(user_id),
           vehicle_id = VALUES(vehicle_id),
           measured_at = VALUES(measured_at),
           measure_type = VALUES(measure_type),
           mode_label = VALUES(mode_label),
           start_strategy = VALUES(start_strategy),
           max_speed_kmh = VALUES(max_speed_kmh),
           duration_ms = VALUES(duration_ms),
           distance_m = VALUES(distance_m)`,
        [
          clientRecordId,
          resolvedUserId,
          vehicleId ?? null,
          measuredAt,
          measureType,
          modeLabel,
          startStrategy ?? null,
          Number(maxSpeedKmh),
          Number(durationMs),
          Number(distanceM),
        ]
      );

      await pool.execute(
        `INSERT INTO sync_events (client_record_id, event_type, message) VALUES (?, 'insert', 'api upsert')`,
        [clientRecordId]
      );

      res.status(201).json({ id: result.insertId });
    } catch (e) {
      console.error(e);
      res.status(500).json({ error: String(e.message || e) });
    }
  });

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`pam-sync-api listening on ${PORT}`);
  });
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
