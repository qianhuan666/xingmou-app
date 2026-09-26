package com.xingmou.core.llm

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** 可选的真实网关联调；仅使用虚构内容，不读取应用数据或保存响应。 */
class DirectDeepSeekLiveSmokeTest {
    @Test
    fun syntheticJsonRoundTrip() = runBlocking {
        val key = System.getenv("DEEPSEEK_SMOKE_KEY")
        assumeTrue("Set a disposable key only for an explicit smoke run", !key.isNullOrBlank())
        val result = DirectDeepSeekGateway({ key }).complete(
            "Return only a JSON object with ok true.",
            "Synthetic connectivity check; no personal data."
        )
        assertTrue(result.exceptionOrNull()?.javaClass?.simpleName ?: "gateway_failed", result.isSuccess)
        assertTrue(JsonParser.parseString(result.getOrThrow()).asJsonObject.get("ok").asBoolean)
    }
}
