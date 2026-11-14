-- OM STUDY AI backend schema (Cloudflare D1 / SQLite)
CREATE TABLE IF NOT EXISTS config_versions (
  version INTEGER PRIMARY KEY AUTOINCREMENT,
  config TEXT NOT NULL,
  note TEXT,
  created_by TEXT,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS secrets (
  provider_id TEXT PRIMARY KEY,
  iv TEXT NOT NULL,
  ciphertext TEXT NOT NULL,
  last4 TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS users (
  uid TEXT PRIMARY KEY,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS admins (
  uid TEXT PRIMARY KEY,
  role TEXT NOT NULL,
  added_by TEXT,
  added_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS usage (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts INTEGER NOT NULL,
  uid TEXT NOT NULL,
  feature TEXT NOT NULL,
  task TEXT,
  provider_id TEXT,
  model TEXT,
  input_tokens INTEGER,
  output_tokens INTEGER,
  status TEXT NOT NULL,
  error_code TEXT,
  latency_ms INTEGER,
  est_cost REAL,
  actual_cost REAL,
  fallback INTEGER NOT NULL DEFAULT 0,
  attempt INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS usage_ts ON usage(ts);
CREATE INDEX IF NOT EXISTS usage_uid_ts ON usage(uid, ts);
CREATE INDEX IF NOT EXISTS usage_provider_ts ON usage(provider_id, ts);
CREATE TABLE IF NOT EXISTS provider_status (
  provider_id TEXT PRIMARY KEY,
  last_test_at INTEGER,
  last_test_ok INTEGER,
  last_test_msg TEXT,
  last_success_at INTEGER,
  last_error_at INTEGER,
  last_error TEXT,
  rate_headers TEXT
);
CREATE TABLE IF NOT EXISTS events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts INTEGER NOT NULL,
  kind TEXT NOT NULL,
  feature TEXT,
  provider_id TEXT,
  uid TEXT,
  detail TEXT
);
CREATE INDEX IF NOT EXISTS events_ts ON events(ts);
CREATE TABLE IF NOT EXISTS audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts INTEGER NOT NULL,
  uid TEXT NOT NULL,
  action TEXT NOT NULL,
  detail TEXT
);
