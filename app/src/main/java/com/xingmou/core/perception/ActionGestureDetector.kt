package com.xingmou.core.perception

import com.xingmou.data.catalog.ExpectedAction
import kotlin.math.hypot

/**
 * 基于 PerceptionFrame 关键点的时序动作检测。纯 Kotlin，无 Android 依赖。
 * 坐标为前置镜像画面的归一化坐标，y 轴向下。
 */
class ActionGestureDetector(private val expected: ExpectedAction) {

    // ---- 可调阈值（初值，真机实测后调整）----
    private companion object {
        const val CLAP_WINDOW_MS = 2_500L
        const val CLAP_CONTACT_DIST = 0.09f
        const val CLAP_APART_DIST = 0.15f
        const val CLAP_WIDE_DIST = 0.18f
        const val CLAP_CONTACT_MAX_Y = 0.70f
        const val CLAP_MIN_INTERVAL_MS = 120L
        const val CLAP_MAX_INTERVAL_MS = 1_500L
        const val CLAP_TARGET_COUNT = 2

        const val RAISE_SHOULDER_GAP = 0.05f
        const val RAISE_NO_POSE_Y = 0.35f
        const val RAISE_HOLD_MS = 500L

        const val STATIC_HOLD_MS = 400L

        // 手部关键点
        val PALM_POINTS = intArrayOf(0, 5, 9, 13, 17)
    }

    private enum class ClapPhase { WAIT_WIDE, WAIT_CONTACT, WAIT_APART }

    // 静态手势/举手保持
    private var holdSinceMs = -1L

    // 拍手时序
    private var clapPhase = ClapPhase.WAIT_WIDE
    private val clapTimes = ArrayDeque<Long>()
    private var lastContactAt = -1L

    fun onFrame(frame: PerceptionFrame, nowMs: Long): Boolean = when (expected) {
        ExpectedAction.CLAP -> detectClap(frame, nowMs)
        ExpectedAction.RAISE_HAND -> detectRaise(frame, nowMs)
        ExpectedAction.POINT_INDEX,
        ExpectedAction.THUMB_UP,
        ExpectedAction.VICTORY,
        ExpectedAction.OPEN_PALM -> detectStatic(frame, nowMs)
    }

    fun reset() {
        holdSinceMs = -1L
        clapPhase = ClapPhase.WAIT_WIDE
        clapTimes.clear()
        lastContactAt = -1L
    }

    // ---------- 拍手 ----------
    private fun detectClap(frame: PerceptionFrame, nowMs: Long): Boolean {
        val l = frame.leftHandLandmarks
        val r = frame.rightHandLandmarks
        if (l.size < 21 || r.size < 21) return false

        val c1 = palmCenter(l)
        val c2 = palmCenter(r)
        val dist = hypot(c1.x - c2.x, c1.y - c2.y)
        val contactY = (c1.y + c2.y) / 2f

        when (clapPhase) {
            ClapPhase.WAIT_WIDE -> {
                if (dist > CLAP_WIDE_DIST) clapPhase = ClapPhase.WAIT_CONTACT
            }
            ClapPhase.WAIT_CONTACT -> {
                if (dist > CLAP_WIDE_DIST) return false // 保持等接触
                if (dist < CLAP_CONTACT_DIST && contactY < CLAP_CONTACT_MAX_Y) {
                    val validRhythm = lastContactAt < 0 ||
                        (nowMs - lastContactAt) in CLAP_MIN_INTERVAL_MS..CLAP_MAX_INTERVAL_MS
                    if (validRhythm) {
                        lastContactAt = nowMs
                        clapPhase = ClapPhase.WAIT_APART
                    } else {
                        clapPhase = ClapPhase.WAIT_WIDE
                    }
                }
            }
            ClapPhase.WAIT_APART -> {
                if (dist > CLAP_APART_DIST) {
                    clapTimes.addLast(lastContactAt)
                    clapPhase = ClapPhase.WAIT_CONTACT
                }
            }
        }

        while (clapTimes.isNotEmpty() && nowMs - clapTimes.first() > CLAP_WINDOW_MS) {
            clapTimes.removeFirst()
        }
        if (clapTimes.size >= CLAP_TARGET_COUNT) {
            reset()
            return true
        }
        return false
    }

    private fun palmCenter(points: List<Point3D>): Point3D {
        var x = 0f; var y = 0f
        for (i in PALM_POINTS) { x += points[i].x; y += points[i].y }
        val n = PALM_POINTS.size
        return Point3D(x / n, y / n)
    }

    // ---------- 举手 ----------
    private fun detectRaise(frame: PerceptionFrame, nowMs: Long): Boolean {
        val raised = if (frame.poseDetected && frame.poseLandmarks.size >= 17) {
            val lm = frame.poseLandmarks
            val leftUp = lm[15].y < lm[11].y - RAISE_SHOULDER_GAP
            val rightUp = lm[16].y < lm[12].y - RAISE_SHOULDER_GAP
            leftUp || rightUp
        } else {
            val handUp = (frame.leftHandDetected && frame.leftHandLandmarks[0].y < RAISE_NO_POSE_Y) ||
                (frame.rightHandDetected && frame.rightHandLandmarks[0].y < RAISE_NO_POSE_Y)
            handUp
        }
        return hold(raised, nowMs, RAISE_HOLD_MS)
    }

    // ---------- 静态手势 ----------
    private fun detectStatic(frame: PerceptionFrame, nowMs: Long): Boolean {
        val target = when (expected) {
            ExpectedAction.POINT_INDEX -> HandGesture.POINT_INDEX
            ExpectedAction.THUMB_UP -> HandGesture.THUMB_UP
            ExpectedAction.VICTORY -> HandGesture.VICTORY
            ExpectedAction.OPEN_PALM -> HandGesture.OPEN_PALM
            else -> HandGesture.NONE
        }
        val match = (frame.leftHandDetected && StateAnalyzer.analyzeGesture(frame.leftHandLandmarks) == target) ||
            (frame.rightHandDetected && StateAnalyzer.analyzeGesture(frame.rightHandLandmarks) == target)
        return hold(match, nowMs, STATIC_HOLD_MS)
    }

    private fun hold(condition: Boolean, nowMs: Long, requiredMs: Long): Boolean {
        if (!condition) {
            holdSinceMs = -1L
            return false
        }
        if (holdSinceMs < 0L) holdSinceMs = nowMs
        return nowMs - holdSinceMs >= requiredMs
    }
}
