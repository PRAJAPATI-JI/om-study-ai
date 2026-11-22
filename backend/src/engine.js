import { FEATURES, HttpError, now, dayStart, monthStart } from "./util.js";
import { loadConfig } from "./config.js";
import { isPaidLike } from "./directory.js";
import { callProvider } from "./adapters.js";
import { decryptSecret } from "./crypto.js";

const SYSTEM_PROMPT = [
  "तुम OM STUDY AI के शिक्षक हो। विद्यार्थी की पढ़ाई में मदद करो।",
  "सरल भाषा में, चरण-दर-चरण समझाओ और अंत में एक छोटा अभ्यास-प्रश्न दो।",
  "विद्यार्थी की चुनी भाषा में जवाब दो (hi = सरल हिंदी, en = English, hinglish = हिंदी+English)।",
  "किसी बोर्ड या परीक्षा का आधिकारिक सिलेबस, नियम, तारीख या पिछले साल का प्रश्न अपनी तरफ से मत गढ़ो। पक्का न हो तो साफ कहो कि पक्का नहीं है।",
  "अंक, रैंक या नौकरी की कोई गारंटी मत दो। झूठे लिंक या हवाले मत बनाओ।",
  "बिजली या इलेक्ट्रिकल प्रैक्टिकल पर हमेशा सुरक्षा बताओ और खतरनाक काम योग्य देखरेख में करने को कहो।",
  "विद्यार्थी की निजी जानकारी (नाम, ईमेल, फोन) मत माँगो।"
].join(" ");

const TASK_HINT = {
  chat: "",
  explain: "इस विषय को शुरुआत से समझाओ।",
  notes: "इस विषय के छोटे, साफ नोट्स बनाओ (बिंदुओं में)।",
  qa: "सवाल का सीधा जवाब दो और कारण भी बताओ।",
  timetable: "एक व्यावहारिक पढ़ाई-समय-सारणी का सुझाव दो। अंक की गारंटी मत दो।",
  quiz: "4 विकल्प वाले 5 प्रश्न बनाओ, सही उत्तर और छोटी समझाइश के साथ। इन्हें 'AI-बनाए प्रश्न' कहो, पिछले साल के नहीं।"
};
export const TASKS = Object.keys(TASK_HINT);

const clip = (s, n) => String(s ?? "").slice(0, n);

function contextText(ctx) {
  if (!ctx || typeof ctx !== "object") return "";
  const parts = [];
  for (const k of ["language", "class", "board", "stream", "level", "goals", "subjects", "weak"]) {
    const v = ctx[k];
    if (v == null) continue;
    const text = Array.isArray(v) ? v.slice(0, 12).map((x) => clip(x, 40)).join(", ") : clip(v, 80);
    if (text) parts.push(`${k}: ${text}`);
  }
  return parts.length ? "विद्यार्थी का सार (सिर्फ मार्गदर्शन के लिए): " + parts.join("; ") : "";
}

/** अनुरोध की जाँच और साफ-सफाई। आगे सिर्फ यही साफ किया हुआ रूप जाता है। */
export function normalizeInput(feature, body) {
  if (feature === "text") {
    const msgs = Array.isArray(body.messages) ? body.messages : [];
    if (!msgs.length || msgs.length > 20) throw new HttpError(400, "bad_messages", "संदेश 1 से 20 के बीच होने चाहिए।");
    let total = 0;
    const clean = msgs.map((m) => {
      if (!m || (m.role !== "user" && m.role !== "assistant") || typeof m.content !== "string") {
        throw new HttpError(400, "bad_messages", "संदेश सही नहीं है।");
      }
      const content = clip(m.content, 4000);
      total += content.length;
      return { role: m.role, content };
    });
    if (total > 16000) throw new HttpError(400, "too_long", "बातचीत बहुत लंबी है।");
    if (clean[clean.length - 1].role !== "user") throw new HttpError(400, "bad_messages", "आखिरी संदेश विद्यार्थी का होना चाहिए।");
    const task = TASKS.includes(body.task) ? body.task : "chat";
    const sys = [SYSTEM_PROMPT, TASK_HINT[task], contextText(body.context)].filter(Boolean).join("\n");
    return { task, messages: [{ role: "system", content: sys }, ...clean] };
  }
  if (feature === "search") {
    const q = clip(body.query, 300).trim();
    if (!q) throw new HttpError(400, "bad_query", "खोज का शब्द लिखिए।");
    return { task: "search", query: q };
  }
  if (feature === "translation") {
    const text = clip(body.text, 2000).trim();
    const lang = /^[a-z]{2,3}(-[A-Za-z]{2,4})?$/;
    const target = String(body.target || "");
    const source = body.source ? String(body.source) : "auto";
    if (!text) throw new HttpError(400, "bad_text", "अनुवाद के लिए पाठ लिखिए।");
    if (!lang.test(target) || (source !== "auto" && !lang.test(source))) throw new HttpError(400, "bad_lang", "भाषा कोड सही नहीं है।");
    return { task: "translate", text, source, target };
  }
  throw new HttpError(501, "not_implemented", "यह सुविधा अभी बनी नहीं है।");
}

