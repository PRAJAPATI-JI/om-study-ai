// OM STUDY AI — Owner Admin Panel (vanilla JS, कोई बाहरी library नहीं)
// यह public Firebase Web API key है (secret नहीं)। अगर यह key सिर्फ Android ऐप तक सीमित है तो लॉगिन विफल होगा — README देखिए।
const FIREBASE_API_KEY = "AIzaSyDJLuC6EgS4G_B0--K6sTS3tSfm9o0ZDl8";
const FEATURE_NAMES = { text: "Text AI", search: "Web Search", image: "Image AI", voice: "Voice AI", ocr: "PDF / OCR", video: "Video AI", translation: "Translation" };
const SECTIONS = [
  ["dashboard", "Dashboard"], ["directory", "API Directory"], ["text", "Text AI"], ["search", "Web Search"], ["image", "Image AI"],
  ["voice", "Voice AI"], ["ocr", "PDF / OCR"], ["video", "Video AI"], ["translation", "Translation"], ["users", "Users"],
  ["analytics", "Usage Analytics"], ["quota", "Quota & Budget"], ["errors", "Errors & Logs"], ["security", "Security"], ["settings", "Settings"]
];
const TIER = { free: ["Verified Free Tier", "ok"], free_unverified: ["Free? (जाँचा नहीं)", "warn"], credits_card: ["Paid / कार्ड चाहिए", "bad"], paid: ["Paid", "bad"] };

const $ = (s) => document.querySelector(s);
let session = null; // {idToken, refreshToken, exp}
let state = { cfg: null, version: 0, keys: {}, status: {}, directory: [], role: "", masterKeySet: false };

// ---------- सुरक्षित DOM सहायक (innerHTML में कभी सर्वर का डेटा नहीं) ----------
function h(tag, attrs, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (k === "class") e.className = v;
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (v === true) e.setAttribute(k, "");
    else if (v !== false && v != null) e.setAttribute(k, v);
  }
  for (const kid of kids.flat()) if (kid != null && kid !== false) e.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  return e;
}
function toast(msg) { const t = $("#toast"); t.textContent = msg; t.classList.add("show"); setTimeout(() => t.classList.remove("show"), 4500); }
const fmtTs = (t) => (t ? new Date(t).toLocaleString("hi-IN") : "—");
function safeLink(url, text) {
  try { const u = new URL(url); if (u.protocol === "https:") return h("a", { href: u.href, target: "_blank", rel: "noopener noreferrer" }, text || u.hostname); } catch { /* */ }
  return h("span", { class: "muted" }, "—");
}

