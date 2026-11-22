export const FEATURES = ["text", "search", "image", "voice", "ocr", "video", "translation"];

export function json(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...extra }
  });
}

export function fail(status, code, message) {
  return json({ ok: false, error: code, message }, status);
}

export const now = () => Date.now();

/** स्थानीय (जैसे भारत, +330 मिनट) दिन की शुरुआत, UTC मिलीसेकंड में */
export function dayStart(ts, tzMin) {
  const shifted = ts + tzMin * 60000;
  return Math.floor(shifted / 86400000) * 86400000 - tzMin * 60000;
}

export function monthStart(ts, tzMin) {
  const d = new Date(ts + tzMin * 60000);
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), 1) - tzMin * 60000;
}

export function b64encode(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s);
}
export function b64decode(str) {
  const s = atob(str);
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}
export function b64urlDecode(str) {
  return b64decode(str.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(str.length / 4) * 4, "="));
}

export async function readJson(request, maxBytes = 64 * 1024) {
  const text = await request.text();
  if (text.length > maxBytes) throw new HttpError(413, "too_large", "अनुरोध बहुत बड़ा है।");
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    throw new HttpError(400, "bad_json", "अनुरोध सही JSON नहीं है।");
  }
}

export class HttpError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}
