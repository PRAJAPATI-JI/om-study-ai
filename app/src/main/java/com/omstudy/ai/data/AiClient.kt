package com.omstudy.ai.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiException(val code: String, message: String? = null) : Exception(message ?: code)

data class AiMe(val isAdmin: Boolean, val textReady: Boolean, val configVersion: Int)
data class AiReply(val text: String, val provider: String, val fallback: Boolean)

/**
 * AI सर्वर से बात। API key कभी ऐप में नहीं होती; ऐप सिर्फ अपने Firebase लॉगिन-टोकन के साथ
 * आपके सर्वर को बुलाता है और सर्वर ही provider चुनता है।
 */
object AiClient {
    suspend fun serverUrl(ctx: Context): String = ctx.dataStore.data.first()[Prefs.SERVER_URL].orEmpty()

    private suspend fun call(ctx: Context, method: String, path: String, body: String?): JSONObject = withContext(Dispatchers.IO) {
        val base = serverUrl(ctx)
        if (base.isEmpty()) throw AiException("no_server")
        val user = FirebaseAuth.getInstance().currentUser ?: throw AiException("not_logged_in")
        val token = user.getIdToken(false).await().token ?: throw AiException("not_logged_in")
        val conn = try {
            (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15000
                readTimeout = 40000
                setRequestProperty("authorization", "Bearer $token")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
        } catch (e: Exception) {
            throw AiException("network")
        }
        try {
            val status = try { conn.responseCode } catch (e: Exception) { throw AiException("network") }
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try { JSONObject(text) } catch (e: Exception) { throw AiException(if (status in 200..299) "bad_response" else "http_$status") }
            if (status !in 200..299) throw AiException(json.optString("error", "http_$status"), json.optString("message"))
            json
        } finally {
            conn.disconnect()
        }
    }

    suspend fun me(ctx: Context): Result<AiMe> = runCatching {
        val j = call(ctx, "GET", "/v1/me", null)
        AiMe(j.optBoolean("isAdmin"), j.optJSONObject("features")?.optBoolean("text") ?: false, j.optInt("configVersion"))
    }

    /** messages: (role, content) जोड़ियाँ; आखिरी हमेशा "user" */
    suspend fun chat(ctx: Context, messages: List<Pair<String, String>>): Result<AiReply> = runCatching {
        val arr = JSONArray()
        messages.forEach { (r, c) -> arr.put(JSONObject().put("role", r).put("content", c)) }
        val body = JSONObject().put("task", "chat").put("messages", arr).toString()
        val j = call(ctx, "POST", "/v1/ai/text", body)
        val prov = j.optJSONObject("provider")
        AiReply(j.optString("result"), prov?.optString("name").orEmpty(), j.optBoolean("fallback"))
    }
}
