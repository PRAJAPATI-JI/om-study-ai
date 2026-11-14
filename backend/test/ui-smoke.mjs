// admin वेबसाइट का असली ब्राउज़र टेस्ट (Chromium + Playwright)। Firebase लॉगिन नकली है (टेस्ट-कुंजी से बने टोकन); बाकी सब असली कोड।
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import worker from "../src/index.js";
import { makeEnv, startMockProviders } from "./helpers.js";
const require = createRequire(import.meta.url);
const { chromium } = require("playwright");

const { env, issuer, closeJwks } = await makeEnv();
const mock = await startMockProviders();
const pub = path.resolve("public");
const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css" };
env.ASSETS = { fetch: async (r) => {
  const p = new URL(r.url).pathname; const f = path.join(pub, p === "/" ? "index.html" : p);
  if (!f.startsWith(pub) || !fs.existsSync(f)) return new Response("nf", { status: 404 });
  return new Response(fs.readFileSync(f), { headers: { "content-type": types[path.extname(f)] || "text/plain" } });
} };
const server = http.createServer(async (rq, rs) => {
  const chunks = []; for await (const c of rq) chunks.push(c);
  const body = chunks.length ? Buffer.concat(chunks) : undefined;
  const res = await worker.fetch(new Request("http://localhost:8799" + rq.url, { method: rq.method, headers: rq.headers, body: ["GET", "HEAD"].includes(rq.method) ? undefined : body }), env);
  rs.writeHead(res.status, Object.fromEntries(res.headers)); rs.end(Buffer.from(await res.arrayBuffer()));
}).listen(8799);

const results = [];
const check = (name, ok, extra = "") => { results.push(ok); console.log((ok ? "PASS " : "FAIL ") + name + (extra ? " — " + extra : "")); };
const browser = await chromium.launch();
async function session(uid) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 800 } });
  const page = await ctx.newPage();
  const errs = []; page.on("pageerror", (e) => errs.push(e.message)); page.on("console", (m) => m.type() === "error" && !/Failed to load resource/.test(m.text()) && errs.push(m.text()));
  const tok = await issuer.sign({ sub: uid, user_id: uid });
  await page.route("https://identitytoolkit.googleapis.com/**", (r) => r.fulfill({ json: { idToken: tok, refreshToken: "r", expiresIn: "3600" } }));
  await page.goto("http://localhost:8799/");
  return { page, errs, ctx };
}
try {
  const o = await session("owner-uid-1");
  await o.page.fill("#email", "a@b.co"); await o.page.fill("#password", "x"); await o.page.click("button.primary");
  await o.page.waitForSelector("#app:not([hidden])");
  await o.page.waitForSelector(".grid .stat");
  check("owner लॉगिन + dashboard दिखा", (await o.page.textContent("#roleBadge")) === "Owner");
  check("असली बिल 'Unavailable'", (await o.page.textContent("#main")).includes("Unavailable"));
  // provider जोड़ना
  await o.page.goto("http://localhost:8799/#text"); await o.page.waitForSelector("text=Directory से जोड़ें");
  await o.page.click("div.card:has(b:text-is('Groq')) button:text-is('जोड़ें')");
  await o.page.waitForSelector("#main input[type=password]");
  // base URL को नकली सर्वर पर, model, enable
  const card = o.page.locator("#main div.card", { has: o.page.locator("input[type=password]") }).first();
  const inputs = card.locator("input[type=text], input:not([type])");
  await inputs.nth(1).fill(mock.base("/a/v1")); await inputs.nth(2).fill("m1");
  await card.locator("input[type=checkbox]").first().check();
  await card.locator("button:text-is('सेव और publish')").click();
  await o.page.waitForTimeout(500);
  await o.page.locator("#main input[type=password]").first().fill("secret-key-ABCD1234");
  await o.page.locator("button:text-is('Key सेव करें')").click();
  await o.page.waitForTimeout(800); console.log("TOAST:", await o.page.textContent("#toast"));
  await o.page.waitForSelector("code:has-text('••••')", { timeout: 5000 });
  const html = await o.page.content();
  check("key सेव → सिर्फ masked दिखी, पूरी key पेज में नहीं", html.includes("1234") && !html.includes("secret-key-ABCD1234"));
  await o.page.locator("button:text-is('Test Connection')").click();
  await o.page.waitForSelector("text=कनेक्शन ठीक है");
  check("Test Connection सफल", true);
  // primary चुनना
  await o.page.selectOption("select >> nth=0", "groq");
  await o.page.click("button:text-is('क्रम publish करें (तुरंत लागू)')");
  await o.page.waitForTimeout(500);
  const chat = await worker.fetch(new Request("http://x/v1/ai/text", { method: "POST", headers: { authorization: "Bearer " + await issuer.sign({ sub: "u1" }), "content-type": "application/json" }, body: JSON.stringify({ messages: [{ role: "user", content: "hi" }] }) }), env);
  check("UI से लगाया primary असल chat में चला", chat.status === 200, "status " + chat.status);
  // सारे 15 पन्ने बिना error खुलें
  for (const id of ["dashboard","directory","text","search","image","voice","ocr","video","translation","users","analytics","quota","errors","security","settings"]) {
    await o.page.goto("http://localhost:8799/#" + id); await o.page.waitForTimeout(300);
    const bad = await o.page.locator("#main p.err").count();
    check("पन्ना खुला: " + id, bad === 0 && (await o.page.locator("#main").innerText()).length > 20);
  }
  // रात्रि/डार्क
  await o.page.click("#themeBtn");
  check("थीम बदली", ["dark", "light"].includes(await o.page.evaluate(() => document.documentElement.dataset.theme)));
  await o.page.screenshot({ path: "/tmp/admin-mobile.png" });
  check("JS errors नहीं (owner)", o.errs.length === 0, o.errs.join(" | "));

  const u = await session("random-user-9");
  await u.page.fill("#email", "u@b.co"); await u.page.fill("#password", "x"); await u.page.click("button.primary");
  await u.page.waitForSelector("#main p.err");
  check("सामान्य उपयोगकर्ता को admin नहीं मिला", (await u.page.textContent("#main")).includes("admin"));
} catch (e) { console.log("FAIL exception", e.message); results.push(false); }
await browser.close(); server.close(); mock.close(); closeJwks();
console.log(results.every(Boolean) ? "ALL UI CHECKS PASSED" : "SOME UI CHECKS FAILED");
process.exit(results.every(Boolean) ? 0 : 1);
