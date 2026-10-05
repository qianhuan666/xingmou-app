package com.xingmou.core.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectSessionControllerTest {

    private fun newController() = DetectSessionController(timeoutMs = 10_000L)

    @Test
    fun `授权后进入检测中`() {
        val c = newController()
        c.start("Q1", isCamera = true)
        assertEquals(DetectPhase.AWAIT_PERMISSION, c.session?.phase)
        c.onPermissionReady(available = true)
        assertEquals(DetectPhase.DETECTING, c.session?.phase)
        assertEquals(10_000L, c.session?.remainingMs)
    }

    @Test
    fun `拒绝授权直接手动兜底`() {
        val c = newController()
        c.start("Q1", isCamera = true)
        c.onPermissionReady(available = false)
        assertEquals(DetectPhase.MANUAL_FALLBACK, c.session?.phase)
    }

    @Test
    fun `检测中命中触发自动作答并结束`() {
        val c = newController()
        var answered: String? = null
        c.onAutoAnswer = { qid, _ -> answered = qid }
        c.start("Q1", isCamera = true)
        c.onPermissionReady(true)
        c.onHit(null, 5_000L)
        assertEquals(DetectPhase.HIT, c.session?.phase)
        assertEquals("Q1", answered)
    }

    @Test
    fun `10秒未命中进入重试并回调鼓励`() {
        val c = newController()
        var encouragedCamera = false
        c.onEncourage = { camera -> encouragedCamera = camera }
        c.start("Q1", isCamera = true)
        c.onPermissionReady(true)
        c.tick(10_000L)
        assertEquals(DetectPhase.RETRYING, c.session?.phase)
        assertTrue(encouragedCamera)
    }

    @Test
    fun `重试后再10秒进入手动兜底`() {
        val c = newController()
        c.start("Q1", isCamera = false)
        c.onPermissionReady(true)
        c.tick(10_000L)
        assertEquals(DetectPhase.RETRYING, c.session?.phase)
        c.tick(20_000L)
        assertEquals(DetectPhase.MANUAL_FALLBACK, c.session?.phase)
    }

    @Test
    fun `手动兜底阶段仍可被命中自动作答`() {
        val c = newController()
        var answered = false
        c.onAutoAnswer = { _, _ -> answered = true }
        c.start("Q1", isCamera = true)
        c.onPermissionReady(true)
        c.tick(10_000L)
        c.tick(20_000L)
        assertEquals(DetectPhase.MANUAL_FALLBACK, c.session?.phase)
        c.onHit("小狗", 21_000L)
        assertTrue(answered)
    }

    @Test
    fun `命中关键词透传meta`() {
        val c = newController()
        var meta: String? = "未设置"
        c.onAutoAnswer = { _, m -> meta = m }
        c.start("Q1", isCamera = false)
        c.onPermissionReady(true)
        c.onHit("苹果", 1_000L)
        assertEquals("苹果", meta)
    }

    @Test
    fun `start换题会重置旧会话`() {
        val c = newController()
        c.start("Q1", isCamera = true)
        c.onPermissionReady(true)
        c.tick(10_000L) // RETRYING
        c.start("Q2", isCamera = true)
        assertEquals("Q2", c.session?.questionId)
        assertEquals(DetectPhase.AWAIT_PERMISSION, c.session?.phase)
        assertEquals(10_000L, c.session?.remainingMs)
    }

    @Test
    fun `stop结束会话`() {
        val c = newController()
        c.start("Q1", isCamera = true)
        c.stop()
        assertNull(c.session)
    }

    @Test
    fun `HIT后重复命中不重复作答`() {
        val c = newController()
        var count = 0
        c.onAutoAnswer = { _, _ -> count++ }
        c.start("Q1", isCamera = true)
        c.onPermissionReady(true)
        c.onHit(null, 100L)
        c.onHit(null, 200L)
        assertEquals(1, count)
    }

    @Test
    fun `begin锚定后超时行为正确`() {
        val c = newController()
        c.start("Q1", isCamera = true)
        c.onPermissionReady(true)
        c.begin(1_000L)
        c.tick(5_000L)
        assertEquals(DetectPhase.DETECTING, c.session?.phase)
        c.tick(11_000L)
        assertEquals(DetectPhase.RETRYING, c.session?.phase)
    }

    @Test
    fun `forceManual把检测中会话转为手动兜底`() {
        val c = newController()
        c.start("Q1", isCamera = true)
        c.onPermissionReady(true)
        c.forceManual()
        assertEquals(DetectPhase.MANUAL_FALLBACK, c.session?.phase)
    }
}
