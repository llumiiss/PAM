"use strict";

const express = require("express");
const cors = require("cors");
const mysql = require("mysql2/promise");

const PORT = Number(process.env.PORT || 8090);
const DB_HOST = process.env.DB_HOST || "mysql";
const DB_USER = process.env.DB_USER || "pam";
const DB_PASSWORD = process.env.DB_PASSWORD || "pam_dev";
const DB_NAME = process.env.DB_NAME || "pam";

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

  /**
   * Body (camelCase):
   * clientRecordId, measuredAtEpochMs, measureType, modeLabel, startStrategy?,
   * maxSpeedKmh, durationMs, distanceM, userId?, vehicleId?
   */
  app.post("/api/attempts", async (req, res) => {
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
          userId ?? null,
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
