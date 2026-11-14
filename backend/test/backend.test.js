import test, { after } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";
import { defaultConfig, validateConfig, validateBaseUrl, clearConfigCache } from "../src/config.js";
import { makeEnv, startMockProviders, req } from "./helpers.js";

let env, issuer, mock, closeJwks;
const call = async (r) => {
  const res = await worker.fetch(r, env);
  return { status: res.status, body: await res.json().catch(() => null) };
};
const tok = (uid, extra) => issuer.sign({ sub: uid, ...extra });

async function setup() {
  ({ env, issuer, closeJwks } = await makeEnv());
  mock ||= await startMockProviders();
  for (const k of Object.keys(mock.behaviour)) delete mock.behaviour[k];
  mock.seen.length = 0;
  clearConfigCache();
}

const prov = (id, over = {}) => ({
  id, feature: "text", name: "Provider " + id, adapter: "openai", baseUrl: mock.base("/" + id), model: "m-" + id,
  tier: "free", enabled: true, ...over
});
function cfgWith(providers, route, budgets = {}) {
  const c = defaultConfig();
  for (const p of providers) c.providers[p.id] = p;
  if (route) c.routes[route.feature || "text"] = { primary: route.primary, backups: route.backups || [] };
  Object.assign(c.budgets, { perUserPerMinute: 100, perUserDailyRequests: 1000, globalDailyRequests: 10000 }, budgets);
  return c;
}
async function publish(cfg, note = "t") {
  const t = await tok("owner-uid-1");
  return call(req("/admin/api/config", { method: "PUT", token: t, body: { config: cfg, note } }));
}
async function setKey(id, key = "sk-test-SECRET-12345678") {
  const t = await tok("owner-uid-1");
  return call(req(`/admin/api/provider/${id}/key`, { method: "PUT", token: t, body: { key } }));
}
async function chat(uid = "user-1", body) {
  const t = await tok(uid);
  return call(req("/v1/ai/text", { method: "POST", token: t, body: body || { task: "chat", messages: [{ role: "user", content: "नमस्ते" }] } }));
}
const rows = (sql, ...a) => env.DB.raw.prepare(sql).all(...a);

// ---------------- AUTH ----------------
test("auth: बिना टोकन, गलत हस्ताक्षर, पुराना, दूसरे प्रोजेक्ट का टोकन — सब मना", async () => {
  await setup();
  assert.equal((await call(req("/v1/me"))).status, 401);
  const forged = await issuer.sign({ sub: "x" }, { key: issuer.wrongKey });
  assert.equal((await call(req("/v1/me", { token: forged }))).status, 401);
  const expired = await issuer.sign({ sub: "x", exp: Math.floor(Date.now() / 1000) - 10 });
  assert.equal((await call(req("/v1/me", { token: expired }))).status, 401);
  const otherProject = await issuer.sign({ sub: "x", aud: "other", iss: "https://securetoken.google.com/other" });
  assert.equal((await call(req("/v1/me", { token: otherProject }))).status, 401);
  assert.equal((await call(req("/v1/me", { token: "a.b.c" }))).status, 401);
  const ok = await call(req("/v1/me", { token: await tok("user-1") }));
  assert.equal(ok.status, 200);
  assert.equal(ok.body.uid, "user-1");
  assert.equal(ok.body.isAdmin, false);
  closeJwks();
});

test("/v1/me: उपयोगकर्ता दर्ज होता है, दोबारा बुलाने पर दोहराव नहीं", async () => {
  await setup();
  await call(req("/v1/me", { token: await tok("user-9") }));
  await call(req("/v1/me", { token: await tok("user-9") }));
  assert.equal(rows("SELECT * FROM users WHERE uid='user-9'").length, 1);
  closeJwks();
});

// ---------------- ADMIN ACCESS ----------------
test("admin: सामान्य उपयोगकर्ता को सारे /admin/api रास्तों पर 403", async () => {
  await setup();
  const t = await tok("user-1");
  for (const [m, p] of [["GET", "/admin/api/overview"], ["GET", "/admin/api/config"], ["PUT", "/admin/api/config"], ["GET", "/admin/api/users"], ["POST", "/admin/api/rollback"], ["GET", "/admin/api/admins"]]) {
    const r = await call(req(p, { method: m, token: t, body: m === "GET" ? undefined : {} }));
    assert.equal(r.status, 403, `${m} ${p}`);
  }
  assert.ok(rows("SELECT * FROM events WHERE kind='admin_denied'").length >= 1);
  assert.equal((await call(req("/admin/api/overview"))).status, 401);
  closeJwks();
});

