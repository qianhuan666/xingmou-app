package com.xingmou.core.llm

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDeepSeekGatewayTest {
    @Test fun sendsRuntimeKeyAndExtractsOnlyModelContent() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                """{"choices":[{"message":{"content":"{\"mode\":\"answer\"}"}}]}"""
            ))
            val gateway = DirectDeepSeekGateway({ "sk-test-runtime-only" }, server.url("/").toString())
            assertEquals("""{"mode":"answer"}""", gateway.complete("system", "user").getOrThrow())
            val request = server.takeRequest()
            assertEquals("Bearer sk-test-runtime-only", request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().contains("\"content\":\"user\""))
        }
    }

    @Test fun missingKeyNeverSendsRequest() = runBlocking {
        MockWebServer().use { server ->
            val gateway = DirectDeepSeekGateway({ null }, server.url("/").toString())
            assertEquals("api_key_missing", gateway.complete("system", "user").exceptionOrNull()?.message)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun httpErrorDoesNotExposeResponseBodyOrKey() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("secret remote response"))
            val gateway = DirectDeepSeekGateway({ "sk-test-runtime-only" }, server.url("/").toString())
            val error = gateway.complete("system", "user").exceptionOrNull()?.message.orEmpty()
            assertEquals("deepseek_http_401", error)
            assertFalse(error.contains("secret"))
            assertFalse(error.contains("sk-test"))
        }
    }
}
