import { DatabaseSync } from "node:sqlite";
import { readFileSync } from "node:fs";
import http from "node:http";
import { b64encode } from "../src/util.js";
import { clearConfigCache } from "../src/config.js";

const schema = readFileSync(new URL("../migrations/0001_init.sql", import.meta.url), "utf8");

/** D1 जैसा छोटा नकली (shim), असल में node:sqlite। असली D1 पर टेस्ट नहीं हुआ। */
export function makeDB() {
  const db = new DatabaseSync(":memory:");
  db.exec(schema);
  return {
    raw: db,
    prepare(sql) {
      const stmt = db.prepare(sql);
      const make = (args) => ({
        bind: (...more) => make(more),
        first: async () => stmt.get(...args) ?? null,
        all: async () => ({ results: stmt.all(...args) }),
        run: async () => {
          const r = stmt.run(...args);
          return { meta: { last_row_id: Number(r.lastInsertRowid), changes: r.changes } };
        }
      });
      return make([]);
    }
  };
}

const b64url = (buf) => b64encode(new Uint8Array(buf)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const enc = (o) => b64url(new TextEncoder().encode(JSON.stringify(o)));

export async function makeJwtIssuer(project = "om-study-ai") {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]
  );
  const jwk = { ...(await crypto.subtle.exportKey("jwk", pair.publicKey)), kid: "k1", alg: "RS256", use: "sig" };
  const other = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" }, true, ["sign", "verify"]
  );
  async function sign(claims, { key = pair.privateKey, kid = "k1" } = {}) {
    const header = enc({ alg: "RS256", typ: "JWT", kid });
    const nowS = Math.floor(Date.now() / 1000);
    const payload = enc({ aud: project, iss: `https://securetoken.google.com/${project}`, iat: nowS - 10, exp: nowS + 3600, ...claims });
    const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(header + "." + payload));
    return `${header}.${payload}.${b64url(sig)}`;
  }
  return { jwk, sign, wrongKey: other.privateKey };
}

export function startServer(handler) {
  return new Promise((resolve) => {
    const server = http.createServer(async (req, res) => {
      const chunks = [];
      for await (const c of req) chunks.push(c);
      req.bodyText = Buffer.concat(chunks).toString();
      handler(req, res);
    });
    server.listen(0, "127.0.0.1", () => resolve({ server, port: server.address().port }));
  });
}

/** नकली provider सर्वर: path के पहले हिस्से (/a, /b ...) से व्यवहार तय होता है */
export async function startMockProviders() {
  const behaviour = {};
  const seen = [];
  const { server, port } = await startServer((req, res) => {
    const prefix = "/" + req.url.split("/")[1];
    const b = behaviour[prefix] || { status: 200 };
    seen.push({ prefix, url: req.url, method: req.method, headers: req.headers, body: req.bodyText });
    const reply = () => {
      res.writeHead(b.status, { "content-type": "application/json", ...(b.headers || {}) });
      if (b.status !== 200) return res.end(JSON.stringify({ error: { message: "mock error " + b.status } }));
      if (req.url.includes("/chat/completions")) {
        const usage = b.noUsage ? undefined : { prompt_tokens: 12, completion_tokens: 7 };
        return res.end(JSON.stringify({ choices: [{ message: { content: b.text || "नमस्ते, यह जवाब है" } }], usage }));
      }
      if (req.url.includes("/search")) {
        return res.end(JSON.stringify({ results: [{ title: "T", url: "https://example.com/x", content: "c" }, { title: "bad", url: "javascript:1", content: "x" }] }));
      }
      if (req.url.includes("/translate")) return res.end(JSON.stringify({ translatedText: "नमस्ते" }));
      res.end("{}");
    };
    if (b.delayMs) setTimeout(reply, b.delayMs); else reply();
  });
  return { port, behaviour, seen, base: (p) => `http://127.0.0.1:${port}${p}`, close: () => server.close() };
}

export async function makeEnv(overrides = {}) {
  const issuer = await makeJwtIssuer();
  const jwks = await startServer((req, res) => {
    res.writeHead(200, { "content-type": "application/json" });
    res.end(JSON.stringify({ keys: [issuer.jwk] }));
  });
  const env = {
    DB: makeDB(),
    FIREBASE_PROJECT_ID: "om-study-ai",
    OWNER_UID: "owner-uid-1",
    MASTER_KEY: "test-master-key-0123456789abcdef",
    JWKS_URL: `http://127.0.0.1:${jwks.port}/jwks`,
    ALLOW_INSECURE_BASE_URL: "1",
    ...overrides
  };
  clearConfigCache();
  return { env, issuer, closeJwks: () => jwks.server.close() };
}

export function req(path, { method = "GET", token, body } = {}) {
  const headers = {};
  if (token) headers.authorization = `Bearer ${token}`;
  if (body !== undefined) headers["content-type"] = "application/json";
  return new Request("https://api.test" + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
}
