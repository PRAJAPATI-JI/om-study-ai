# OM STUDY AI — AI सर्वर + Owner Admin Panel (सरल हिंदी गाइड)

## यह क्या है
- `backend/` = आपका अपना AI सर्वर (Cloudflare Worker + D1 डेटाबेस)। API keys सिर्फ यहीं, एन्क्रिप्ट होकर रहती हैं। ऐप या GitHub में कभी नहीं।
- `backend/public/` = Owner Admin Panel वेबसाइट (उसी सर्वर के साथ)। ऐप → सेटिंग्स → "Owner Admin Panel खोलें" से भी खुलती है।
- ऐप का Firebase लॉगिन वही रहता है (प्रोजेक्ट `om-study-ai`)। Firebase Blaze/कार्ड की ज़रूरत इस तरीके में नहीं रखी।

## ज़रूरी सच (पढ़ लीजिए)
- Cloudflare Workers Free: 1 लाख requests/दिन (आधिकारिक docs, 2026-11-13 को जाँचा)। Cloudflare खाते में कार्ड माँगा जाता है या नहीं, यह मैंने पक्का नहीं किया — साइनअप पर जो दिखे वही मानिए। कार्ड माँगे तो मुझे बताइए, विकल्प सोचेंगे।
- यह deploy मैं यहाँ से चलाकर जाँच नहीं सका (मेरे पास आपका Cloudflare खाता नहीं)। कोड के 35 टेस्ट और admin वेबसाइट का ब्राउज़र टेस्ट नकली provider के साथ पास हैं; असली Cloudflare और असली AI providers पर अभी "Not tested"।
- मुफ्त providers की सीमाएँ बदलती रहती हैं। Admin Panel → API Directory में हर provider की जाँच-तारीख और स्रोत दिखता है।

## एक बार का सेटअप (फोन से)
1. **Cloudflare खाता** बनाइए: dash.cloudflare.com → Sign up (मुफ्त)।
2. **Account ID**: Cloudflare → Workers & Pages → दाईं ओर "Account ID" कॉपी कीजिए।
3. **API Token**: My Profile → API Tokens → Create Token → "Edit Cloudflare Workers" template → बनाइए, token कॉपी कीजिए। (D1 के लिए उसमें "D1: Edit" अनुमति भी जोड़िए।)
4. **अपनी UID**: ऐप v0.4.0 खोलिए → सेटिंग्स → नीचे "आपकी UID" — दबाकर कॉपी कीजिए।
5. **MASTER_KEY**: कोई भी 32+ अक्षर का लंबा गुप्त वाक्य सोचिए (कहीं और इस्तेमाल न कीजिए, चैट में मत भेजिए)।
6. GitHub → om-study-ai → Settings → Secrets and variables → Actions → New repository secret, ये चार बनाइए:
   `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `OWNER_UID`, `MASTER_KEY`
7. Actions → "Deploy AI backend (Cloudflare)" → Run workflow। हरा होने पर सर्वर का पता Cloudflare में दिखेगा (`https://om-study-ai-api.<आपका-नाम>.workers.dev`)।
8. ऐप → सेटिंग्स → "AI सर्वर" में वह पता डालिए → सेव → "कनेक्शन जाँचें"।
9. वही पता ब्राउज़र में खोलिए → ऐप वाले ईमेल/पासवर्ड से लॉगिन।
   - अगर लॉगिन "API key not valid / restricted" कहे: Google Cloud Console → APIs & Services → Credentials में वह Web API key Android-only restricted है; उसकी restriction में Identity Toolkit API की अनुमति दीजिए, या मुझे बताइए।

## Admin Panel में पहला AI चालू करना
1. "API Directory" या "Text AI" → किसी provider (जैसे Gemini/Groq) के आगे "जोड़ें"।
2. उसके कार्ड में model का नाम जाँचिए, "चालू" टिक करके "सेव और publish"।
3. प्रदाता की साइट से अपनी API key बनाकर यहीं पासवर्ड-बॉक्स में चिपकाइए → "Key सेव करें" (चैट में कभी मत भेजिए)। अब सिर्फ •••• और आखिरी 4 अक्षर दिखेंगे।
4. "Test Connection" दबाइए।
5. ऊपर "क्रम" में Primary और Backup चुनकर "क्रम publish करें"। अगली ऐप request से नया provider चलेगा, APK बदलने की ज़रूरत नहीं।

## सुरक्षा के नियम (डिफ़ॉल्ट)
- Free-only चालू और Paid lock चालू। Paid/कार्ड-वाला provider तब तक नहीं चलता जब तक आप खुद चारों शर्तें न खोलें (Quota & Budget देखिए)।
- हर बदलाव का Audit रिकॉर्ड और पुराने config पर rollback (Settings)।
- Admin के सारे बदलाव सिर्फ OWNER_UID वाले खाते से; बाकी viewer सिर्फ पढ़ सकते हैं।

## अभी बना नहीं
Image, Voice, PDF/OCR, Video AI के सर्वर-adapter। Directory में दिखते हैं पर "अभी बना नहीं" लिखा है; ऐप को "उपलब्ध नहीं" मिलता है, नकली नतीजा नहीं। AI चैट अब फोन में सेव होती है (आखिरी 200 संदेश).
