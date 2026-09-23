package com.xingmou.core.llm

import com.xingmou.BuildConfig

/** 开发环境客户端入口。Key 为空时明确禁用网络模型，不使用硬编码默认值。 */
object DeepSeekClientFactory {
    fun createOrNull(): DeepSeekClient? =
        BuildConfig.DEEPSEEK_API_KEY.trim().takeIf { it.isNotEmpty() }?.let(::DeepSeekClient)

    fun isConfigured(): Boolean = BuildConfig.DEEPSEEK_API_KEY.isNotBlank()
}
