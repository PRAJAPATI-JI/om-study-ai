import { json, fail, HttpError, readJson, now } from "./util.js";
import { verifyFirebaseToken, roleOf } from "./auth.js";
import { loadConfig } from "./config.js";
import { runFeature, featureAvailability, logEvent } from "./engine.js";
import { handleAdmin } from "./admin.js";

const SEC_HEADERS = {
  "x-content-type-options": "nosniff",
  "referrer-policy": "no-referrer",
  "x-frame-options": "DENY",
  "content-security-policy":
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
    "connect-src 'self' https://identitytoolkit.googleapis.com https://securetoken.googleapis.com; frame-ancestors 'none'; base-uri 'none'"
};

async function handleApi(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;

  if (path === "/health") return json({ ok: true, time: now() });

  if (path.startsWith("/admin/api/")) {
    const auth = await verifyFirebaseToken(request, env);
    const role = await roleOf(env, auth.uid);
    if (!role) {
      await logEvent(env, "admin_denied", { uid: auth.uid, detail: path });
      throw new HttpError(403, "not_admin", "आपके पास admin अधिकार नहीं है।");
    }
    return handleAdmin(request, env, auth, role);
  }

  if (path === "/v1/me" && request.method === "GET") {
    const auth = await verifyFirebaseToken(request, env);
    const t = now();
    await env.DB.prepare(
      "INSERT INTO users (uid, first_seen, last_seen) VALUES (?,?,?) ON CONFLICT(uid) DO UPDATE SET last_seen = excluded.last_seen"
    ).bind(auth.uid, t, t).run();
    const role = await roleOf(env, auth.uid);
    const { cfg, version } = await loadConfig(env);
    return json({ ok: true, uid: auth.uid, isAdmin: !!role, role, configVersion: version, features: await featureAvailability(env, cfg) });
  }

  const m = path.match(/^\/v1\/ai\/([a-z]+)$/);
  if (m && request.method === "POST") {
    const auth = await verifyFirebaseToken(request, env);
    const body = await readJson(request, 64 * 1024);
    return json(await runFeature(env, auth, m[1], body));
  }

  throw new HttpError(404, "not_found", "यह पता मौजूद नहीं है।");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const isApi = url.pathname === "/health" || url.pathname.startsWith("/v1/") || url.pathname.startsWith("/admin/api/");
    if (!isApi) {
      // admin वेबसाइट (static)
      const res = await env.ASSETS.fetch(request);
      const h = new Headers(res.headers);
      for (const [k, v] of Object.entries(SEC_HEADERS)) h.set(k, v);
      return new Response(res.body, { status: res.status, headers: h });
    }
    try {
      return await handleApi(request, env);
    } catch (e) {
      if (e instanceof HttpError) return fail(e.status, e.code, e.message);
      console.error("internal_error", e && e.message);
      return fail(500, "internal", "सर्वर में गड़बड़ हुई। बाद में कोशिश कीजिए।");
    }
  }
};