test("admin: client खुद को admin नहीं बना सकता (टोकन के दावे/हेडर काम नहीं करते)", async () => {
  await setup();
  const t = await tok("user-1", { admin: true, role: "owner" });
  const r = await worker.fetch(new Request("https://api.test/admin/api/overview", { headers: { authorization: `Bearer ${t}`, "x-admin": "1", "x-uid": "owner-uid-1" } }), env);
  assert.equal(r.status, 403);
  closeJwks();
});

test("admin: मालिक को पूरी पहुँच; viewer सिर्फ पढ़ सकता है", async () => {
  await setup();
  const owner = await tok("owner-uid-1");
  assert.equal((await call(req("/admin/api/overview", { token: owner }))).status, 200);
  const add = await call(req("/admin/api/admins", { method: "POST", token: owner, body: { uid: "viewer-uid-77" } }));
  assert.equal(add.status, 200);
  const v = await tok("viewer-uid-77");
  assert.equal((await call(req("/admin/api/overview", { token: v }))).status, 200);
  assert.equal((await call(req("/admin/api/config", { method: "PUT", token: v, body: { config: defaultConfig() } }))).status, 403);
  assert.equal((await call(req("/admin/api/admins", { token: v }))).status, 403);
  assert.equal((await call(req("/admin/api/provider/x/key", { method: "PUT", token: v, body: { key: "abcdefgh12" } }))).status, 403);
  assert.ok(rows("SELECT * FROM audit WHERE action='admin_add'").length === 1);
  closeJwks();
});

// ---------------- CONFIG ----------------
test("config: अमान्य बातें लौटा दी जाती हैं (SSRF, adapter बेमेल, दो बार provider, खराब संख्या)", async () => {
  await setup();
  assert.ok(validateBaseUrl("http://example.com", {}));
  assert.ok(validateBaseUrl("https://localhost/x", {}));
  assert.ok(validateBaseUrl("https://169.254.169.254/latest", {}));
  assert.ok(validateBaseUrl("https://[::1]/", {}));
  assert.ok(validateBaseUrl("https://user:pw@example.com/", {}));
  assert.equal(validateBaseUrl("https://api.groq.com/openai/v1", {}), null);
  const bad = cfgWith([prov("a", { adapter: "tavily" })], { primary: "a" });
  assert.ok(validateConfig(bad, env).some((e) => e.includes("इस feature के लिए नहीं")));
  const unimpl = cfgWith([prov("a", { adapter: "fal" })], { primary: "a" });
  assert.ok(validateConfig(unimpl, env).some((e) => e.includes("बना नहीं है")));
  const dup = cfgWith([prov("a")], { primary: "a", backups: ["a"] });
  assert.ok(validateConfig(dup, env).some((e) => e.includes("दो बार")));
  const missing = cfgWith([prov("a")], { primary: "zzz" });
  assert.ok(validateConfig(missing, env).length > 0);
  const neg = cfgWith([prov("a")], { primary: "a" }, { perUserDailyRequests: -1 });
  assert.ok(validateConfig(neg, env).length > 0);
  const cross = cfgWith([prov("a", { feature: "search", adapter: "tavily" })], { primary: "a" });
  assert.ok(validateConfig(cross, env).length > 0, "search provider text route में नहीं");
  const t = await tok("owner-uid-1");
  const r = await call(req("/admin/api/config", { method: "PUT", token: t, body: { config: bad } }));
  assert.equal(r.status, 400);
  assert.equal(r.body.error, "invalid_config");
  closeJwks();
});

test("config: publish पर version बढ़ता है और rollback पुराना लौटाता है", async () => {
  await setup();
  const a = await publish(cfgWith([prov("a")], { primary: "a" }), "पहला");
  const b = await publish(cfgWith([prov("a"), prov("b")], { primary: "b", backups: ["a"] }), "दूसरा");
  assert.equal(a.body.version + 1, b.body.version);
  const t = await tok("owner-uid-1");
  let cur = await call(req("/admin/api/config", { token: t }));
  assert.equal(cur.body.config.routes.text.primary, "b");
  const rb = await call(req("/admin/api/rollback", { method: "POST", token: t, body: { version: a.body.version } }));
  assert.equal(rb.status, 200);
  cur = await call(req("/admin/api/config", { token: t }));
  assert.equal(cur.body.config.routes.text.primary, "a");
  assert.equal(cur.body.version, rb.body.version);
  assert.equal((await call(req("/admin/api/rollback", { method: "POST", token: t, body: { version: 9999 } }))).status, 404);
  closeJwks();
});

