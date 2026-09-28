package com.xingmou.core.llm

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.xingmou.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 大模型供应商。三家均采用 OpenAI 兼容的 /chat/completions 协议。 */
enum class ChatLlmProvider(val label: String) {
    DEEPSEEK("DeepSeek"),
    DOUBAO("豆包"),
    QWEN("千问")
}

/** 一条聊天消息。 */
data class ChatMessage(val role: String, val content: String)

/**
 * 多供应商 LLM 聊天网关。
 *
 * 统一封装 DeepSeek / 豆包（火山方舟）/ 千问（阿里百炼）三家的兼容接口。
 * Key 仅在发起请求时通过 [keyProvider] 读取，不写入日志或审计信息。
 */
class ChatLlmGateway(
    private val provider: ChatLlmProvider,
    private val keyProvider: () -> String?,
    /** 豆包需要传入推理接入点 ID（ep- 开头）；千问/DeepSeek 可留空使用默认模型。 */
    private val modelOrEndpoint: String = "",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    private val baseUrl: String = when (provider) {
        ChatLlmProvider.DEEPSEEK -> "https://api.deepseek.com"
        ChatLlmProvider.DOUBAO -> "https://ark.cn-beijing.volces.com/api/v3"
        ChatLlmProvider.QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
    }

    private val defaultModel: String = when (provider) {
        ChatLlmProvider.DEEPSEEK -> "deepseek-chat"
        // 豆包 ep- 接入点：优先 BuildConfig.ARK_ENDPOINT（内置），占位空时由 chat() fallback 到用户自填
        ChatLlmProvider.DOUBAO -> BuildConfig.ARK_ENDPOINT
        ChatLlmProvider.QWEN -> "qwen-plus"
    }

    /** 多轮聊天。[history] 不包含当前 user 消息时会自动追加。 */
    suspend fun chat(systemPrompt: String, history: List<ChatMessage>, userMessage: String): Result<String> =
        withContext(Dispatchers.IO) {
            val key = if (provider == ChatLlmProvider.DOUBAO && BuildConfig.ARK_API_KEY.isNotBlank()) {
                // 豆包内置凭证：BuildConfig.ARK_API_KEY 优先，开箱即用。
                BuildConfig.ARK_API_KEY
            } else {
                keyProvider()?.takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(IllegalStateException("api_key_missing"))
            }
            val model = modelOrEndpoint.ifBlank { defaultModel }
            if (model.isBlank()) {
                return@withContext Result.failure(IllegalStateException("model_endpoint_missing"))
            }
            val messages = JsonArray().apply {
                add(JsonObject().apply { addProperty("role", "system"); addProperty("content", systemPrompt) })
                history.forEach { add(JsonObject().apply { addProperty("role", it.role); addProperty("content", it.content) }) }
                add(JsonObject().apply { addProperty("role", "user"); addProperty("content", userMessage) })
            }
            val body = JsonObject().apply {
                addProperty("model", model)
                addProperty("temperature", 0.7)
                addProperty("max_tokens", 800)
                add("messages", messages)
            }
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer $key")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IllegalStateException("http_${response.code}"))
                    }
                    val content = response.body?.string()
                        ?: return@use Result.failure(IllegalStateException("empty_response"))
                    val parsed = JsonParser.parseString(content).asJsonObject
                    val answer = parsed.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("message")?.get("content")?.asString
                        ?.takeIf { it.isNotBlank() }
                        ?: return@use Result.failure(IllegalStateException("invalid_response"))
                    Result.success(answer.trim())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                Result.failure(IllegalStateException("network_error"))
            } catch (_: Exception) {
                Result.failure(IllegalStateException("invalid_response"))
            }
        }
}
