package com.xingmou.core.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalApiKeyStoreInstrumentedTest {
    @Test fun savesAndClearsRuntimeKeyInTestAppOnly() {
        // 使用测试专用的设置文件，不碰用户主应用的 Key。
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "xingmou_institution_api_instrumented_test"
        context.deleteSharedPreferences(name)
        val store = LocalApiKeyStore(context, name)
        try {
            assertFalse(store.isConfigured())
            assertTrue(store.save("sk-test-device-only-1234567890123456"))
            assertTrue(store.isConfigured())
            assertEquals("sk-test-device-only-1234567890123456", store.get())
        } finally {
            store.clear()
            assertFalse(store.isConfigured())
            context.deleteSharedPreferences(name)
        }
    }
}
