/**
 * Provider adapters। हर adapter एक जैसा नतीजा लौटाता है:
 *   ok:true  → { ok, result, tokensIn, tokensOut, headers }
 *   ok:false → { ok, errorCode, retryable, fallbackable, status, message, headers }
 * API key कभी संदेश/लॉग में नहीं जाती।
 */

const KEEP_HEADERS = /^(x-ratelimit-|retry-after)/i;

function pickHeaders(res) {
  const out = {};
  for (const [k, v] of res.headers.entries()) if (KEEP_HEADERS.test(k)) out[k.toLowerCase()] = v;
  return out;
}

export function classifyStatus(status) {
  if (status === 429) return { errorCode: "rate_limit", retryable: true, fallbackable: true };
  if (status === 402) return { errorCode: "quota_exhausted", retryable: true, fallbackable: true };
  if (status === 408) return { errorCode: "timeout", retryable: true, fallbackable: true };
  if (status >= 500) return { errorCode: "server_error", retryable: true, fallbackable: true };
  if (status === 401 || status === 403) return { errorCode: "auth_error", retryable: false, fallbackable: true };
  if (status === 404) return { errorCode: "not_found", retryable: false, fallbackable: true };
  return { errorCode: "bad_request", retryable: false, fallbackable: false };
}

function scrub(text, key) {
  let t = String(text || "").slice(0, 300);
  if (key) t = t.split(key).join("[key]");
  return t;
}

async function send(url, init, key, timeoutMs, fetchImpl) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetchImpl(url, { ...init, signal: ctrl.signal });
    const headers = pickHeaders(res);
    if (!res.ok) {
      const body = await res.text().catch(() => "");
      const c = classifyStatus(res.status);
      return { ok: false, status: res.status, ...c, message: scrub(body, key), headers };
    }
    let data;
    try {
      data = await res.json();
    } catch {
      return { ok: false, status: 502, errorCode: "bad_response", retryable: true, fallbackable: true, message: "जवाब पढ़ा नहीं जा सका।", headers };
    }
    return { ok: true, data, headers };
  } catch (e) {
    const aborted = e && e.name === "AbortError";
    return {
      ok: false, status: 0, errorCode: aborted ? "timeout" : "network", retryable: true, fallbackable: true,
      message: aborted ? "समय खत्म हो गया।" : "नेटवर्क में दिक्कत।", headers: {}
    };
  } finally {
    clearTimeout(timer);
  }
}

const join = (base, path) => base.replace(/\/+$/, "") + path;

export async function callOpenAI(p, key, input, { timeoutMs, maxTokens, fetchImpl }) {
  const body = {
    model: p.model,
    messages: input.messages,
    max_tokens: Math.min(input.maxTokens || maxTokens, maxTokens),
    temperature: 0.4
  };
  const r = await send(join(p.baseUrl, "/chat/completions"), {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
    body: JSON.stringify(body)
  }, key, timeoutMs, fetchImpl);
  if (!r.ok) return r;
  const text = r.data?.choices?.[0]?.message?.content;
  if (typeof text !== "string" || !text.trim()) {
    return { ok: false, status: 502, errorCode: "empty_response", retryable: true, fallbackable: true, message: "खाली जवाब मिला।", headers: r.headers };
  }
  const u = r.data.usage || {};
  return {
    ok: true, result: { text: text.trim() },
    tokensIn: Number.isInteger(u.prompt_tokens) ? u.prompt_tokens : null,
    tokensOut: Number.isInteger(u.completion_tokens) ? u.completion_tokens : null,
    headers: r.headers
  };
}

export async function callTavily(p, key, input, { timeoutMs, fetchImpl }) {
  const r = await send(join(p.baseUrl, "/search"), {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${key}` },
    body: JSON.stringify({ query: input.query, max_results: 5, search_depth: "basic" })
  }, key, timeoutMs, fetchImpl);
  if (!r.ok) return r;
  const results = (r.data?.results || []).slice(0, 8).map((x) => ({
    title: String(x.title || "").slice(0, 200), url: String(x.url || ""), snippet: String(x.content || "").slice(0, 500)
  })).filter((x) => /^https?:\/\//.test(x.url));
  return { ok: true, result: { query: input.query, results, retrievedAt: new Date().toISOString() }, tokensIn: null, tokensOut: null, headers: r.headers };
}

export async function callBrave(p, key, input, { timeoutMs, fetchImpl }) {
  const url = join(p.baseUrl, "/web/search") + "?count=5&q=" + encodeURIComponent(input.query);
  const r = await send(url, { method: "GET", headers: { accept: "application/json", "x-subscription-token": key } }, key, timeoutMs, fetchImpl);
  if (!r.ok) return r;
  const results = (r.data?.web?.results || []).slice(0, 8).map((x) => ({
    title: String(x.title || "").slice(0, 200), url: String(x.url || ""), snippet: String(x.description || "").slice(0, 500)
  })).filter((x) => /^https?:\/\//.test(x.url));
  return { ok: true, result: { query: input.query, results, retrievedAt: new Date().toISOString() }, tokensIn: null, tokensOut: null, headers: r.headers };
}

export async function callLibreTranslate(p, key, input, { timeoutMs, fetchImpl }) {
  const r = await send(join(p.baseUrl, "/translate"), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ q: input.text, source: input.source || "auto", target: input.target, format: "text", api_key: key })
  }, key, timeoutMs, fetchImpl);
  if (!r.ok) return r;
  const t = r.data?.translatedText;
  if (typeof t !== "string") {
    return { ok: false, status: 502, errorCode: "bad_response", retryable: true, fallbackable: true, message: "अनुवाद नहीं मिला।", headers: r.headers };
  }
  return { ok: true, result: { text: t }, tokensIn: null, tokensOut: null, headers: r.headers };
}

export function callProvider(p, key, input, opts) {
  switch (p.adapter) {
    case "openai": return callOpenAI(p, key, input, opts);
    case "tavily": return callTavily(p, key, input, opts);
    case "brave": return callBrave(p, key, input, opts);
    case "libretranslate": return callLibreTranslate(p, key, input, opts);
    default:
      return Promise.resolve({ ok: false, status: 501, errorCode: "not_implemented", retryable: false, fallbackable: true, message: "यह adapter अभी बना नहीं है।", headers: {} });
  }
}