/** provider चल सकता है या नहीं (नियम: मुफ्त/भुगतान) */
export function providerAllowed(cfg, p) {
  if (!p || !p.enabled) return false;
  if (isPaidLike(p.tier)) {
    const b = cfg.budgets;
    if (b.paidLock || b.freeOnly) return false;
    if (p.paidApproved !== true) return false;
    if (!(b.globalDailyUsd > 0 && b.globalMonthlyUsd > 0)) return false;
  }
  return true;
}

export function chainFor(cfg, feature) {
  const r = cfg.routes[feature] || { primary: null, backups: [] };
  return [r.primary, ...(r.backups || [])].filter(Boolean).map((id) => cfg.providers[id]).filter(Boolean);
}

async function count(env, sql, ...args) {
  const row = await env.DB.prepare(sql).bind(...args).first();
  return row ? Number(Object.values(row)[0]) || 0 : 0;
}

export async function logEvent(env, kind, { feature = null, provider = null, uid = null, detail = "" } = {}) {
  await env.DB.prepare("INSERT INTO events (ts, kind, feature, provider_id, uid, detail) VALUES (?,?,?,?,?,?)")
    .bind(now(), kind, feature, provider, uid, String(detail).slice(0, 300)).run();
}

async function hasKey(env, id) {
  return !!(await env.DB.prepare("SELECT 1 FROM secrets WHERE provider_id = ?").bind(id).first());
}

/** किस feature के लिए अभी कोई चलने लायक provider है (ऐप को "AI चालू/बंद" बताने के लिए) */
export async function featureAvailability(env, cfg) {
  const out = {};
  for (const f of FEATURES) {
    let ok = false;
    for (const p of chainFor(cfg, f)) {
      if (providerAllowed(cfg, p) && (await hasKey(env, p.id))) { ok = true; break; }
    }
    out[f] = ok;
  }
  return out;
}

function costOf(p, tin, tout) {
  if (p.priceRequest != null && Number(p.priceRequest) > 0) return Number(p.priceRequest);
  if (tin != null && tout != null && (p.priceIn != null || p.priceOut != null)) {
    return (tin * (p.priceIn || 0) + tout * (p.priceOut || 0)) / 1e6;
  }
  return isPaidLike(p.tier) ? null : 0; // मुफ्त = 0, भुगतान वाले का टोकन पता न हो तो अज्ञात (null)
}

async function recordStatus(env, id, ok, message, headers) {
  const t = now();
  const rh = headers && Object.keys(headers).length ? JSON.stringify(headers) : null;
  await env.DB.prepare(
    `INSERT INTO provider_status (provider_id, last_success_at, last_error_at, last_error, rate_headers)
     VALUES (?,?,?,?,?)
     ON CONFLICT(provider_id) DO UPDATE SET
       last_success_at = COALESCE(excluded.last_success_at, last_success_at),
       last_error_at = COALESCE(excluded.last_error_at, last_error_at),
       last_error = CASE WHEN excluded.last_error_at IS NOT NULL THEN excluded.last_error ELSE last_error END,
       rate_headers = COALESCE(excluded.rate_headers, rate_headers)`
  ).bind(id, ok ? t : null, ok ? null : t, ok ? null : String(message).slice(0, 200), rh).run();
}