// ---------------- KEYS ----------------
test("key: सेव होने पर सिर्फ masked दिखती है; D1 में plain text नहीं; दोबारा बदलना और हटाना", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }));
  const key = "sk-test-SECRET-12345678";
  const s = await setKey("a", key);
  assert.equal(s.status, 200);
  assert.equal(s.body.masked, "••••••••5678");
  const t = await tok("owner-uid-1");
  const cfg = await call(req("/admin/api/config", { token: t }));
  const dump = JSON.stringify(cfg.body);
  assert.ok(!dump.includes(key) && !dump.includes("SECRET"), "पूरी key कहीं नहीं दिखनी चाहिए");
  assert.equal(cfg.body.keys.a.masked, "••••••••5678");
  const stored = rows("SELECT * FROM secrets")[0];
  assert.ok(!JSON.stringify(stored).includes("SECRET"));
  await setKey("a", "sk-new-KEY-abcdef9999");
  assert.equal((await call(req("/admin/api/config", { token: t }))).body.keys.a.masked, "••••••••9999");
  assert.equal((await setKey("a", "short")).status, 400);
  assert.equal((await setKey("nope")).status, 404);
  const del = await call(req("/admin/api/provider/a/key", { method: "DELETE", token: t }));
  assert.equal(del.status, 200);
  assert.equal(rows("SELECT * FROM secrets").length, 0);
  closeJwks();
});

test("key: MASTER_KEY न हो तो key सेव नहीं होती", async () => {
  await setup();
  delete env.MASTER_KEY;
  await publish(cfgWith([prov("a")], { primary: "a" }));
  const r = await setKey("a");
  assert.equal(r.status, 500);
  assert.equal(r.body.error, "no_master_key");
  assert.equal(rows("SELECT * FROM secrets").length, 0);
  closeJwks();
});

// ---------------- TEST CONNECTION ----------------
test("connection test: सफल और असफल दोनों का नतीजा सेव होता है", async () => {
  await setup();
  await publish(cfgWith([prov("a"), prov("b")], { primary: "a", backups: ["b"] }));
  await setKey("a");
  await setKey("b");
  mock.behaviour["/b"] = { status: 401 };
  const t = await tok("owner-uid-1");
  const ok = await call(req("/admin/api/provider/a/test", { method: "POST", token: t, body: {} }));
  assert.equal(ok.body.ok, true);
  const bad = await call(req("/admin/api/provider/b/test", { method: "POST", token: t, body: {} }));
  assert.equal(bad.body.ok, false);
  assert.equal(bad.body.errorCode, "auth_error");
  const st = rows("SELECT * FROM provider_status ORDER BY provider_id");
  assert.equal(st[0].last_test_ok, 1);
  assert.equal(st[1].last_test_ok, 0);
  // key असली अनुरोध में Bearer के रूप में गई
  assert.equal(mock.seen[0].headers.authorization, "Bearer sk-test-SECRET-12345678");
  // key संदेश में नहीं
  assert.ok(!JSON.stringify(bad.body).includes("SECRET"));
  assert.equal((await call(req("/admin/api/provider/nokey/test", { method: "POST", token: t, body: {} }))).status, 404);
  closeJwks();
});

test("connection test: बिना key के मना", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }));
  const t = await tok("owner-uid-1");
  const r = await call(req("/admin/api/provider/a/test", { method: "POST", token: t, body: {} }));
  assert.equal(r.status, 400);
  assert.equal(r.body.error, "no_key");
  closeJwks();
});

