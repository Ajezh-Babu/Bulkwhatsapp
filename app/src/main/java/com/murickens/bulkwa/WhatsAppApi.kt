package com.murickens.bulkwa

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class SendResult(val ok: Boolean, val detail: String)

/** Thin client for the official WhatsApp Business Cloud API (Meta). */
class WhatsAppApi(private val phoneNumberId: String, private val token: String) {
    private val client = OkHttpClient()

    /** Sends an APPROVED template message. params fill {{1}}, {{2}}... in the body. */
    fun sendTemplate(to: String, template: String, lang: String, params: List<String>): SendResult {
        val template_ = JSONObject()
            .put("name", template)
            .put("language", JSONObject().put("code", lang))
        if (params.isNotEmpty()) {
            val ps = JSONArray()
            params.forEach { ps.put(JSONObject().put("type", "text").put("text", it)) }
            template_.put("components", JSONArray().put(JSONObject().put("type", "body").put("parameters", ps)))
        }
        val body = JSONObject()
            .put("messaging_product", "whatsapp")
            .put("to", to)
            .put("type", "template")
            .put("template", template_)
        val req = Request.Builder()
            .url("https://graph.facebook.com/v21.0/$phoneNumberId/messages")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (r.isSuccessful) SendResult(true, "sent")
                else SendResult(false, JSONObject(s).optJSONObject("error")?.optString("message") ?: s)
            }
        } catch (e: Exception) {
            SendResult(false, e.message ?: "network error")
        }
    }
}
