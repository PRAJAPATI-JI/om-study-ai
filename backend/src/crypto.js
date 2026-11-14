import { b64encode, b64decode } from "./util.js";

/** API key को D1 में रखने से पहले AES-GCM से एन्क्रिप्ट करना। MASTER_KEY सिर्फ Worker secret में रहती है। */
async function aesKey(masterKey) {
  if (!masterKey || masterKey.length < 16) throw new Error("MASTER_KEY missing or too short");
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(masterKey));
  return crypto.subtle.importKey("raw", digest, "AES-GCM", false, ["encrypt", "decrypt"]);
}

export async function encryptSecret(masterKey, plain) {
  const key = await aesKey(masterKey);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(plain)));
  return { iv: b64encode(iv), ciphertext: b64encode(ct) };
}

export async function decryptSecret(masterKey, iv, ciphertext) {
  const key = await aesKey(masterKey);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: b64decode(iv) }, key, b64decode(ciphertext));
  return new TextDecoder().decode(plain);
}

export function maskKey(last4) {
  return "••••••••" + last4;
}
