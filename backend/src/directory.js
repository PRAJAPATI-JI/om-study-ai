/**
 * Provider Directory (संभावित provider की सूची)।
 * tier:
 *   free            = मैंने आधिकारिक दस्तावेज़ से मुफ्त स्तर जाँचा (verifiedAt और source देखें)
 *   free_unverified = मुफ्त स्तर हो सकता है, पर मैंने आधिकारिक पन्ने से पक्का नहीं किया
 *   credits_card    = मुफ्त क्रेडिट/ट्रायल मिलता है पर कार्ड/भुगतान-तरीका चाहिए → "Paid" की तरह संभाला जाता है
 *   paid            = भुगतान वाला
 * implemented=false का मतलब: इसका adapter अभी बना नहीं है। सेटिंग सेव हो सकती है पर अनुरोध 501 देगा।
 * baseUrl / model नाम मेरी जानकारी से हैं और बदल सकते हैं। असली पुष्टि "Test Connection" से ही होती है।
 */
const V = "2026-11-13";

export const DIRECTORY = [
  // ---------------- TEXT ----------------
  {
    id: "gemini", feature: "text", name: "Google Gemini", adapter: "openai",
    baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai", model: "gemini-2.5-flash",
    website: "https://ai.google.dev", docs: "https://ai.google.dev/gemini-api/docs/rate-limits",
    tier: "free_unverified", verifiedAt: V, source: "https://ai.google.dev/gemini-api/docs/rate-limits",
    freeInfo: "आधिकारिक पन्ने पर Free स्तर का ज़िक्र है, पर हर मॉडल की सटीक सीमा सिर्फ Google AI Studio में दिखती है। \"सीमाएँ गारंटी नहीं हैं\" भी लिखा है।",
    rateInfo: "AI Studio में देखें", costInfo: "Free स्तर के बाहर भुगतान", implemented: true
  },
  {
    id: "groq", feature: "text", name: "Groq", adapter: "openai",
    baseUrl: "https://api.groq.com/openai/v1", model: "openai/gpt-oss-20b",
    website: "https://groq.com", docs: "https://console.groq.com/docs/rate-limits",
    tier: "free_unverified", verifiedAt: V, source: "https://console.groq.com/docs/rate-limits",
    freeInfo: "आधिकारिक पन्ना Developer plan की सीमाएँ दिखाता है (जैसे gpt-oss-20b: 30 RPM, 1K RPD, 8K TPM, 200K TPD)। Free plan की सीमाएँ उस पन्ने पर नहीं मिलीं।",
    rateInfo: "अपने खाते के Limits पन्ने पर देखें", costInfo: "ऊपर की सीमा से आगे भुगतान", implemented: true
  },
  {
    id: "cerebras", feature: "text", name: "Cerebras", adapter: "openai",
    baseUrl: "https://api.cerebras.ai/v1", model: "gpt-oss-120b",
    website: "https://www.cerebras.ai", docs: "https://inference-docs.cerebras.ai/support/rate-limits",
    tier: "credits_card", verifiedAt: V, source: "https://inference-docs.cerebras.ai/support/rate-limits",
    freeInfo: "Free Trial: verified भुगतान-तरीका जोड़ने के बाद $5 क्रेडिट, 30 दिन में खत्म। gpt-oss-120b: 5 RPM, 1M टोकन/दिन।",
    rateInfo: "5 RPM, 1M TPD (Free Trial, gpt-oss-120b)", costInfo: "क्रेडिट खत्म होने पर भुगतान", implemented: true
  },
  {
    id: "cloudflare-ai", feature: "text", name: "Cloudflare Workers AI", adapter: "openai",
    baseUrl: "https://api.cloudflare.com/client/v4/accounts/ACCOUNT_ID/ai/v1", model: "@cf/meta/llama-3.1-8b-instruct",
    website: "https://developers.cloudflare.com/workers-ai/", docs: "https://developers.cloudflare.com/workers-ai/platform/pricing/",
    tier: "free_unverified", verifiedAt: V, source: "",
    freeInfo: "मैं आधिकारिक पन्ना नहीं खोल पाया। तीसरे पक्ष के स्रोत रोज़ का मुफ्त हिस्सा बताते हैं। पक्का नहीं।",
    rateInfo: "Unavailable", costInfo: "Unavailable", implemented: true,
    notes: "baseUrl में ACCOUNT_ID की जगह अपनी Cloudflare Account ID डालिए। API token भी चाहिए।"
  },
  {
    id: "openrouter", feature: "text", name: "OpenRouter", adapter: "openai",
    baseUrl: "https://openrouter.ai/api/v1", model: "",
    website: "https://openrouter.ai", docs: "https://openrouter.ai/docs/limits",
    tier: "free", verifiedAt: V, source: "https://openrouter.ai/docs/limits",
    freeInfo: "जिन मॉडल के नाम के अंत में :free है, वे मुफ्त हैं: 20 अनुरोध/मिनट। अब तक 10 से कम क्रेडिट खरीदे हों तो 50 अनुरोध/दिन, 10 या ज़्यादा हों तो 1000/दिन।",
    rateInfo: "20 RPM; 50/दिन (क्रेडिट <10)", costInfo: "सिर्फ :free मॉडल चुनें, वरना भुगतान",
    implemented: true, notes: "model में कोई ':free' वाला मॉडल खुद चुनिए (नाम बदलते रहते हैं)।"
  },
  {
    id: "huggingface", feature: "text", name: "Hugging Face", adapter: "openai",
    baseUrl: "https://router.huggingface.co/v1", model: "",
    website: "https://huggingface.co", docs: "https://huggingface.co/docs/inference-providers",
    tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: true
  },
  {
    id: "mistral", feature: "text", name: "Mistral", adapter: "openai",
    baseUrl: "https://api.mistral.ai/v1", model: "",
    website: "https://mistral.ai", docs: "https://docs.mistral.ai",
    tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: true
  },
  {
    id: "custom-openai", feature: "text", name: "अपना OpenAI-जैसा API", adapter: "openai",
    baseUrl: "", model: "", website: "", docs: "",
    tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "आप खुद जाँचिए।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: true
  },

  // ---------------- SEARCH ----------------
  {
    id: "tavily", feature: "search", name: "Tavily", adapter: "tavily",
    baseUrl: "https://api.tavily.com", model: "",
    website: "https://tavily.com", docs: "https://docs.tavily.com/documentation/api-credits",
    tier: "free", verifiedAt: V, source: "https://docs.tavily.com/documentation/api-credits.md",
    freeInfo: "हर महीने 1,000 मुफ्त API credits। basic खोज = 1 credit, advanced = 2 credits।",
    rateInfo: "1,000 credits/माह", costInfo: "खत्म होने पर भुगतान", implemented: true
  },
  {
    id: "brave", feature: "search", name: "Brave Search API", adapter: "brave",
    baseUrl: "https://api.search.brave.com/res/v1", model: "",
    website: "https://brave.com/search/api", docs: "https://api-docs.search.brave.software/guides/quickstart",
    tier: "credits_card", verifiedAt: V, source: "https://brave.com/search/api",
    freeInfo: "अलग Free tier नहीं। हर महीने $5 के मुफ्त क्रेडिट मिलते हैं। FAQ के अनुसार दुरुपयोग रोकने के लिए क्रेडिट कार्ड माँगा जाता है।",
    rateInfo: "Search: 50 क्वेरी/सेकंड तक", costInfo: "$5 प्रति 1,000 अनुरोध", implemented: true
  },
  { id: "exa", feature: "search", name: "Exa", adapter: "exa", baseUrl: "", model: "", website: "https://exa.ai", docs: "https://docs.exa.ai", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "serpapi", feature: "search", name: "SerpAPI", adapter: "serpapi", baseUrl: "", model: "", website: "https://serpapi.com", docs: "https://serpapi.com/search-api", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "google-pse", feature: "search", name: "Google Programmable Search", adapter: "google-pse", baseUrl: "", model: "", website: "https://programmablesearchengine.google.com", docs: "https://developers.google.com/custom-search/v1/overview", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },

  // ---------------- IMAGE ----------------
  { id: "hf-image", feature: "image", name: "Hugging Face (image)", adapter: "huggingface-image", baseUrl: "", model: "", website: "https://huggingface.co", docs: "https://huggingface.co/docs/inference-providers", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "cf-image", feature: "image", name: "Cloudflare Workers AI (image)", adapter: "cloudflare-image", baseUrl: "", model: "", website: "https://developers.cloudflare.com/workers-ai/", docs: "https://developers.cloudflare.com/workers-ai/", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "gemini-image", feature: "image", name: "Google Gemini (image)", adapter: "gemini-image", baseUrl: "", model: "", website: "https://ai.google.dev", docs: "https://ai.google.dev/gemini-api/docs", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "fal-image", feature: "image", name: "fal.ai (image)", adapter: "fal", baseUrl: "", model: "", website: "https://fal.ai", docs: "https://docs.fal.ai", tier: "paid", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं। आम तौर पर भुगतान वाला माना जाए।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },

  // ---------------- VOICE ----------------
  { id: "google-speech", feature: "voice", name: "Google Cloud Speech", adapter: "google-speech", baseUrl: "", model: "", website: "https://cloud.google.com/speech-to-text", docs: "https://cloud.google.com/speech-to-text/docs", tier: "paid", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं। Google Cloud में बिलिंग खाता चाहिए।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "azure-speech", feature: "voice", name: "Azure Speech", adapter: "azure-speech", baseUrl: "", model: "", website: "https://azure.microsoft.com/products/ai-services/ai-speech", docs: "https://learn.microsoft.com/azure/ai-services/speech-service/", tier: "paid", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "openai-speech", feature: "voice", name: "OpenAI-जैसा Speech API", adapter: "openai-speech", baseUrl: "", model: "", website: "", docs: "", tier: "paid", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "android-tts", feature: "voice", name: "Android का अपना TTS/STT (फोन में)", adapter: "device", baseUrl: "", model: "", website: "", docs: "", tier: "free", verifiedAt: V, source: "", freeInfo: "फोन में बना हुआ है, सर्वर की ज़रूरत नहीं। ऐप में बाद में जुड़ेगा। हिंदी की गुणवत्ता फोन पर निर्भर।", rateInfo: "लागू नहीं", costInfo: "₹0", implemented: false },

  // ---------------- OCR / PDF ----------------
  { id: "google-docai", feature: "ocr", name: "Google Document AI", adapter: "google-docai", baseUrl: "", model: "", website: "https://cloud.google.com/document-ai", docs: "https://cloud.google.com/document-ai/docs", tier: "paid", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "tesseract", feature: "ocr", name: "Tesseract OCR (खुद का सर्वर)", adapter: "tesseract", baseUrl: "", model: "", website: "https://github.com/tesseract-ocr/tesseract", docs: "https://tesseract-ocr.github.io/", tier: "free", verifiedAt: V, source: "", freeInfo: "खुला सॉफ्टवेयर है पर Cloudflare Worker में सीधे नहीं चलता; अलग सर्वर चाहिए।", rateInfo: "लागू नहीं", costInfo: "सर्वर की लागत", implemented: false },
  { id: "cf-docs", feature: "ocr", name: "Cloudflare-संगत document processing", adapter: "cloudflare-docs", baseUrl: "", model: "", website: "https://developers.cloudflare.com", docs: "https://developers.cloudflare.com", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },

  // ---------------- VIDEO ----------------
  { id: "gemini-video", feature: "video", name: "Gemini (video समझना)", adapter: "gemini-video", baseUrl: "", model: "", website: "https://ai.google.dev", docs: "https://ai.google.dev/gemini-api/docs", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "hf-video", feature: "video", name: "Hugging Face (video)", adapter: "huggingface-video", baseUrl: "", model: "", website: "https://huggingface.co", docs: "https://huggingface.co/docs/inference-providers", tier: "free_unverified", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },
  { id: "fal-video", feature: "video", name: "fal.ai (video)", adapter: "fal", baseUrl: "", model: "", website: "https://fal.ai", docs: "https://docs.fal.ai", tier: "paid", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं। वीडियो बनाना आम तौर पर महँगा होता है।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false },

  // ---------------- TRANSLATION ----------------
  {
    id: "libretranslate", feature: "translation", name: "LibreTranslate", adapter: "libretranslate",
    baseUrl: "https://libretranslate.com", model: "",
    website: "https://libretranslate.com", docs: "https://docs.libretranslate.com",
    tier: "free_unverified", verifiedAt: V, source: "",
    freeInfo: "खुला सॉफ्टवेयर है। सार्वजनिक सर्वर पर API key और सीमाएँ हो सकती हैं; मैंने जाँचा नहीं। अपना सर्वर भी चला सकते हैं।",
    rateInfo: "Unavailable", costInfo: "Unavailable", implemented: true
  },
  { id: "google-translate", feature: "translation", name: "Google Cloud Translation", adapter: "google-translate", baseUrl: "", model: "", website: "https://cloud.google.com/translate", docs: "https://cloud.google.com/translate/docs", tier: "paid", verifiedAt: V, source: "", freeInfo: "जाँचा नहीं।", rateInfo: "Unavailable", costInfo: "Unavailable", implemented: false }
];

export const ADAPTERS = ["openai", "tavily", "brave", "libretranslate"];

/** कौन सा adapter किस feature के लिए चलता है */
export const ADAPTER_FEATURE = { openai: "text", tavily: "search", brave: "search", libretranslate: "translation" };

/** paid जैसा गिना जाने वाला tier (मालिक की स्पष्ट मंज़ूरी चाहिए) */
export function isPaidLike(tier) {
  return tier === "paid" || tier === "credits_card";
}