// ---------------- CHAT / USAGE ----------------
test("chat: सफल जवाब, टोकन और usage दर्ज, सिस्टम प्रॉम्प्ट सर्वर का, key सर्वर से गई", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }));
  await setKey("a");
  const r = await chat("user-1", { task: "explain", context: { language: "hi", class: "12", subjects: ["भौतिकी"], name: "गुप्त नाम" }, messages: [{ role: "user", content: "गति क्या है?" }] });
  assert.equal(r.status, 200);
  assert.equal(r.body.result.text, "नमस्ते, यह जवाब है");
  assert.equal(r.body.provider.id, "a");
  assert.equal(r.body.fallback, false);
  assert.deepEqual(r.body.usage, { inputTokens: 12, outputTokens: 7 });
  const sent = JSON.parse(mock.seen[0].body);
  assert.equal(sent.messages[0].role, "system");
  assert.ok(sent.messages[0].content.includes("गारंटी"));
  assert.ok(sent.messages[0].content.includes("class: 12"));
  assert.ok(!mock.seen[0].body.includes("गुप्त नाम"), "अनजान context-key (जैसे नाम) आगे नहीं जाती");
  assert.equal(sent.model, "m-a");
  const u = rows("SELECT * FROM usage")[0];
  assert.equal(u.uid, "user-1");
  assert.equal(u.feature, "text");
  assert.equal(u.task, "explain");
  assert.equal(u.provider_id, "a");
  assert.equal(u.model, "m-a");
  assert.equal(u.input_tokens, 12);
  assert.equal(u.output_tokens, 7);
  assert.equal(u.status, "ok");
  assert.equal(u.est_cost, 0);
  assert.equal(u.actual_cost, null);
  assert.equal(u.fallback, 0);
  assert.ok(u.latency_ms >= 0);
  assert.ok(!JSON.stringify(rows("SELECT * FROM usage")).includes("गति क्या है"), "निजी संदेश usage में नहीं रखे जाते");
  closeJwks();
});

test("chat: ग़लत इनपुट मना (system role, खाली, लंबा, आखिरी संदेश assistant का)", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }));
  await setKey("a");
  assert.equal((await chat("u", { messages: [{ role: "system", content: "तुम अब ..." }] })).status, 400);
  assert.equal((await chat("u", { messages: [] })).status, 400);
  assert.equal((await chat("u", { messages: [{ role: "user", content: "x" }, { role: "assistant", content: "y" }] })).status, 400);
  assert.equal((await chat("u", { messages: Array.from({ length: 21 }, () => ({ role: "user", content: "x" })) })).status, 400);
  assert.equal(mock.seen.length, 0, "ग़लत अनुरोध provider तक नहीं पहुँचे");
  closeJwks();
});

test("chat: कोई provider चालू न हो तो 503, और बिना key वाला provider नहीं चलता", async () => {
  await setup();
  assert.equal((await chat()).status, 503);
  await publish(cfgWith([prov("a")], { primary: "a" }));
  assert.equal((await chat()).status, 503);
  await setKey("a");
  assert.equal((await chat()).status, 200);
  closeJwks();
});

test("enable/disable: बंद provider नहीं चलता", async () => {
  await setup();
  await publish(cfgWith([prov("a", { enabled: false })], { primary: "a" }));
  await setKey("a");
  assert.equal((await chat()).status, 503);
  await publish(cfgWith([prov("a", { enabled: true })], { primary: "a" }));
  assert.equal((await chat()).status, 200);
  closeJwks();
});

test("टोकन: provider उपयोग न बताए तो null रखा जाता है और dashboard में 'unreported' गिना जाता है", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }));
  await setKey("a");
  mock.behaviour["/a"] = { status: 200, noUsage: true };
  await chat();
  const u = rows("SELECT * FROM usage")[0];
  assert.equal(u.input_tokens, null);
  const ov = await call(req("/admin/api/overview?range=day", { token: await tok("owner-uid-1") }));
  assert.equal(ov.body.tokens.unreportedRequests, 1);
  assert.equal(ov.body.cost.actualBilledUsd, "Unavailable");
  closeJwks();
});

// ---------------- SWITCHING & FALLBACK ----------------
test("instant switching: नया primary publish होते ही अगली request उसी से", async () => {
  await setup();
  await publish(cfgWith([prov("a"), prov("b")], { primary: "a", backups: [] }));
  await setKey("a"); await setKey("b");
  assert.equal((await chat()).body.provider.id, "a");
  await publish(cfgWith([prov("a"), prov("b")], { primary: "b", backups: ["a"] }));
  assert.equal((await chat()).body.provider.id, "b");
  closeJwks();
});

