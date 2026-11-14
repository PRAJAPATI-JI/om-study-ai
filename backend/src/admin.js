import { HttpError, json, readJson, now, dayStart } from "./util.js";
import { loadConfig, saveConfig, validateConfig, defaultConfig, clearConfigCache } from "./config.js";
import { DIRECTORY, isPaidLike } from "./directory.js";
import { encryptSecret, maskKey, decryptSecret } from "./crypto.js";
import { callProvider } from "./adapters.js";
import { logEvent } from "./engine.js";

async function audit(env, uid, action, detail = "") {
  await env.DB.prepare("INSERT INTO audit (ts, uid, action, detail) VALUES (?,?,?,?)").bind(now(), uid, action, String(detail).slice(0, 300)).run();
}

const all = async (env, sql, ...args) => (await env.DB.prepare(sql).bind(...args).all()).results || [];
const one = async (env, sql, ...args) => (await env.DB.prepare(sql).bind(...args).first()) || {};

function requireOwner(role) {
  if (role !== "owner") throw new HttpError(403, "owner_only", "यह काम सिर्फ मालिक कर सकता है।");
}

async function overview(env, url) {
  const { cfg, version } = await loadConfig(env);
  const tz = cfg.budgets.tzOffsetMin;
  const t = now();
  const range = url.searchParams.get("range") || "day";
  const days = range === "month" ? 30 : range === "week" ? 7 : 1;
  const since = dayStart(t, tz) - (days - 1) * 86400000;
  const real = "attempt = 1 AND COALESCE(task,'') != 'test'";
  const realAny = "COALESCE(task,'') != 'test'";

  const requests = (await one(env, `SELECT COUNT(*) c FROM usage WHERE ${real} AND ts >= ?`, since)).c || 0;
  const okReq = (await one(env, `SELECT COUNT(*) c FROM usage WHERE ${realAny} AND status='ok' AND ts >= ?`, since)).c || 0;
  const tok = await one(env, `SELECT COALESCE(SUM(input_tokens),0) i, COALESCE(SUM(output_tokens),0) o,
      SUM(CASE WHEN status='ok' AND input_tokens IS NULL THEN 1 ELSE 0 END) unreported
      FROM usage WHERE ${realAny} AND ts >= ?`, since);
  const cost = await one(env, `SELECT COALESCE(SUM(est_cost),0) c, SUM(CASE WHEN status='ok' AND est_cost IS NULL THEN 1 ELSE 0 END) unknown
      FROM usage WHERE ${realAny} AND ts >= ?`, since);
  const dist = async (days2) => (await one(env, `SELECT COUNT(DISTINCT uid) c FROM usage WHERE ${realAny} AND ts >= ?`, dayStart(t, tz) - (days2 - 1) * 86400000)).c || 0;

  const byFeature = await all(env, `SELECT feature, COUNT(*) calls, SUM(status='ok') ok, SUM(status!='ok') err,
      COALESCE(SUM(input_tokens),0) tin, COALESCE(SUM(output_tokens),0) tout, COALESCE(SUM(est_cost),0) cost
      FROM usage WHERE ${realAny} AND ts >= ? GROUP BY feature ORDER BY calls DESC`, since);
  const byProvider = await all(env, `SELECT provider_id, COUNT(*) calls, SUM(status='ok') ok, SUM(status!='ok') err,
      COALESCE(SUM(input_tokens),0) tin, COALESCE(SUM(output_tokens),0) tout, COALESCE(SUM(est_cost),0) cost,
      AVG(latency_ms) avg_latency FROM usage WHERE ${realAny} AND ts >= ? GROUP BY provider_id ORDER BY calls DESC`, since);
  const evCount = async (kind) => (await one(env, "SELECT COUNT(*) c FROM events WHERE kind = ? AND ts >= ?", kind, since)).c || 0;

  const statuses = await all(env, "SELECT * FROM provider_status");
  const sMap = Object.fromEntries(statuses.map((s) => [s.provider_id, s]));
  const dayS = dayStart(t, tz);
  const health = [];
  for (const p of Object.values(cfg.providers)) {
    const s = sMap[p.id] || {};
    const used = (await one(env, "SELECT COUNT(*) c FROM usage WHERE provider_id = ? AND ts >= ?", p.id, dayS)).c || 0;
    const key = await one(env, "SELECT 1 x FROM secrets WHERE provider_id = ?", p.id);
    let remaining = "Unavailable";
    if (s.rate_headers) {
      try {
        const h = JSON.parse(s.rate_headers);
        const rem = Object.entries(h).filter(([k]) => k.includes("remaining"));
        if (rem.length) remaining = rem.map(([k, v]) => `${k}: ${v}`).join(", ");
      } catch { /* ignore */ }
    }
    health.push({
      id: p.id, name: p.name, feature: p.feature, enabled: !!p.enabled, hasKey: !!key.x, tier: p.tier,
      lastTestAt: s.last_test_at || null, lastTestOk: s.last_test_ok == null ? null : !!s.last_test_ok, lastTestMsg: s.last_test_msg || "",
      lastSuccessAt: s.last_success_at || null, lastErrorAt: s.last_error_at || null, lastError: s.last_error || "",
      usedToday: used, dailyCap: cfg.budgets.providerDailyRequests?.[p.id] || 0, remainingReportedByProvider: remaining
    });
  }
  const recent = await all(env, `SELECT ts, uid, feature, task, provider_id, model, status, error_code, latency_ms, fallback, attempt
      FROM usage ORDER BY id DESC LIMIT 20`);
  const recentEvents = await all(env, "SELECT ts, kind, feature, provider_id, detail FROM events ORDER BY id DESC LIMIT 20");
  const usersTotal = (await one(env, "SELECT COUNT(*) c FROM users")).c || 0;
  const usersActive = (await one(env, "SELECT COUNT(*) c FROM users WHERE last_seen >= ?", since)).c || 0;

  return {
    range, since, configVersion: version,
    users: {
      registeredInApp: usersTotal, activeInRange: usersActive, daily: await dist(1), weekly: await dist(7), monthly: await dist(30),
      note: "यह उन्हीं की गिनती है जिन्होंने नया ऐप खोलकर सर्वर से संपर्क किया। Firebase की पूरी गिनती नहीं।"
    },
    requests: { total: requests, successful: okReq, failed: Math.max(0, requests - okReq) },
    tokens: { input: tok.i || 0, output: tok.o || 0, unreportedRequests: tok.unreported || 0 },
    cost: {
      estimatedUsd: cost.c || 0, unknownCostRequests: cost.unknown || 0, actualBilledUsd: "Unavailable",
      note: "अनुमानित खर्च आपके भरे हुए दाम और provider के बताए टोकन से निकलता है। असली बिल provider के पन्ने पर देखें।"
    },
    byFeature, byProvider,
    events: { fallback: await evCount("fallback"), error: await evCount("error"), rateLimit: await evCount("rate_limit"), budgetBlock: await evCount("budget_block") },
    health, recent, recentEvents
  };
}

