package com.xingmou.core.llm

import android.content.Context

/**
 * 使用者自行输入的设备级 Key；不进入构建配置、Room 导出或日志。
 *
 * 扩展为多供应商存储（DeepSeek / 豆包 / 千问），并保留旧的 deepseek 单 Key 接口向后兼容。
 */
class LocalApiKeyStore(context: Context, preferencesName: String = "xingmou_institution_api") {
    private val preferences = (context.applicationContext ?: context)
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    // ---- 旧接口（默认 DeepSeek，向后兼容现有调用方） ----

    fun get(): String? = get(ChatLlmProvider.DEEPSEEK)

    fun isConfigured(): Boolean = get() != null

    fun save(rawKey: String): Boolean = save(ChatLlmProvider.DEEPSEEK, rawKey)

    fun clear(): Boolean = clear(ChatLlmProvider.DEEPSEEK)

    // ---- 多供应商接口 ----

    private fun keyName(provider: ChatLlmProvider) = "${provider.name.lowercase()}_key"

    fun get(provider: ChatLlmProvider): String? =
        preferences.getString(keyName(provider), null)?.takeIf { it.isNotBlank() }

    fun isConfigured(provider: ChatLlmProvider): Boolean = get(provider) != null

    /** 是否任一供应商已配置。 */
    fun isAnyConfigured(): Boolean = ChatLlmProvider.entries.any { isConfigured(it) }

    fun save(provider: ChatLlmProvider, rawKey: String): Boolean {
        val key = rawKey.trim()
        require(key.isNotBlank() && key.length in 8..512 && key.none(Char::isWhitespace)) {
            "invalid_api_key"
        }
        return preferences.edit().putString(keyName(provider), key).commit()
    }

    fun clear(provider: ChatLlmProvider): Boolean =
        preferences.edit().remove(keyName(provider)).commit()

    // ---- 供应商选择与模型端点 ----

    // 默认豆包：走 veFaaS 反代，开箱即用，最终用户无需自配 Key。
    private val defaultProvider = ChatLlmProvider.DOUBAO

    fun selectedProvider(): ChatLlmProvider = runCatching {
        ChatLlmProvider.valueOf(preferences.getString("chat_provider", defaultProvider.name) ?: defaultProvider.name)
    }.getOrDefault(defaultProvider)

    fun setSelectedProvider(provider: ChatLlmProvider): Boolean =
        preferences.edit().putString("chat_provider", provider.name).commit()

    /** 豆包的推理接入点 ID（ep- 开头）。千问/DeepSeek 返回空串即可使用默认模型。 */
    fun modelEndpoint(provider: ChatLlmProvider): String =
        preferences.getString("model_endpoint_${provider.name.lowercase()}", "") ?: ""

    fun setModelEndpoint(provider: ChatLlmProvider, value: String): Boolean =
        preferences.edit().putString("model_endpoint_${provider.name.lowercase()}", value.trim()).commit()
}
