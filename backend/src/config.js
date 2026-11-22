import { FEATURES, HttpError, now } from "./util.js";
import { ADAPTERS, ADAPTER_FEATURE } from "./directory.js";

export function defaultConfig() {
  const routes = {};
  for (const f of FEATURES) routes[f] = { primary: null, backups: [] };
  return {
    providers: {},
    routes,
    budgets: {
      freeOnly: true, // सिर्फ मुफ्त providers
      paidLock: true, // True रहते कोई भी paid provider नहीं चलेगा
      tzOffsetMin: 330,
      globalDailyRequests: 500,
      perUserDailyRequests: 50,
      perUserPerMinute: 6,
      globalDailyUsd: 0, // 0 = कोई paid खर्च मंज़ूर नहीं
      globalMonthlyUsd: 0,
      featureDailyRequests: {},
      featureDailyUsd: {},
      providerDailyRequests: {}
    },
    maxOutputTokens: 1024,
    requestTimeoutMs: 25000
  };
}

const ID_RE = /^[a-z0-9][a-z0-9_-]{0,39}$/;

function isPrivateHost(host) {
  const h = host.toLowerCase();
  if (h === "localhost" || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".localhost")) return true;
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(h)) return true; // सीधे IPv4 पते नहीं
  if (h.includes(":") || h.startsWith("[")) return true; // IPv6 पते नहीं
  return false;
}

export function validateBaseUrl(url, env = {}) {
  if (!url) return null;
  let u;
  try {
    u = new URL(url);
  } catch {
    return "baseUrl सही नहीं है।";
  }
  if (env.ALLOW_INSECURE_BASE_URL === "1") return null; // सिर्फ टेस्ट के लिए
  if (u.protocol !== "https:") return "baseUrl https:// से शुरू होना चाहिए।";
  if (isPrivateHost(u.hostname)) return "baseUrl में localhost/निजी पते की अनुमति नहीं है।";
  if (u.username || u.password) return "baseUrl में यूज़रनेम/पासवर्ड नहीं होना चाहिए।";
  return null;
}

const num = (v) => typeof v === "number" && Number.isFinite(v) && v >= 0;