async function analytics(env, url) {
  const { cfg } = await loadConfig(env);
  const tz = cfg.budgets.tzOffsetMin;
  const days = Math.min(90, Math.max(1, parseInt(url.searchParams.get("days") || "14", 10) || 14));
  const since = dayStart(now(), tz) - (days - 1) * 86400000;
  const rows = await all(env, `SELECT CAST((ts + ?) / 86400000 AS INTEGER) d, feature, provider_id, COUNT(*) calls, SUM(status='ok') ok,
      COALESCE(SUM(input_tokens),0) tin, COALESCE(SUM(output_tokens),0) tout, COALESCE(SUM(est_cost),0) cost
      FROM usage WHERE COALESCE(task,'') != 'test' AND ts >= ? GROUP BY d, feature, provider_id ORDER BY d`, tz * 60000, since);
  return { days, tzOffsetMin: tz, rows };
}

export async function handleAdmin(request, env, auth, role) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/^\/admin\/api/, "") || "/";
  const method = request.method;
  const uid = auth.uid;

  if (method !== "GET") requireOwner(role);

  if (method === "GET" && path === "/overview") return json(await overview(env, url));
  if (method === "GET" && path === "/analytics") return json(await analytics(env, url));

  if (method === "GET" && path === "/config") {
    const { cfg, version } = await loadConfig(env);
    const secrets = await all(env, "SELECT provider_id, last4, updated_at FROM secrets");
    const status = await all(env, "SELECT provider_id, last_test_at, last_test_ok, last_test_msg, last_success_at, last_error_at, last_error FROM provider_status");
    return json({
      version, config: cfg, role,
      keys: Object.fromEntries(secrets.map((s) => [s.provider_id, { masked: maskKey(s.last4), updatedAt: s.updated_at }])),
      status: Object.fromEntries(status.map((s) => [s.provider_id, s])),
      directory: DIRECTORY, masterKeySet: !!env.MASTER_KEY && env.MASTER_KEY.length >= 16
    });
  }

  if (method === "PUT" && path === "/config") {
    const body = await readJson(request, 256 * 1024);
    const version = await saveConfig(env, body.config, uid, body.note);
    await audit(env, uid, "config_publish", `v${version} ${body.note || ""}`);
    return json({ ok: true, version });
  }

  if (method === "GET" && path === "/versions") {
    return json({ versions: await all(env, "SELECT version, note, created_by, created_at FROM config_versions ORDER BY version DESC LIMIT 50") });
  }

  if (method === "POST" && path === "/rollback") {
    const body = await readJson(request);
    const row = await env.DB.prepare("SELECT config FROM config_versions WHERE version = ?").bind(Number(body.version)).first();
    if (!row) throw new HttpError(404, "no_version", "यह version नहीं मिला।");
    const version = await saveConfig(env, JSON.parse(row.config), uid, `rollback to v${body.version}`);
    await audit(env, uid, "config_rollback", `to v${body.version} → v${version}`);
    return json({ ok: true, version });
  }

  const keyMatch = path.match(/^\/provider\/([a-z0-9_-]{1,40})\/(key|test)$/);
  if (keyMatch) {
    const [, id, what] = keyMatch;
    const { cfg } = await loadConfig(env);
    const p = cfg.providers[id];
    if (!p) throw new HttpError(404, "no_provider", "यह provider config में नहीं है। पहले उसे जोड़कर publish कीजिए।");

    if (what === "key" && method === "PUT") {
      if (!env.MASTER_KEY || env.MASTER_KEY.length < 16) {
        throw new HttpError(500, "no_master_key", "सर्वर में MASTER_KEY नहीं है, इसलिए key सुरक्षित नहीं रखी जा सकती। README के कदम देखिए।");
      }
      const body = await readJson(request);
      const key = String(body.key || "").trim();
      if (key.length < 8 || key.length > 400 || /\s/.test(key)) throw new HttpError(400, "bad_key", "key सही नहीं लग रही।");
      const enc = await encryptSecret(env.MASTER_KEY, key);
      await env.DB.prepare(
        `INSERT INTO secrets (provider_id, iv, ciphertext, last4, updated_at) VALUES (?,?,?,?,?)
         ON CONFLICT(provider_id) DO UPDATE SET iv=excluded.iv, ciphertext=excluded.ciphertext, last4=excluded.last4, updated_at=excluded.updated_at`
      ).bind(id, enc.iv, enc.ciphertext, key.slice(-4), now()).run();
      await audit(env, uid, "key_set", id);
      return json({ ok: true, masked: maskKey(key.slice(-4)) });
    }
    if (what === "key" && method === "DELETE") {
      await env.DB.prepare("DELETE FROM secrets WHERE provider_id = ?").bind(id).run();
      await audit(env, uid, "key_delete", id);
      return json({ ok: true });
    }
    if (what === "test" && method === "POST") {
      const body = await readJson(request);
      if (isPaidLike(p.tier) && body.confirmPaid !== true) {
        throw new HttpError(400, "confirm_paid", "यह paid जैसा provider है। टेस्ट से भी थोड़ा खर्च हो सकता है। पुष्टि के साथ दोबारा भेजिए।");
      }
      const row = await env.DB.prepare("SELECT iv, ciphertext FROM secrets WHERE provider_id = ?").bind(id).first();
      if (!row) throw new HttpError(400, "no_key", "पहले इस provider की key सेव कीजिए।");
      const key = await decryptSecret(env.MASTER_KEY, row.iv, row.ciphertext);
      const input = p.adapter === "openai" ? { messages: [{ role: "user", content: "Reply with the single word: ok" }], maxTokens: 8 }
        : p.adapter === "libretranslate" ? { text: "hello", source: "en", target: "hi" }
        : { query: "OM STUDY AI test" };
      const started = Date.now();
      const r = await callProvider(p, key, input, { timeoutMs: cfg.requestTimeoutMs, maxTokens: 16, fetchImpl: fetch });
      const latency = Date.now() - started;
      const t = now();
      await env.DB.prepare(
        `INSERT INTO provider_status (provider_id, last_test_at, last_test_ok, last_test_msg) VALUES (?,?,?,?)
         ON CONFLICT(provider_id) DO UPDATE SET last_test_at=excluded.last_test_at, last_test_ok=excluded.last_test_ok, last_test_msg=excluded.last_test_msg`
      ).bind(id, t, r.ok ? 1 : 0, r.ok ? `ठीक (${latency} ms)` : `${r.errorCode}: ${r.message}`.slice(0, 200)).run();
      await env.DB.prepare(
        `INSERT INTO usage (ts, uid, feature, task, provider_id, model, input_tokens, output_tokens, status, error_code, latency_ms, est_cost, actual_cost, fallback, attempt)
         VALUES (?,?,?,'test',?,?,?,?,?,?,?,NULL,NULL,0,1)`
      ).bind(t, uid, p.feature, id, p.model || null, r.tokensIn ?? null, r.tokensOut ?? null, r.ok ? "ok" : "error", r.ok ? null : r.errorCode, latency).run();
      await audit(env, uid, "provider_test", `${id} ${r.ok ? "ok" : r.errorCode}`);
      return json({ ok: r.ok, latencyMs: latency, errorCode: r.ok ? null : r.errorCode, message: r.ok ? "कनेक्शन ठीक है।" : r.message });
    }
  }

  if (method === "GET" && path === "/users") {
    const since = now() - 30 * 86400000;
    return json({
      users: await all(env, `SELECT u.uid, u.first_seen, u.last_seen,
        (SELECT COUNT(*) FROM usage x WHERE x.uid = u.uid AND x.attempt = 1 AND COALESCE(x.task,'') != 'test' AND x.ts >= ?) requests30d
        FROM users u ORDER BY u.last_seen DESC LIMIT 200`, since),
      note: "निजी चैट या दस्तावेज़ यहाँ नहीं दिखते। सिर्फ UID और गिनती।"
    });
  }

  if (method === "GET" && path === "/events") {
    const kind = url.searchParams.get("kind");
    const limit = Math.min(200, parseInt(url.searchParams.get("limit") || "100", 10) || 100);
    const rows = kind
      ? await all(env, "SELECT * FROM events WHERE kind = ? ORDER BY id DESC LIMIT ?", kind, limit)
      : await all(env, "SELECT * FROM events ORDER BY id DESC LIMIT ?", limit);
    const errs = await all(env, `SELECT ts, uid, feature, provider_id, error_code, latency_ms, attempt FROM usage WHERE status != 'ok' ORDER BY id DESC LIMIT ?`, limit);
    return json({ events: rows, failedCalls: errs });
  }

  if (method === "GET" && path === "/audit") {
    return json({ audit: await all(env, "SELECT * FROM audit ORDER BY id DESC LIMIT 100") });
  }

  if (path === "/admins") {
    requireOwner(role);
    if (method === "GET") return json({ admins: await all(env, "SELECT uid, role, added_by, added_at FROM admins ORDER BY added_at DESC") });
    if (method === "POST") {
      const body = await readJson(request);
      const target = String(body.uid || "").trim();
      if (target.length < 6 || target.length > 128 || /\s/.test(target)) throw new HttpError(400, "bad_uid", "UID सही नहीं है।");
      await env.DB.prepare("INSERT OR REPLACE INTO admins (uid, role, added_by, added_at) VALUES (?, 'viewer', ?, ?)").bind(target, uid, now()).run();
      await audit(env, uid, "admin_add", target);
      return json({ ok: true });
    }
  }
  const delAdmin = path.match(/^\/admins\/([^/]{6,128})$/);
  if (delAdmin && method === "DELETE") {
    await env.DB.prepare("DELETE FROM admins WHERE uid = ?").bind(delAdmin[1]).run();
    await audit(env, uid, "admin_remove", delAdmin[1]);
    return json({ ok: true });
  }

  if (method === "GET" && path === "/security") {
    const { cfg } = await loadConfig(env);
    return json({
      ownerUidSet: !!env.OWNER_UID, masterKeySet: !!env.MASTER_KEY && env.MASTER_KEY.length >= 16,
      firebaseProject: env.FIREBASE_PROJECT_ID, freeOnly: cfg.budgets.freeOnly, paidLock: cfg.budgets.paidLock,
      adminCount: (await one(env, "SELECT COUNT(*) c FROM admins")).c || 0, role,
      keysStored: (await one(env, "SELECT COUNT(*) c FROM secrets")).c || 0
    });
  }

  throw new HttpError(404, "not_found", "ऐसा admin काम नहीं है।");
}

export { defaultConfig, validateConfig, clearConfigCache, logEvent };
