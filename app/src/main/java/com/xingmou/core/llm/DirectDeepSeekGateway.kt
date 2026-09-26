package com.xingmou.core.llm

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.xingmou.core.agent.GatewayRetryableException
import com.xingmou.core.agent.ModelGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 纯前端 BYOK 直连。Key 仅在发起请求时读取，不附带到审计或错误信息。 */
class DirectDeepSeekGateway(
    private val keyProvider: () -> String?,
    private val baseUrl: String = "https://api.deepseek.com",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
) : ModelGateway {
    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        val key = keyProvider()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(IllegalStateException("api_key_missing"))
        try {
            val messages = JsonArray().apply {
                add(JsonObject().apply { addProperty("role", "system"); addProperty("content", systemPrompt) })
                add(JsonObject().apply { addProperty("role", "user"); addProperty("content", userMessage) })
            }
            val body = JsonObject().apply {
                addProperty("model", "deepseek-chat")
                addProperty("temperature", 0.2)
                addProperty("max_tokens", 600)
                add("response_format", JsonObject().apply { addProperty("type", "json_object") })
                add("messages", messages)
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = "deepseek_http_${response.code}"
                    return@withContext Result.failure(
                        if (response.code == 429 || response.code in 500..599) GatewayRetryableException(error)
                        else IllegalStateException(error)
                    )
                }
                if (response.body == null) return@withContext Result.failure(IllegalStateException("deepseek_empty_response"))
                val content = response.peekBody(256_000).string()
                val parsed = JsonParser.parseString(content).asJsonObject
                val answer = parsed.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")?.asString
                    ?.takeIf { it.isNotBlank() && it.length <= 60_000 }
                    ?: return@withContext Result.failure(IllegalStateException("deepseek_invalid_response"))
                Result.success(answer)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            Result.failure(GatewayRetryableException("deepseek_network_error"))
        } catch (_: Exception) {
            Result.failure(IllegalStateException("deepseek_invalid_response"))
        }
    }
}