test("fallback: primary 429 → backup; हर provider सिर्फ एक बार; events और usage में दर्ज", async () => {
  await setup();
  await publish(cfgWith([prov("a"), prov("b")], { primary: "a", backups: ["b"] }));
  await setKey("a"); await setKey("b");
  mock.behaviour["/a"] = { status: 429, headers: { "x-ratelimit-remaining-requests": "0" } };
  const r = await chat();
  assert.equal(r.status, 200);
  assert.equal(r.body.provider.id, "b");
  assert.equal(r.body.fallback, true);
  assert.equal(mock.seen.filter((s) => s.prefix === "/a").length, 1, "primary पर दोबारा retry नहीं");
  assert.equal(mock.seen.filter((s) => s.prefix === "/b").length, 1);
  const u = rows("SELECT provider_id, status, error_code, fallback, attempt FROM usage ORDER BY id");
  assert.deepEqual(u.map((x) => [x.provider_id, x.status, x.error_code, x.fallback, x.attempt]), [["a", "error", "rate_limit", 0, 1], ["b", "ok", null, 1, 2]]);
  const ev = rows("SELECT kind FROM events").map((e) => e.kind);
  assert.ok(ev.includes("fallback") && ev.includes("rate_limit"));
  const st = rows("SELECT * FROM provider_status WHERE provider_id='a'")[0];
  assert.ok(st.last_error.includes("rate_limit"));
  assert.ok(st.rate_headers.includes("x-ratelimit-remaining-requests"));
  closeJwks();
});

test("fallback: 500, quota(402) भी fallback; दोनों backup तक जाता है; सब असफल → 502", async () => {
  await setup();
  await publish(cfgWith([prov("a"), prov("b"), prov("c")], { primary: "a", backups: ["b", "c"] }));
  for (const id of ["a", "b", "c"]) await setKey(id);
  mock.behaviour["/a"] = { status: 500 };
  mock.behaviour["/b"] = { status: 402 };
  let r = await chat();
  assert.equal(r.body.provider.id, "c");
  mock.behaviour["/c"] = { status: 503 };
  r = await chat();
  assert.equal(r.status, 502);
  assert.equal(r.body.error, "provider_failed");
  const calls = mock.seen.slice(-3).map((s) => s.prefix);
  assert.deepEqual(calls, ["/a", "/b", "/c"], "हर provider सिर्फ एक बार");
  assert.ok(!JSON.stringify(r.body).includes("mock error"), "provider की भीतरी गलती client को नहीं");
  assert.ok(rows("SELECT * FROM events WHERE kind='error'").length >= 1);
  closeJwks();
});

test("fallback: 400 (ग़लत अनुरोध) पर backup नहीं आज़माते", async () => {
  await setup();
  await publish(cfgWith([prov("a"), prov("b")], { primary: "a", backups: ["b"] }));
  await setKey("a"); await setKey("b");
  mock.behaviour["/a"] = { status: 400 };
  const r = await chat();
  assert.equal(r.status, 502);
  assert.equal(mock.seen.filter((s) => s.prefix === "/b").length, 0);
  closeJwks();
});

test("fallback: timeout पर backup (प्रतीक्षा सीमा 3 सेकंड)", async () => {
  await setup();
  const c = cfgWith([prov("a"), prov("b")], { primary: "a", backups: ["b"] });
  c.requestTimeoutMs = 3000;
  await publish(c);
  await setKey("a"); await setKey("b");
  mock.behaviour["/a"] = { status: 200, delayMs: 3600 };
  const r = await chat();
  assert.equal(r.status, 200);
  assert.equal(r.body.provider.id, "b");
  assert.equal(rows("SELECT error_code FROM usage WHERE attempt=1")[0].error_code, "timeout");
  closeJwks();
});

test("feature अलग-अलग: text बदलने से search/translation की config अछूती", async () => {
  await setup();
  const c = cfgWith([prov("a"), prov("s", { feature: "search", adapter: "tavily" }), prov("tr", { feature: "translation", adapter: "libretranslate" })], { primary: "a" });
  c.routes.search = { primary: "s", backups: [] };
  c.routes.translation = { primary: "tr", backups: [] };
  await publish(c);
  const before = JSON.stringify((await call(req("/admin/api/config", { token: await tok("owner-uid-1") }))).body.config.routes);
  const c2 = JSON.parse(JSON.stringify(c));
  c2.providers.b = prov("b");
  c2.routes.text = { primary: "b", backups: ["a"] };
  await publish(c2);
  const after = (await call(req("/admin/api/config", { token: await tok("owner-uid-1") }))).body.config.routes;
  assert.deepEqual(after.search, JSON.parse(before).search);
  assert.deepEqual(after.translation, JSON.parse(before).translation);
  assert.equal(after.image.primary, null);
  closeJwks();
});

