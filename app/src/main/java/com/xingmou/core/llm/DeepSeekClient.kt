package com.xingmou.core.llm

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 直连客户端。
 * - OpenAI 兼容格式，端点 /chat/completions
 * - 支持 response_format=json_object 强制 JSON 输出
 * - API Key 由外部注入（应从 Android Keystore / EncryptedSharedPreferences 读取，不要硬编码）
 */
class DeepSeekClient(
    private val apiKey: String,
    private val model: String = "deepseek-chat",
    private val baseUrl: String = "https://api.deepseek.com"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 调用大模型，返回原始响应体字符串。
     * @param systemPrompt 系统提示词
     * @param userText 已脱敏的用户文本
     * @param jsonMode 是否强制 JSON 输出（默认开启）
     * @return 成功返回响应文本；失败返回 Result.failure（调用方应降级到 SafeResponses）
     */
    suspend fun complete(systemPrompt: String, userText: String, jsonMode: Boolean = true): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JsonObject().apply {
                    addProperty("model", model)
                    addProperty("temperature", 0.2) // 低随机，输出稳定
                    if (jsonMode) {
                        add("response_format", JsonObject().apply { addProperty("type", "json_object") })
                    }
                    add("messages", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", systemPrompt)
                        })
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", userText)
                        })
                    })
                }

                val request = Request.Builder()
                    .url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("DeepSeek HTTP ${resp.code}: $respBody")
                    }
                    // 提取 choices[0].message.content
                    extractContent(respBody)
                }
            }
        }

    /** 从 OpenAI 兼容响应中提取 message.content */
    private fun extractContent(respBody: String): String {
        val obj = com.google.gson.JsonParser.parseString(respBody).asJsonObject
        return obj
            .getAsJsonArray("choices")
            .first()
            .asJsonObject
            .getAsJsonObject("message")
            .get("content")
            .asString
    }
}