async function logUsage(env, row) {
  await env.DB.prepare(
    `INSERT INTO usage (ts, uid, feature, task, provider_id, model, input_tokens, output_tokens, status, error_code, latency_ms, est_cost, actual_cost, fallback, attempt)
     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,NULL,?,?)`
  ).bind(
    now(), row.uid, row.feature, row.task, row.provider_id, row.model, row.tin, row.tout,
    row.status, row.error_code, row.latency, row.cost, row.fallback ? 1 : 0, row.attempt
  ).run();
}

/** एक अनुरोध: सीमाएँ → chain → provider (ज़रूरत पर fallback, हर provider सिर्फ एक बार) */
export async function runFeature(env, auth, feature, rawBody, deps = {}) {
  if (!FEATURES.includes(feature)) throw new HttpError(404, "unknown_feature", "ऐसी सुविधा नहीं है।");
  const fetchImpl = deps.fetchImpl || fetch;
  const { cfg, version } = await loadConfig(env);
  const b = cfg.budgets;
  const input = normalizeInput(feature, rawBody);
  const uid = auth.uid;
  const t = now();
  const dStart = dayStart(t, b.tzOffsetMin);
  const mStart = monthStart(t, b.tzOffsetMin);
  const real = "attempt = 1 AND COALESCE(task,'') != 'test'";

  // ---- सीमाएँ ----
  const perMin = await count(env, `SELECT COUNT(*) FROM usage WHERE ${real} AND uid = ? AND ts >= ?`, uid, t - 60000);
  if (b.perUserPerMinute > 0 && perMin >= b.perUserPerMinute) {
    await logEvent(env, "rate_limit", { feature, uid, detail: "per_user_minute" });
    throw new HttpError(429, "rate_limited", "आप बहुत तेज़ी से अनुरोध भेज रहे हैं। एक मिनट रुककर फिर कोशिश कीजिए।");
  }
  const perDay = await count(env, `SELECT COUNT(*) FROM usage WHERE ${real} AND uid = ? AND ts >= ?`, uid, dStart);
  if (b.perUserDailyRequests > 0 && perDay >= b.perUserDailyRequests) {
    await logEvent(env, "rate_limit", { feature, uid, detail: "per_user_day" });
    throw new HttpError(429, "daily_limit", "आज की सीमा पूरी हो गई। कल फिर कोशिश कीजिए।");
  }
  const globalDay = await count(env, `SELECT COUNT(*) FROM usage WHERE ${real} AND ts >= ?`, dStart);
  if (b.globalDailyRequests > 0 && globalDay >= b.globalDailyRequests) {
    await logEvent(env, "budget_block", { feature, uid, detail: "global_daily_requests" });
    throw new HttpError(429, "service_busy", "आज सेवा की कुल सीमा पूरी हो गई। कल फिर कोशिश कीजिए।");
  }
  const featCap = b.featureDailyRequests?.[feature];
  if (featCap > 0) {
    const n = await count(env, `SELECT COUNT(*) FROM usage WHERE ${real} AND feature = ? AND ts >= ?`, feature, dStart);
    if (n >= featCap) {
      await logEvent(env, "budget_block", { feature, uid, detail: "feature_daily_requests" });
      throw new HttpError(429, "feature_limit", "इस सुविधा की आज की सीमा पूरी हो गई।");
    }
  }

  // ---- खर्च (सिर्फ paid providers के लिए ज़रूरी) ----
  let spend = null;
  const getSpend = async () => {
    if (spend) return spend;
    spend = {
      day: await count(env, "SELECT COALESCE(SUM(est_cost),0) FROM usage WHERE ts >= ?", dStart),
      month: await count(env, "SELECT COALESCE(SUM(est_cost),0) FROM usage WHERE ts >= ?", mStart),
      featDay: await count(env, "SELECT COALESCE(SUM(est_cost),0) FROM usage WHERE feature = ? AND ts >= ?", feature, dStart)
    };
    return spend;
  };

  // ---- chain ----
  const chain = [];
  for (const p of chainFor(cfg, feature)) {
    if (!providerAllowed(cfg, p)) continue;
    if (!(await hasKey(env, p.id))) continue;
    const cap = b.providerDailyRequests?.[p.id];
    if (cap > 0) {
      const n = await count(env, "SELECT COUNT(*) FROM usage WHERE provider_id = ? AND ts >= ?", p.id, dStart);
      if (n >= cap) { await logEvent(env, "provider_cap", { feature, provider: p.id, uid, detail: `cap ${cap}` }); continue; }
    }
    if (isPaidLike(p.tier)) {
      const s = await getSpend();
      const fcap = b.featureDailyUsd?.[feature];
      if (s.day >= b.globalDailyUsd || s.month >= b.globalMonthlyUsd || (fcap > 0 && s.featDay >= fcap)) {
        await logEvent(env, "budget_block", { feature, provider: p.id, uid, detail: "usd_budget" });
        continue;
      }
    }
    chain.push(p);
  }
  if (!chain.length) {
    throw new HttpError(503, "feature_unavailable", "यह सुविधा अभी चालू नहीं है। बाद में कोशिश कीजिए।");
  }

  // ---- कोशिशें ----
  let attempt = 0;
  let lastErr = null;
  for (const p of chain) {
    attempt++;
    const started = Date.now();
    let key;
    try {
      const row = await env.DB.prepare("SELECT iv, ciphertext FROM secrets WHERE provider_id = ?").bind(p.id).first();
      key = await decryptSecret(env.MASTER_KEY, row.iv, row.ciphertext);
    } catch {
      lastErr = { errorCode: "key_error", message: "key पढ़ी नहीं जा सकी", fallbackable: true };
      await logUsage(env, { uid, feature, task: input.task, provider_id: p.id, model: p.model || null, tin: null, tout: null, status: "error", error_code: "key_error", latency: 0, cost: null, fallback: attempt > 1, attempt });
      continue;
    }
    const r = await callProvider(p, key, input, { timeoutMs: cfg.requestTimeoutMs, maxTokens: cfg.maxOutputTokens, fetchImpl });
    const latency = Date.now() - started;
    if (r.ok) {
      const cost = costOf(p, r.tokensIn, r.tokensOut);
      await logUsage(env, { uid, feature, task: input.task, provider_id: p.id, model: p.model || null, tin: r.tokensIn, tout: r.tokensOut, status: "ok", error_code: null, latency, cost, fallback: attempt > 1, attempt });
      await recordStatus(env, p.id, true, "", r.headers);
      return {
        ok: true, feature, task: input.task, result: r.result,
        provider: { id: p.id, name: p.name, model: p.model || "" },
        fallback: attempt > 1, usage: { inputTokens: r.tokensIn, outputTokens: r.tokensOut }, configVersion: version
      };
    }
    lastErr = r;
    await logUsage(env, { uid, feature, task: input.task, provider_id: p.id, model: p.model || null, tin: null, tout: null, status: "error", error_code: r.errorCode, latency, cost: null, fallback: attempt > 1, attempt });
    await recordStatus(env, p.id, false, `${r.errorCode}: ${r.message}`, r.headers);
    if (r.errorCode === "rate_limit") await logEvent(env, "rate_limit", { feature, provider: p.id, uid, detail: "provider" });
    if (!r.fallbackable) break; // ग़लत अनुरोध हर जगह ग़लत रहेगा, दूसरा provider नहीं आज़माते
    const next = chain[attempt];
    if (next) await logEvent(env, "fallback", { feature, provider: p.id, uid, detail: `${r.errorCode} → ${next.id}` });
  }
  await logEvent(env, "error", { feature, uid, detail: `${lastErr?.errorCode}: ${lastErr?.message}` });
  throw new HttpError(502, "provider_failed", "AI सेवा अभी जवाब नहीं दे पा रही। थोड़ी देर बाद कोशिश कीजिए।");
}

export { hasKey };