// ---------------- PAID / BUDGET ----------------
test("free-only/paid lock: paid provider कभी नहीं चलता जब तक मालिक सब न खोले", async () => {
  await setup();
  const paid = prov("p", { tier: "paid", paidApproved: true, priceIn: 1, priceOut: 2 });
  const free = prov("f");
  // paid primary, free backup, पर lock चालू
  await publish(cfgWith([paid, free], { primary: "p", backups: ["f"] }));
  await setKey("p"); await setKey("f");
  let r = await chat();
  assert.equal(r.body.provider.id, "f");
  assert.equal(mock.seen.filter((s) => s.prefix === "/p").length, 0, "paid provider को छुआ तक नहीं");
  // सिर्फ paidLock हटाया, freeOnly अभी भी चालू
  await publish(cfgWith([paid, free], { primary: "p", backups: ["f"] }, { paidLock: false }));
  assert.equal((await chat()).body.provider.id, "f");
  // दोनों हटाए पर बजट 0
  await publish(cfgWith([paid, free], { primary: "p", backups: ["f"] }, { paidLock: false, freeOnly: false }));
  assert.equal((await chat()).body.provider.id, "f");
  // paidApproved के बिना
  await publish(cfgWith([{ ...paid, paidApproved: false }, free], { primary: "p", backups: ["f"] }, { paidLock: false, freeOnly: false, globalDailyUsd: 1, globalMonthlyUsd: 5 }));
  assert.equal((await chat()).body.provider.id, "f");
  // सब मंज़ूर
  await publish(cfgWith([paid, free], { primary: "p", backups: ["f"] }, { paidLock: false, freeOnly: false, globalDailyUsd: 1, globalMonthlyUsd: 5 }));
  assert.equal((await chat()).body.provider.id, "p");
  closeJwks();
});

test("credits_card (जैसे Cerebras/Brave) को भी paid की तरह रोका जाता है", async () => {
  await setup();
  await publish(cfgWith([prov("cc", { tier: "credits_card", paidApproved: true })], { primary: "cc" }));
  await setKey("cc");
  assert.equal((await chat()).status, 503);
  closeJwks();
});

test("बजट: खर्च सीमा पार होते ही paid provider रुकता है और free backup चलता है; अनुमानित खर्च दर्ज", async () => {
  await setup();
  const paid = prov("p", { tier: "paid", paidApproved: true, priceIn: 1000000, priceOut: 1000000 }); // 1 USD/टोकन, ताकि जल्दी पार हो
  const free = prov("f");
  const open = { paidLock: false, freeOnly: false, globalDailyUsd: 10, globalMonthlyUsd: 100 };
  await publish(cfgWith([paid, free], { primary: "p", backups: ["f"] }, open));
  await setKey("p"); await setKey("f");
  const r1 = await chat();
  assert.equal(r1.body.provider.id, "p");
  assert.equal(rows("SELECT est_cost FROM usage ORDER BY id")[0].est_cost, 19); // (12+7) टोकन × 1 USD
  const r2 = await chat();
  assert.equal(r2.body.provider.id, "f", "बजट (10 USD) पार हो चुका");
  assert.ok(rows("SELECT * FROM events WHERE kind='budget_block'").length >= 1);
  closeJwks();
});

test("paid provider का टोकन पता न हो तो खर्च 'अज्ञात' (null) रहता है, 0 नहीं", async () => {
  await setup();
  const paid = prov("p", { tier: "paid", paidApproved: true, priceIn: 1, priceOut: 1 });
  await publish(cfgWith([paid], { primary: "p" }, { paidLock: false, freeOnly: false, globalDailyUsd: 10, globalMonthlyUsd: 100 }));
  await setKey("p");
  mock.behaviour["/p"] = { status: 200, noUsage: true };
  await chat();
  assert.equal(rows("SELECT est_cost FROM usage")[0].est_cost, null);
  const ov = await call(req("/admin/api/overview", { token: await tok("owner-uid-1") }));
  assert.equal(ov.body.cost.unknownCostRequests, 1);
  closeJwks();
});

