package com.xingmou.core.llm

import android.content.Context

/** 使用者自行输入的设备级 Key；不进入构建配置、Room 导出或日志。 */
class LocalApiKeyStore(context: Context, preferencesName: String = "xingmou_institution_api") {
    private val preferences = (context.applicationContext ?: context)
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun get(): String? = preferences.getString("deepseek_key", null)?.takeIf { it.isNotBlank() }

    fun isConfigured(): Boolean = get() != null

    fun save(rawKey: String): Boolean {
        val key = rawKey.trim()
        require(key.startsWith("sk-") && key.length in 24..256 && key.none(Char::isWhitespace)) {
            "invalid_api_key"
        }
        return preferences.edit().putString("deepseek_key", key).commit()
    }

    fun clear(): Boolean = preferences.edit().remove("deepseek_key").commit()
}
