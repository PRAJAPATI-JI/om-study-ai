import { b64urlDecode, HttpError } from "./util.js";

const DEFAULT_JWKS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
let jwksCache = { at: 0, keys: new Map(), url: "" };

async function getKey(env, kid) {
  const url = env.JWKS_URL || DEFAULT_JWKS;
  const fresh = Date.now() - jwksCache.at < 3600_000 && jwksCache.url === url;
  if (!fresh || !jwksCache.keys.has(kid)) {
    const res = await fetch(url);
    if (!res.ok) throw new HttpError(503, "auth_unavailable", "लॉगिन जाँच सेवा अभी उपलब्ध नहीं है।");
    const { keys } = await res.json();
    const map = new Map();
    for (const jwk of keys || []) {
      map.set(jwk.kid, await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]));
    }
    jwksCache = { at: Date.now(), keys: map, url };
  }
  return jwksCache.keys.get(kid);
}

/** Firebase ID token की असली जाँच: हस्ताक्षर, project, issuer, समय। UID सिर्फ यहीं से आता है, client के भेजे नाम से कभी नहीं। */
export async function verifyFirebaseToken(request, env) {
  const h = request.headers.get("authorization") || "";
  const m = h.match(/^Bearer\s+(.+)$/i);
  if (!m) throw new HttpError(401, "no_token", "लॉगिन ज़रूरी है।");
  const parts = m[1].split(".");
  if (parts.length !== 3) throw new HttpError(401, "bad_token", "लॉगिन टोकन सही नहीं है।");
  let header, payload;
  try {
    header = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[0])));
    payload = JSON.parse(new TextDecoder().decode(b64urlDecode(parts[1])));
  } catch {
    throw new HttpError(401, "bad_token", "लॉगिन टोकन सही नहीं है।");
  }
  if (header.alg !== "RS256" || !header.kid) throw new HttpError(401, "bad_token", "लॉगिन टोकन सही नहीं है।");
  const key = await getKey(env, header.kid);
  if (!key) throw new HttpError(401, "bad_token", "लॉगिन टोकन सही नहीं है।");
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5", key, b64urlDecode(parts[2]), new TextEncoder().encode(parts[0] + "." + parts[1])
  );
  if (!ok) throw new HttpError(401, "bad_signature", "लॉगिन टोकन सही नहीं है।");
  const project = env.FIREBASE_PROJECT_ID;
  const nowSec = Math.floor(Date.now() / 1000);
  if (payload.aud !== project || payload.iss !== `https://securetoken.google.com/${project}`) {
    throw new HttpError(401, "wrong_project", "यह टोकन इस ऐप का नहीं है।");
  }
  if (typeof payload.exp !== "number" || payload.exp < nowSec) throw new HttpError(401, "expired", "लॉगिन की अवधि खत्म हो गई। दोबारा लॉगिन कीजिए।");
  if (typeof payload.iat === "number" && payload.iat > nowSec + 300) throw new HttpError(401, "bad_token", "लॉगिन टोकन सही नहीं है।");
  if (!payload.sub || typeof payload.sub !== "string") throw new HttpError(401, "bad_token", "लॉगिन टोकन सही नहीं है।");
  return { uid: payload.sub, email: payload.email || "" };
}

/** भूमिका: owner (सिर्फ Worker variable OWNER_UID वाला), viewer (मालिक द्वारा जोड़ा, सिर्फ पढ़ना), या null */
export async function roleOf(env, uid) {
  if (env.OWNER_UID && uid === env.OWNER_UID) return "owner";
  const row = await env.DB.prepare("SELECT role FROM admins WHERE uid = ?").bind(uid).first();
  return row ? row.role : null;
}