// ---------------- LIMITS ----------------
test("सीमा: प्रति-उपयोगकर्ता प्रति मिनट", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }, { perUserPerMinute: 2 }));
  await setKey("a");
  assert.equal((await chat("u1")).status, 200);
  assert.equal((await chat("u1")).status, 200);
  const third = await chat("u1");
  assert.equal(third.status, 429);
  assert.equal(third.body.error, "rate_limited");
  assert.equal((await chat("u2")).status, 200, "दूसरे उपयोगकर्ता पर असर नहीं");
  assert.ok(rows("SELECT * FROM events WHERE kind='rate_limit'").length >= 1);
  closeJwks();
});

test("सीमा: प्रति-उपयोगकर्ता दैनिक, कुल दैनिक, feature दैनिक, provider दैनिक", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }, { perUserDailyRequests: 2 }));
  await setKey("a");
  await chat("u1"); await chat("u1");
  assert.equal((await chat("u1")).body.error, "daily_limit");

  await publish(cfgWith([prov("a")], { primary: "a" }, { globalDailyRequests: 3, perUserDailyRequests: 100 }));
  const before = rows("SELECT COUNT(*) c FROM usage WHERE attempt=1")[0].c;
  for (let i = before; i < 3; i++) await chat("g" + i);
  assert.equal((await chat("gx")).body.error, "service_busy");

  await publish(cfgWith([prov("a")], { primary: "a" }, { featureDailyRequests: { text: before + 10 + 1 } }));
  // text feature की गिनती अब तक कुल इतनी ही है; सीमा ठीक अगली पर
  const used = rows("SELECT COUNT(*) c FROM usage WHERE attempt=1")[0].c;
  await publish(cfgWith([prov("a")], { primary: "a" }, { featureDailyRequests: { text: used } }));
  assert.equal((await chat("fx")).body.error, "feature_limit");

  await publish(cfgWith([prov("a"), prov("b")], { primary: "a", backups: ["b"] }, { providerDailyRequests: { a: 1 } }));
  await setKey("b");
  const usedA = rows("SELECT COUNT(*) c FROM usage WHERE provider_id='a'")[0].c;
  assert.ok(usedA >= 1);
  const r = await chat("pa");
  assert.equal(r.body.provider.id, "b", "a की दैनिक सीमा पूरी, इसलिए b");
  assert.ok(rows("SELECT * FROM events WHERE kind='provider_cap'").length >= 1);
  closeJwks();
});

// ---------------- OTHER FEATURES ----------------
test("search: Tavily से नतीजे, खतरनाक लिंक छँटे, retrievedAt साथ", async () => {
  await setup();
  const c = cfgWith([prov("s", { feature: "search", adapter: "tavily", model: "" })], null);
  c.routes.search = { primary: "s", backups: [] };
  await publish(c);
  await setKey("s");
  const r = await call(req("/v1/ai/search", { method: "POST", token: await tok("u1"), body: { query: "JAC board result" } }));
  assert.equal(r.status, 200);
  assert.equal(r.body.result.results.length, 1);
  assert.equal(r.body.result.results[0].url, "https://example.com/x");
  assert.ok(r.body.result.retrievedAt);
  closeJwks();
});

test("translation: LibreTranslate; भाषा कोड की जाँच", async () => {
  await setup();
  const c = cfgWith([prov("t", { feature: "translation", adapter: "libretranslate", model: "" })], null);
  c.routes.translation = { primary: "t", backups: [] };
  await publish(c);
  await setKey("t");
  const ok = await call(req("/v1/ai/translation", { method: "POST", token: await tok("u1"), body: { text: "hello", source: "en", target: "hi" } }));
  assert.equal(ok.body.result.text, "नमस्ते");
  const bad = await call(req("/v1/ai/translation", { method: "POST", token: await tok("u1"), body: { text: "hello", target: "hi;drop" } }));
  assert.equal(bad.status, 400);
  closeJwks();
});

test("जो feature बना नहीं (image/voice/ocr/video) वह साफ 501 देता है, नकली नतीजा नहीं", async () => {
  await setup();
  for (const f of ["image", "voice", "ocr", "video"]) {
    const r = await call(req(`/v1/ai/${f}`, { method: "POST", token: await tok("u1"), body: {} }));
    assert.equal(r.status, 501, f);
  }
  assert.equal((await call(req("/v1/ai/nonsense", { method: "POST", token: await tok("u1"), body: {} }))).status, 404);
  closeJwks();
});