/** पूरी config की जाँच। गलतियों की सूची लौटाता है (खाली सूची = ठीक)। */
export function validateConfig(cfg, env = {}) {
  const errs = [];
  if (!cfg || typeof cfg !== "object") return ["config सही नहीं है।"];
  const providers = cfg.providers || {};
  for (const [id, p] of Object.entries(providers)) {
    if (!ID_RE.test(id) || p.id !== id) errs.push(`provider id "${id}" सही नहीं है।`);
    if (!FEATURES.includes(p.feature)) errs.push(`${id}: feature सही नहीं है।`);
    if (typeof p.name !== "string" || !p.name.trim() || p.name.length > 80) errs.push(`${id}: नाम सही नहीं है।`);
    if (!ADAPTERS.includes(p.adapter)) errs.push(`${id}: इसका adapter अभी बना नहीं है (${p.adapter})।`);
    else if (ADAPTER_FEATURE[p.adapter] !== p.feature) errs.push(`${id}: adapter ${p.adapter} इस feature के लिए नहीं है।`);
    const bu = validateBaseUrl(p.baseUrl, env);
    if (bu) errs.push(`${id}: ${bu}`);
    if (p.adapter === "openai" && p.enabled && !p.model) errs.push(`${id}: model का नाम चाहिए।`);
    if (!["free", "free_unverified", "credits_card", "paid"].includes(p.tier)) errs.push(`${id}: tier सही नहीं है।`);
    if (p.priceIn != null && !num(p.priceIn)) errs.push(`${id}: priceIn सही नहीं है।`);
    if (p.priceOut != null && !num(p.priceOut)) errs.push(`${id}: priceOut सही नहीं है।`);
    if (p.priceRequest != null && !num(p.priceRequest)) errs.push(`${id}: priceRequest सही नहीं है।`);
    if (typeof p.model === "string" && p.model.length > 120) errs.push(`${id}: model बहुत लंबा है।`);
  }
  const routes = cfg.routes || {};
  for (const f of FEATURES) {
    const r = routes[f];
    if (!r) { errs.push(`routes.${f} नहीं है।`); continue; }
    const chain = [r.primary, ...(r.backups || [])].filter(Boolean);
    if ((r.backups || []).length > 2) errs.push(`${f}: backup 2 से ज़्यादा नहीं हो सकते।`);
    if (new Set(chain).size !== chain.length) errs.push(`${f}: एक provider दो बार नहीं आ सकता।`);
    for (const id of chain) {
      if (!providers[id]) errs.push(`${f}: provider "${id}" मौजूद नहीं है।`);
      else if (providers[id].feature !== f) errs.push(`${f}: provider "${id}" किसी और feature का है।`);
    }
  }
  const b = cfg.budgets || {};
  for (const k of ["globalDailyRequests", "perUserDailyRequests", "perUserPerMinute", "globalDailyUsd", "globalMonthlyUsd"]) {
    if (!num(b[k])) errs.push(`budgets.${k} सही संख्या नहीं है।`);
  }
  if (!Number.isInteger(b.tzOffsetMin) || Math.abs(b.tzOffsetMin) > 840) errs.push("budgets.tzOffsetMin सही नहीं है।");
  for (const k of ["featureDailyRequests", "featureDailyUsd", "providerDailyRequests"]) {
    for (const [key, v] of Object.entries(b[k] || {})) if (!num(v)) errs.push(`budgets.${k}.${key} सही संख्या नहीं है।`);
  }
  if (typeof b.freeOnly !== "boolean" || typeof b.paidLock !== "boolean") errs.push("freeOnly/paidLock सही नहीं हैं।");
  if (!(Number.isInteger(cfg.maxOutputTokens) && cfg.maxOutputTokens >= 16 && cfg.maxOutputTokens <= 4096)) errs.push("maxOutputTokens 16 से 4096 के बीच हो।");
  if (!(Number.isInteger(cfg.requestTimeoutMs) && cfg.requestTimeoutMs >= 3000 && cfg.requestTimeoutMs <= 28000)) errs.push("requestTimeoutMs 3000 से 28000 के बीच हो।");
  return errs;
}

let cache = { at: 0, version: 0, cfg: null };
const TTL_MS = 5000;

export function clearConfigCache() {
  cache = { at: 0, version: 0, cfg: null };
}

/** मौजूदा (published) config। 5 सेकंड तक cache, इसलिए नया provider अगले कुछ सेकंड में लागू हो जाता है। */
export async function loadConfig(env) {
  if (cache.cfg && Date.now() - cache.at < TTL_MS) return cache;
  const row = await env.DB.prepare("SELECT version, config FROM config_versions ORDER BY version DESC LIMIT 1").first();
  if (!row) {
    cache = { at: Date.now(), version: 0, cfg: defaultConfig() };
  } else {
    cache = { at: Date.now(), version: row.version, cfg: JSON.parse(row.config) };
  }
  return cache;
}

export async function saveConfig(env, cfg, uid, note) {
  const errs = validateConfig(cfg, env);
  if (errs.length) throw new HttpError(400, "invalid_config", errs.join(" | "));
  const res = await env.DB.prepare("INSERT INTO config_versions (config, note, created_by, created_at) VALUES (?, ?, ?, ?)")
    .bind(JSON.stringify(cfg), (note || "").slice(0, 200), uid, now()).run();
  const version = res.meta?.last_row_id;
  // सिर्फ आखिरी 50 version रखो
  await env.DB.prepare("DELETE FROM config_versions WHERE version <= (SELECT MAX(version) FROM config_versions) - 50").run();
  clearConfigCache();
  return version;
}
