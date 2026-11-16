package com.example.data.network

import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class FonnteResult(
    val success: Boolean,
    val detail: String = "",
    val reason: String = ""
)

class FonnteService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun sendMessage(token: String, targetPhone: String, message: String): FonnteResult {
        if (token.isBlank()) {
            return FonnteResult(false, reason = "Token Fonnte belum diatur di Pengaturan.")
        }
        return withContext(Dispatchers.IO) {
            try {
                val normalized = SecurityUtils.normalizeFonnteTarget(targetPhone)
                val body = FormBody.Builder()
                    .add("target", normalized)
                    .add("message", message)
                    .add("countryCode", "0")
                    .build()

                val request = Request.Builder()
                    .url("https://api.fonnte.com/send")
                    .addHeader("Authorization", token.trim())
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(responseBody) }.getOrNull()
                    val ok = json?.optBoolean("status", false) == true || json?.optBoolean("Status", false) == true
                    FonnteResult(
                        success = ok,
                        detail = json?.optString("detail", "").orEmpty(),
                        reason = json?.optString("reason", "").orEmpty().ifBlank {
                            if (!ok) "Fonnte menolak permintaan (HTTP ${response.code})" else ""
                        }
                    )
                }
            } catch (e: Exception) {
                FonnteResult(false, reason = e.message ?: "Gagal menghubungi server Fonnte.")
            }
        }
    }
}