// ---------------- AVAILABILITY / DASHBOARD ----------------
test("/v1/me: features की उपलब्धता बताता है (key और enable के आधार पर)", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }));
  let me = await call(req("/v1/me", { token: await tok("u1") }));
  assert.equal(me.body.features.text, false);
  await setKey("a");
  me = await call(req("/v1/me", { token: await tok("u1") }));
  assert.equal(me.body.features.text, true);
  assert.equal(me.body.features.image, false);
  assert.equal(typeof me.body.configVersion, "number");
  closeJwks();
});

test("dashboard: अनुरोध, सफल/असफल, उपयोगकर्ता, feature/provider आँकड़े, health, quota 'Unavailable'", async () => {
  await setup();
  await publish(cfgWith([prov("a"), prov("b")], { primary: "a", backups: ["b"] }));
  await setKey("a"); await setKey("b");
  await chat("u1"); await chat("u2");
  mock.behaviour["/a"] = { status: 429, headers: { "x-ratelimit-remaining-requests": "0" } };
  await chat("u1");
  const ov = (await call(req("/admin/api/overview?range=month", { token: await tok("owner-uid-1") }))).body;
  assert.equal(ov.requests.total, 3);
  assert.equal(ov.requests.successful, 3);
  assert.equal(ov.requests.failed, 0);
  assert.equal(ov.users.monthly, 2);
  assert.equal(ov.tokens.input, 36);
  assert.equal(ov.events.fallback, 1);
  assert.equal(ov.events.rateLimit, 1);
  assert.equal(ov.byFeature[0].feature, "text");
  const pa = ov.byProvider.find((p) => p.provider_id === "a");
  assert.equal(pa.err, 1);
  const hb = ov.health.find((h) => h.id === "b");
  assert.equal(hb.hasKey, true);
  assert.equal(hb.remainingReportedByProvider, "Unavailable");
  const ha = ov.health.find((h) => h.id === "a");
  assert.ok(ha.remainingReportedByProvider.includes("x-ratelimit-remaining-requests"));
  assert.ok(ov.recent.length >= 3);
  const users = (await call(req("/admin/api/users", { token: await tok("owner-uid-1") }))).body.users;
  assert.ok(!JSON.stringify(users).includes("नमस्ते"));
  const an = (await call(req("/admin/api/analytics?days=7", { token: await tok("owner-uid-1") }))).body;
  assert.ok(an.rows.length >= 1);
  const evs = (await call(req("/admin/api/events", { token: await tok("owner-uid-1") }))).body;
  assert.ok(evs.failedCalls.length >= 1);
  closeJwks();
});

test("audit: मालिक के बदलाव दर्ज होते हैं", async () => {
  await setup();
  await publish(cfgWith([prov("a")], { primary: "a" }), "अंकेक्षण");
  await setKey("a");
  const a = (await call(req("/admin/api/audit", { token: await tok("owner-uid-1") }))).body.audit.map((x) => x.action);
  assert.ok(a.includes("config_publish") && a.includes("key_set"));
  closeJwks();
});

test("directory: 'free' सिर्फ उन्हीं को जिनका स्रोत और तारीख है; adapter-रहित provider जोड़ा नहीं जा सकता", async () => {
  const { DIRECTORY } = await import("../src/directory.js");
  for (const d of DIRECTORY.filter((x) => x.tier === "free" && x.adapter !== "device" && x.adapter !== "tesseract")) {
    assert.ok(d.source && d.verifiedAt, `${d.id}: free है पर स्रोत/तारीख नहीं`);
  }
  for (const id of ["gemini", "groq", "cerebras", "cloudflare-ai", "openrouter", "huggingface", "mistral", "tavily", "brave", "exa", "serpapi", "google-pse", "fal-image", "google-speech", "azure-speech", "google-docai", "tesseract", "gemini-video", "libretranslate", "google-translate"]) {
    assert.ok(DIRECTORY.find((d) => d.id === id), id + " directory में नहीं");
  }
  assert.equal(DIRECTORY.find((d) => d.id === "cerebras").tier, "credits_card");
  assert.equal(DIRECTORY.find((d) => d.id === "brave").tier, "credits_card");
  assert.equal(DIRECTORY.find((d) => d.id === "groq").tier, "free_unverified");
});

after(() => { mock && mock.close(); setTimeout(() => process.exit(process.exitCode || 0), 200); });
