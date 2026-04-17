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
   * measuredAtEpochMs, measureType, modeLabel, startStrategy?, maxSpeedKmh, durationMs, distanceM
   */
  app.post("/api/attempts", async (req, res) => {
    try {
      const {
        measuredAtEpochMs,
        measureType,
        modeLabel,
        startStrategy,
        maxSpeedKmh,
        durationMs,
        distanceM,
      } = req.body || {};

      if (
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
          (measured_at, measure_type, mode_label, start_strategy, max_speed_kmh, duration_ms, distance_m)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
        [
          measuredAt,
          measureType,
          modeLabel,
          startStrategy ?? null,
          Number(maxSpeedKmh),
          Number(durationMs),
          Number(distanceM),
        ]
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