// ---------- लॉगिन (Firebase REST) ----------
async function fb(url, body) {
  const r = await fetch(url, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
  return { ok: r.ok, data: await r.json().catch(() => ({})) };
}
function saveSession(s) { session = s; try { sessionStorage.setItem("om_s", JSON.stringify(s)); } catch { /* */ } }
async function login(email, password) {
  const r = await fb(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_API_KEY}`, { email, password, returnSecureToken: true });
  if (!r.ok) throw new Error(r.data?.error?.message === "INVALID_LOGIN_CREDENTIALS" ? "ईमेल या पासवर्ड सही नहीं है।" : `लॉगिन नहीं हुआ (${r.data?.error?.message || "error"})`);
  saveSession({ idToken: r.data.idToken, refreshToken: r.data.refreshToken, exp: Date.now() + (parseInt(r.data.expiresIn, 10) - 60) * 1000 });
}
async function token() {
  if (!session) throw new Error("login");
  if (Date.now() > session.exp) {
    const r = await fetch(`https://securetoken.googleapis.com/v1/token?key=${FIREBASE_API_KEY}`, {
      method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" },
      body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(session.refreshToken)}`
    });
    const d = await r.json().catch(() => ({}));
    if (!r.ok) { logout(); throw new Error("login"); }
    saveSession({ idToken: d.id_token, refreshToken: d.refresh_token, exp: Date.now() + (parseInt(d.expires_in, 10) - 60) * 1000 });
  }
  return session.idToken;
}
function logout() { session = null; try { sessionStorage.removeItem("om_s"); } catch { /* */ } show(); }

async function api(path, opts = {}) {
  const r = await fetch("/admin/api" + path, {
    method: opts.method || "GET",
    headers: { authorization: "Bearer " + (await token()), "content-type": "application/json" },
    body: opts.body ? JSON.stringify(opts.body) : undefined
  });
  const d = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(d.message || d.error || `error ${r.status}`);
  return d;
}
const guard = (fn) => async (...a) => { try { await fn(...a); } catch (e) { toast(e.message || "गड़बड़ हुई"); } };

async function loadConfig() {
  const d = await api("/config");
  state = { cfg: d.config, version: d.version, keys: d.keys, status: d.status, directory: d.directory, role: d.role, masterKeySet: d.masterKeySet };
  $("#roleBadge").textContent = d.role === "owner" ? "Owner" : "Viewer (सिर्फ पढ़ना)";
}
async function publish(note) {
  const d = await api("/config", { method: "PUT", body: { config: state.cfg, note } });
  toast(`प्रकाशित: version ${d.version}`);
  await loadConfig();
}
const isOwner = () => state.role === "owner";
const isPaidLikeTier = (t) => t === "paid" || t === "credits_card";

// ---------- पन्ने ----------
function statCard(label, value, sub) { return h("div", { class: "card stat" }, h("span", { class: "muted" }, label), h("b", {}, value), sub ? h("span", { class: "muted" }, sub) : null); }
function table(cols, rows) {
  return h("div", { class: "scroll" }, h("table", {}, h("thead", {}, h("tr", {}, cols.map((c) => h("th", {}, c)))),
    h("tbody", {}, rows.length ? rows.map((r) => h("tr", {}, r.map((c) => h("td", {}, c)))) : [h("tr", {}, h("td", { colspan: cols.length, class: "muted" }, "अभी कोई डेटा नहीं"))])));
}

async function pageDashboard(root) {
  const range = root.dataset.range || "day";
  const o = await api("/overview?range=" + range);
  root.append(h("div", { class: "row" }, ["day", "week", "month"].map((r) =>
    h("button", { class: r === range ? "primary" : "", onclick: () => { root.dataset.range = r; render("dashboard"); } }, { day: "आज", week: "7 दिन", month: "30 दिन" }[r]))));
  root.append(h("div", { class: "grid" },
    statCard("कुल उपयोगकर्ता (नया ऐप)", o.users.registeredInApp), statCard("सक्रिय (चुनी अवधि)", o.users.activeInRange),
    statCard("आज / 7 दिन / 30 दिन", `${o.users.daily} / ${o.users.weekly} / ${o.users.monthly}`),
    statCard("कुल requests", o.requests.total), statCard("सफल", o.requests.successful), statCard("असफल", o.requests.failed),
    statCard("Input tokens", o.tokens.input, o.tokens.unreportedRequests ? `${o.tokens.unreportedRequests} requests में provider ने नहीं बताया` : ""),
    statCard("Output tokens", o.tokens.output),
    statCard("अनुमानित खर्च", "$" + o.cost.estimatedUsd.toFixed(4), o.cost.unknownCostRequests ? `${o.cost.unknownCostRequests} requests का खर्च अज्ञात` : ""),
    statCard("असली बिल (provider से)", o.cost.actualBilledUsd, "provider का पन्ना देखें"),
    statCard("Fallback", o.events.fallback), statCard("API errors", o.events.error), statCard("Rate-limit", o.events.rateLimit), statCard("Budget से रुके", o.events.budgetBlock)));
  root.append(h("p", { class: "muted" }, o.users.note, " ", o.cost.note, " Config version: ", o.configVersion));
  root.append(h("div", { class: "card" }, h("h3", {}, "Feature-वार"),
    table(["Feature", "Calls", "OK", "Err", "Tokens in/out", "Cost $"], o.byFeature.map((f) => [FEATURE_NAMES[f.feature] || f.feature, f.calls, f.ok, f.err, `${f.tin}/${f.tout}`, f.cost.toFixed(4)]))));
  root.append(h("div", { class: "card" }, h("h3", {}, "Provider-वार"),
    table(["Provider", "Calls", "OK", "Err", "Tokens in/out", "Avg ms"], o.byProvider.map((f) => [f.provider_id, f.calls, f.ok, f.err, `${f.tin}/${f.tout}`, Math.round(f.avg_latency || 0)]))));
  root.append(h("div", { class: "card" }, h("h3", {}, "API Health और बचा हुआ quota"),
    table(["Provider", "स्थिति", "आज उपयोग", "Provider बताया quota", "आखिरी टेस्ट"], o.health.map((p) => [
      `${p.name} (${FEATURE_NAMES[p.feature]})`,
      h("span", { class: p.enabled && p.hasKey ? "ok" : "warn" }, !p.hasKey ? "key नहीं" : p.enabled ? "चालू" : "बंद"),
      p.dailyCap ? `${p.usedToday}/${p.dailyCap}` : String(p.usedToday), p.remainingReportedByProvider,
      p.lastTestAt ? `${p.lastTestOk ? "✔" : "✘"} ${fmtTs(p.lastTestAt)} ${p.lastTestMsg || ""}` : "—"]))));
  root.append(h("div", { class: "card" }, h("h3", {}, "हाल की गतिविधि"),
    table(["समय", "Feature", "Provider", "स्थिति", "ms", "Fallback"], o.recent.map((r) => [fmtTs(r.ts), r.feature, r.provider_id, r.status === "ok" ? "ok" : r.error_code || "error", r.latency_ms ?? "—", r.fallback ? "हाँ" : ""]))));
}

function tierTag(t) { const [l, c] = TIER[t] || [t, ""]; return h("span", { class: "tag " + c }, l); }

function directoryCard(d, onAdd) {
  const added = !!state.cfg.providers[d.id];
  return h("div", { class: "card" },
    h("b", {}, d.name), " ", tierTag(d.tier), d.implemented ? h("span", { class: "tag ok" }, "ऐप में समर्थित") : h("span", { class: "tag bad" }, "अभी बना नहीं (adapter नहीं)"),
    h("p", { class: "muted" }, d.freeInfo), h("p", { class: "muted" }, "Rate: ", d.rateInfo, " · दाम: ", d.costInfo),
    h("p", { class: "muted" }, "जाँच की तारीख: ", d.verifiedAt || "—", " · स्रोत: ", d.source ? safeLink(d.source, "लिंक") : "कोई नहीं", " · ", safeLink(d.website, "वेबसाइट"), " · ", safeLink(d.docs, "docs")),
    onAdd && isOwner() ? h("button", { disabled: added || !d.implemented, onclick: guard(() => onAdd(d)) }, added ? "जुड़ा हुआ" : d.implemented ? "जोड़ें" : "जोड़ नहीं सकते") : null);
}

async function addFromDirectory(d) {
  state.cfg.providers[d.id] = {
    id: d.id, name: d.name, feature: d.feature, adapter: d.adapter, baseUrl: d.baseUrl || "", model: d.model || "",
    enabled: false, tier: d.tier, paidApproved: false
  };
  await publish(`जोड़ा: ${d.id}`);
}

function pageDirectory(root) {
  root.append(h("p", { class: "muted" }, "'Verified Free Tier' सिर्फ उन्हीं पर है जिनका आधिकारिक स्रोत और तारीख दर्ज है। बाकी सब 'जाँचा नहीं' या Paid है। नाम/model/URL बदल सकते हैं — असली पुष्टि 'Test Connection' से होती है।"));
  for (const f of Object.keys(FEATURE_NAMES)) {
    root.append(h("h2", {}, FEATURE_NAMES[f]));
    state.directory.filter((d) => d.feature === f).forEach((d) => root.append(directoryCard(d, async (x) => { await addFromDirectory(x); render(currentPage); })));
  }
}

function providerForm(p) {
  const f = {};
  const inp = (k, label, type = "text") => { f[k] = h("input", { type, value: p[k] ?? "" }); return h("label", {}, label, f[k]); };
  const chk = (k, label) => { f[k] = h("input", { type: "checkbox" }); f[k].checked = !!p[k]; return h("label", {}, f[k], label); };
  const key = state.keys[p.id];
  const keyIn = h("input", { type: "password", autocomplete: "off", placeholder: key ? "नई key से बदलें" : "API key यहाँ चिपकाइए" });
  const out = h("p", { class: "muted" });
  const st = state.status[p.id];
  return h("div", { class: "card" },
    h("b", {}, p.name), " ", tierTag(p.tier), h("span", { class: "tag" }, p.id),
    inp("name", "नाम"), inp("baseUrl", "Base URL (https://…)"), inp("model", "Model / endpoint"),
    chk("enabled", "चालू (enable)"),
    isPaidLikeTier(p.tier) ? chk("paidApproved", "मैं इस paid provider का खर्च मंज़ूर करता/करती हूँ") : null,
    h("div", { class: "grid" }, inp("priceIn", "दाम $/1M input token", "number"), inp("priceOut", "दाम $/1M output token", "number"), inp("priceRequest", "दाम $/request", "number")),
    h("div", { class: "row" }, h("button", { class: "primary", disabled: !isOwner(), onclick: guard(async () => {
      p.name = f.name.value.trim(); p.baseUrl = f.baseUrl.value.trim(); p.model = f.model.value.trim(); p.enabled = f.enabled.checked;
      if (f.paidApproved) p.paidApproved = f.paidApproved.checked;
      for (const k of ["priceIn", "priceOut", "priceRequest"]) { const v = f[k].value.trim(); if (v === "") delete p[k]; else p[k] = Number(v); }
      await publish(`बदला: ${p.id}`); render(currentPage);
    }) }, "सेव और publish")),
    h("hr"), h("p", {}, "API key: ", key ? h("code", {}, key.masked) : h("span", { class: "warn" }, "सेव नहीं है"), key ? h("span", { class: "muted" }, " · बदला: " + fmtTs(key.updatedAt)) : null),
    !state.masterKeySet ? h("p", { class: "err" }, "सर्वर में MASTER_KEY नहीं है, इसलिए key सेव नहीं होगी।") : null,
    keyIn,
    h("div", { class: "row" },
      h("button", { disabled: !isOwner(), onclick: guard(async () => { await api(`/provider/${p.id}/key`, { method: "PUT", body: { key: keyIn.value } }); keyIn.value = ""; toast("key सुरक्षित सेव हुई"); await loadConfig(); render(currentPage); }) }, "Key सेव करें"),
      h("button", { class: "danger", disabled: !isOwner() || !key, onclick: guard(async () => { if (!confirm("key हटाएँ?")) return; await api(`/provider/${p.id}/key`, { method: "DELETE" }); await loadConfig(); render(currentPage); }) }, "Key हटाएँ"),
      h("button", { disabled: !isOwner() || !key, onclick: guard(async () => {
        let confirmPaid = false;
        if (isPaidLikeTier(p.tier)) { confirmPaid = confirm("यह paid जैसा provider है। टेस्ट से भी थोड़ा खर्च हो सकता है। जारी रखें?"); if (!confirmPaid) return; }
        out.textContent = "टेस्ट चल रहा है…";
        const r = await api(`/provider/${p.id}/test`, { method: "POST", body: { confirmPaid } });
        out.textContent = (r.ok ? "✔ " : "✘ ") + r.message + ` (${r.latencyMs} ms)`;
        await loadConfig();
      }) }, "Test Connection")),
    out, st?.last_test_at ? h("p", { class: "muted" }, `आखिरी टेस्ट: ${fmtTs(st.last_test_at)} — ${st.last_test_ok ? "ठीक" : "असफल"} ${st.last_test_msg || ""}`) : null,
    h("button", { class: "danger", disabled: !isOwner(), onclick: guard(async () => {
      if (!confirm(`${p.name} हटाएँ? (key भी हटेगी)`)) return;
      delete state.cfg.providers[p.id];
      const r = state.cfg.routes[p.feature];
      if (r.primary === p.id) r.primary = null;
      r.backups = r.backups.filter((x) => x !== p.id);
      await publish(`हटाया: ${p.id}`);
      if (state.keys[p.id]) await api(`/provider/${p.id}/key`, { method: "DELETE" }).catch(() => {});
      await loadConfig(); render(currentPage);
    }) }, "यह provider हटाएँ"));
}

function pageFeature(root, feature) {
  const r = state.cfg.routes[feature];
  const mine = Object.values(state.cfg.providers).filter((p) => p.feature === feature);
  const opts = (sel) => [h("option", { value: "" }, "— कोई नहीं —"), ...mine.map((p) => { const o = h("option", { value: p.id }, p.name); if (sel === p.id) o.selected = true; return o; })];
  const sP = h("select", {}, opts(r.primary)), s1 = h("select", {}, opts(r.backups[0])), s2 = h("select", {}, opts(r.backups[1]));
  root.append(h("div", { class: "card" }, h("h3", {}, FEATURE_NAMES[feature] + " — क्रम"),
    h("p", { class: "muted" }, "Primary फेल (quota / rate-limit / बंद / server error / timeout) होने पर Backup 1, फिर Backup 2 चलेगा। हर provider सिर्फ एक बार आज़माया जाता है। Paid provider तभी चलता है जब आपने Quota & Budget में उसकी अनुमति दी हो।"),
    h("label", {}, "Primary", sP), h("label", {}, "Backup 1", s1), h("label", {}, "Backup 2", s2),
    h("button", { class: "primary", disabled: !isOwner(), onclick: guard(async () => {
      state.cfg.routes[feature] = { primary: sP.value || null, backups: [s1.value, s2.value].filter(Boolean) };
      await publish(`${feature} क्रम बदला`);
    }) }, "क्रम publish करें (तुरंत लागू)")));
  if (!mine.length) root.append(h("p", { class: "muted" }, "इस feature में अभी कोई provider नहीं जुड़ा। नीचे से जोड़िए।"));
  mine.forEach((p) => root.append(providerForm(p)));
  root.append(h("h3", {}, "Directory से जोड़ें"));
  state.directory.filter((d) => d.feature === feature).forEach((d) => root.append(directoryCard(d, async (x) => { await addFromDirectory(x); render(currentPage); })));
  if (feature === "text") root.append(h("p", { class: "muted" }, "Chat, समझाना, नोट्स, सवाल-जवाब, टाइमटेबल, क्विज़ — सब अभी इसी एक Text क्रम से चलते हैं।"));
  if (["image", "voice", "ocr", "video"].includes(feature)) root.append(h("p", { class: "warn" }, "ध्यान: इस feature का सर्वर-कोड अभी बना नहीं है। यहाँ सिर्फ जानकारी देख सकते हैं; ऐप को अभी 'उपलब्ध नहीं' मिलेगा।"));
}

async function pageUsers(root) {
  const d = await api("/users");
  root.append(h("p", { class: "muted" }, d.note), table(["UID", "पहली बार", "आखिरी बार", "30 दिन requests"], d.users.map((u) => [u.uid, fmtTs(u.first_seen), fmtTs(u.last_seen), u.requests30d])));
}
async function pageAnalytics(root) {
  const d = await api("/analytics?days=14");
  root.append(h("p", { class: "muted" }, "पिछले 14 दिन (दिन = आपके समय क्षेत्र से)"));
  const byDay = {};
  d.rows.forEach((r) => { const k = new Date(r.d * 86400000).toISOString().slice(0, 10); (byDay[k] ||= { calls: 0, ok: 0, tin: 0, tout: 0, cost: 0 }); const x = byDay[k]; x.calls += r.calls; x.ok += r.ok; x.tin += r.tin; x.tout += r.tout; x.cost += r.cost; });
  const max = Math.max(1, ...Object.values(byDay).map((x) => x.calls));
  root.append(h("div", { class: "card" }, Object.entries(byDay).map(([k, x]) => {
    // CSP के कारण style attribute नहीं; CSSOM से चौड़ाई सेट करते हैं
    const bar = h("div", { class: "bar" }); bar.style.width = Math.max(2, (x.calls / max) * 60) + "%";
    const day = h("span", { class: "muted day" }, k);
    return h("div", { class: "row" }, day, bar, h("span", {}, `${x.calls}`));
  })));
  root.append(table(["दिन", "Calls", "OK", "Tokens in/out", "Cost $"], Object.entries(byDay).reverse().map(([k, x]) => [k, x.calls, x.ok, `${x.tin}/${x.tout}`, x.cost.toFixed(4)])));
}
function pageQuota(root) {
  const b = state.cfg.budgets;
  const f = {};
  const n = (k, label) => { f[k] = h("input", { type: "number", min: "0", step: "any", value: b[k] }); return h("label", {}, label, f[k]); };
  const c = (k, label) => { f[k] = h("input", { type: "checkbox" }); f[k].checked = !!b[k]; return h("label", {}, f[k], label); };
  const map = (key, label) => { const ta = h("textarea", { rows: "3" }); ta.value = JSON.stringify(b[key] || {}); f[key] = ta; return h("label", {}, label + " (JSON, जैसे {\"gemini\":200}; 0 = सीमा नहीं)", ta); };
  root.append(h("div", { class: "card" }, h("h3", {}, "सुरक्षा ताले"),
    c("freeOnly", "सिर्फ मुफ्त providers (Free-only mode)"), c("paidLock", "Paid lock (चालू रहते कोई paid provider नहीं चलेगा)"),
    h("p", { class: "muted" }, "Paid provider चलने के लिए चारों चाहिए: Free-only बंद, Paid lock बंद, provider पर 'खर्च मंज़ूर', और नीचे दैनिक व मासिक USD सीमा 0 से ज़्यादा। कभी अपने-आप paid पर नहीं जाता.")),
    h("div", { class: "card" }, h("h3", {}, "सीमाएँ"),
      n("globalDailyRequests", "कुल requests प्रति दिन"), n("perUserDailyRequests", "प्रति उपयोगकर्ता प्रति दिन"), n("perUserPerMinute", "प्रति उपयोगकर्ता प्रति मिनट"),
      n("globalDailyUsd", "कुल खर्च सीमा प्रति दिन ($)"), n("globalMonthlyUsd", "कुल खर्च सीमा प्रति माह ($)"), n("tzOffsetMin", "समय क्षेत्र (UTC से मिनट; भारत = 330)"),
      map("featureDailyRequests", "Feature-वार दैनिक requests"), map("featureDailyUsd", "Feature-वार दैनिक $"), map("providerDailyRequests", "Provider-वार दैनिक requests")),
    h("button", { class: "primary", disabled: !isOwner(), onclick: guard(async () => {
      for (const k of ["freeOnly", "paidLock"]) b[k] = f[k].checked;
      for (const k of ["globalDailyRequests", "perUserDailyRequests", "perUserPerMinute", "globalDailyUsd", "globalMonthlyUsd", "tzOffsetMin"]) b[k] = Number(f[k].value);
      for (const k of ["featureDailyRequests", "featureDailyUsd", "providerDailyRequests"]) { try { b[k] = JSON.parse(f[k].value || "{}"); } catch { throw new Error(k + " का JSON सही नहीं है"); } }
      await publish("budget बदला");
    }) }, "सेव और publish"));
}
async function pageErrors(root) {
  const d = await api("/events?limit=100");
  root.append(h("h3", {}, "घटनाएँ (fallback, rate-limit, budget, errors)"), table(["समय", "प्रकार", "Feature", "Provider", "विवरण"], d.events.map((e) => [fmtTs(e.ts), e.kind, e.feature || "", e.provider_id || "", e.detail || ""])));
  root.append(h("h3", {}, "असफल calls"), table(["समय", "Feature", "Provider", "Error", "ms"], d.failedCalls.map((e) => [fmtTs(e.ts), e.feature, e.provider_id, e.error_code || "", e.latency_ms ?? ""])));
}
async function pageSecurity(root) {
  const s = await api("/security");
  root.append(h("div", { class: "card" }, h("h3", {}, "सुरक्षा स्थिति"),
    h("p", {}, "OWNER_UID सेट: ", s.ownerUidSet ? "✔" : "✘"), h("p", {}, "MASTER_KEY सेट: ", s.masterKeySet ? "✔" : "✘ (key सेव नहीं होंगी)"),
    h("p", {}, "Firebase project: ", s.firebaseProject), h("p", {}, "सेव की हुई keys: ", s.keysStored), h("p", {}, "Free-only: ", String(s.freeOnly), " · Paid lock: ", String(s.paidLock)),
    h("p", { class: "muted" }, "Owner-only जाँच सर्वर पर Firebase UID से होती है; यह पन्ना सिर्फ दिखाता है.")));
  if (isOwner()) {
    const a = await api("/admins");
    const uidIn = h("input", { placeholder: "नए viewer का Firebase UID" });
    root.append(h("div", { class: "card" }, h("h3", {}, "Viewer admins (सिर्फ पढ़ सकते हैं)"),
      table(["UID", "भूमिका", ""], a.admins.map((x) => [x.uid, x.role, h("button", { class: "danger", onclick: guard(async () => { await api("/admins/" + encodeURIComponent(x.uid), { method: "DELETE" }); render("security"); }) }, "हटाएँ")])),
      uidIn, h("button", { onclick: guard(async () => { await api("/admins", { method: "POST", body: { uid: uidIn.value.trim() } }); render("security"); }) }, "Viewer जोड़ें")));
  }
  const au = await api("/audit");
  root.append(h("div", { class: "card" }, h("h3", {}, "बदलावों का रिकॉर्ड (Audit)"), table(["समय", "UID", "काम", "विवरण"], au.audit.map((x) => [fmtTs(x.ts), x.uid, x.action, x.detail]))));
}
async function pageSettings(root) {
  const v = await api("/versions");
  root.append(h("div", { class: "card" }, h("h3", {}, "Config versions (rollback)"), h("p", { class: "muted" }, `मौजूदा: v${state.version}. Rollback पुराना config नए version की तरह लागू करता है; चल रहे अनुरोध नहीं छेड़ता।`),
    table(["Version", "नोट", "किसने", "कब", ""], v.versions.map((x) => [x.version, x.note || "", x.created_by, fmtTs(x.created_at),
      isOwner() && x.version !== state.version ? h("button", { onclick: guard(async () => { if (!confirm(`v${x.version} पर लौटें?`)) return; await api("/rollback", { method: "POST", body: { version: x.version } }); await loadConfig(); render("settings"); }) }, "लौटें") : ""]))));
  root.append(h("div", { class: "card" }, h("h3", {}, "थीम"), h("button", { onclick: toggleTheme }, "लाइट / डार्क बदलें")));
}

// ---------- ढाँचा ----------
let currentPage = "dashboard";
async function render(page) {
  currentPage = page;
  const root = $("#main");
  const keepRange = root.dataset.range;
  root.replaceChildren();
  if (keepRange) root.dataset.range = keepRange;
  $("#title").textContent = (SECTIONS.find((s) => s[0] === page) || [0, ""])[1];
  $("#nav").replaceChildren(...SECTIONS.map(([id, label]) => h("a", { href: "#" + id, class: id === page ? "on" : "" }, label)));
  $("#nav").classList.remove("open");
  try {
    await loadConfig();
    if (page === "dashboard") await pageDashboard(root);
    else if (page === "directory") pageDirectory(root);
    else if (FEATURE_NAMES[page]) pageFeature(root, page);
    else if (page === "users") await pageUsers(root);
    else if (page === "analytics") await pageAnalytics(root);
    else if (page === "quota") pageQuota(root);
    else if (page === "errors") await pageErrors(root);
    else if (page === "security") await pageSecurity(root);
    else if (page === "settings") await pageSettings(root);
  } catch (e) {
    if (e.message === "login") return;
    root.replaceChildren(h("div", { class: "card" }, h("p", { class: "err" }, e.message), h("p", { class: "muted" }, "अगर 'admin अधिकार नहीं' दिखे तो Cloudflare में OWNER_UID आपके UID पर सेट होना चाहिए (ऐप → सेटिंग में UID दिखेगा)।")));
  }
}
function toggleTheme() {
  const t = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = t; try { localStorage.setItem("om_theme", t); } catch { /* */ }
}
function show() {
  $("#login").hidden = !!session; $("#app").hidden = !session;
  if (session) render(location.hash.slice(1) || "dashboard");
}
try {
  const t = localStorage.getItem("om_theme");
  document.documentElement.dataset.theme = t || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  const s = sessionStorage.getItem("om_s"); if (s) session = JSON.parse(s);
} catch { /* */ }
$("#themeBtn").onclick = toggleTheme;
$("#menuBtn").onclick = () => $("#nav").classList.toggle("open");
$("#logoutBtn").onclick = logout;
window.addEventListener("hashchange", () => session && render(location.hash.slice(1) || "dashboard"));
$("#loginForm").addEventListener("submit", async (e) => {
  e.preventDefault(); $("#loginMsg").textContent = "";
  try { await login($("#email").value.trim(), $("#password").value); $("#password").value = ""; show(); } catch (err) { $("#loginMsg").textContent = err.message; }
});
show();
