package com.xingmou.core.perception

import com.xingmou.data.catalog.ExpectedAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionGestureDetectorTest {

    // 构造一只手：掌心在 (cx,cy)；fingers[i]=true 表示该手指伸直；thumbOut 拇指外展
    private fun hand(cx: Float, cy: Float,
                     fingers: BooleanArray = BooleanArray(4),
                     thumbOut: Boolean = false): List<Point3D> {
        val pts = Array(21) { Point3D(cx, cy) }
        val tips = intArrayOf(8, 12, 16, 20)
        val pips = intArrayOf(6, 10, 14, 18)
        for (i in 0..3) {
            pts[pips[i]] = Point3D(cx, cy + 0.04f)
            pts[tips[i]] = Point3D(cx, if (fingers[i]) cy - 0.08f else cy + 0.08f)
        }
        pts[2] = Point3D(cx, cy)
        pts[4] = Point3D(if (thumbOut) cx + 0.08f else cx, cy)
        return pts.toList()
    }

    private fun frame(
        left: List<Point3D> = emptyList(),
        right: List<Point3D> = emptyList(),
        pose: List<Point3D> = emptyList()
    ) = PerceptionFrame(
        timestampMs = 0L,
        poseLandmarks = pose,
        leftHandLandmarks = left,
        rightHandLandmarks = right,
        poseDetected = pose.isNotEmpty(),
        leftHandDetected = left.isNotEmpty(),
        rightHandDetected = right.isNotEmpty()
    )

    private fun pose(lWristY: Float, rWristY: Float, shoulderY: Float = 0.5f) =
        Array(33) { Point3D(0.5f, 0.5f) }.also {
            it[11] = Point3D(0.4f, shoulderY); it[12] = Point3D(0.6f, shoulderY)
            it[15] = Point3D(0.4f, lWristY); it[16] = Point3D(0.6f, rWristY)
        }.toList()

    // ---------- 拍手 ----------
    @Test
    fun `窗口内两次拍手命中`() {
        val d = ActionGestureDetector(ExpectedAction.CLAP)
        var t = 0L
        assertFalse(d.onFrame(frame(hand(0.40f, 0.4f), hand(0.60f, 0.4f)), t)) // 远
        t = 200; assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), t)) // 接触
        t = 400; assertFalse(d.onFrame(frame(hand(0.40f, 0.4f), hand(0.60f, 0.4f)), t)) // 分开=1次
        t = 600; assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), t))
        t = 800; assertTrue(d.onFrame(frame(hand(0.40f, 0.4f), hand(0.60f, 0.4f)), t))  // 第2次
    }

    @Test
    fun `双手一直合着不算拍手`() {
        val d = ActionGestureDetector(ExpectedAction.CLAP)
        repeat(5) { i ->
            assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), i * 200L))
        }
    }

    @Test
    fun `接触点在画面下部不算拍手`() {
        val d = ActionGestureDetector(ExpectedAction.CLAP)
        var t = 0L
        assertFalse(d.onFrame(frame(hand(0.40f, 0.85f), hand(0.60f, 0.85f)), t))
        t = 200; assertFalse(d.onFrame(frame(hand(0.47f, 0.85f), hand(0.53f, 0.85f)), t))
        t = 400; assertFalse(d.onFrame(frame(hand(0.40f, 0.85f), hand(0.60f, 0.85f)), t))
        t = 600; assertFalse(d.onFrame(frame(hand(0.47f, 0.85f), hand(0.53f, 0.85f)), t))
        t = 800; assertFalse(d.onFrame(frame(hand(0.40f, 0.85f), hand(0.60f, 0.85f)), t))
    }

    // ---------- 举手 ----------
    @Test
    fun `举手持续500ms命中`() {
        val d = ActionGestureDetector(ExpectedAction.RAISE_HAND)
        val f = frame(pose = pose(lWristY = 0.2f, rWristY = 0.7f))
        assertFalse(d.onFrame(f, 0L))
        assertFalse(d.onFrame(f, 300L))
        assertTrue(d.onFrame(f, 500L))
    }

    @Test
    fun `抬手经过不足500ms不算`() {
        val d = ActionGestureDetector(ExpectedAction.RAISE_HAND)
        assertFalse(d.onFrame(frame(pose = pose(0.2f, 0.7f)), 0L))
        assertFalse(d.onFrame(frame(pose = pose(0.6f, 0.7f)), 400L))
        assertFalse(d.onFrame(frame(pose = pose(0.2f, 0.7f)), 800L))
    }

    @Test
    fun `无姿态时手在画面上部也算举手`() {
        val d = ActionGestureDetector(ExpectedAction.RAISE_HAND)
        val f = frame(left = hand(0.5f, 0.25f, fingers = BooleanArray(4) { true }))
        assertFalse(d.onFrame(f, 0L))
        assertTrue(d.onFrame(f, 500L))
    }

    // ---------- 静态手势 ----------
    @Test
    fun `食指指向持续400ms命中`() {
        val d = ActionGestureDetector(ExpectedAction.POINT_INDEX)
        val f = frame(right = hand(0.5f, 0.4f, fingers = booleanArrayOf(true, false, false, false)))
        assertFalse(d.onFrame(f, 0L))
        assertTrue(d.onFrame(f, 400L))
    }

    @Test
    fun `点赞持续400ms命中`() {
        val d = ActionGestureDetector(ExpectedAction.THUMB_UP)
        val f = frame(left = hand(0.5f, 0.4f, fingers = BooleanArray(4), thumbOut = true))
        assertFalse(d.onFrame(f, 0L))
        assertTrue(d.onFrame(f, 400L))
    }

    @Test
    fun `reset清空时序状态`() {
        val d = ActionGestureDetector(ExpectedAction.CLAP)
        assertFalse(d.onFrame(frame(hand(0.40f, 0.4f), hand(0.60f, 0.4f)), 0L))
        assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), 200L))
        assertFalse(d.onFrame(frame(hand(0.40f, 0.4f), hand(0.60f, 0.4f)), 400L)) // 1次
        d.reset()
        assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), 600L))    // 新计数
        assertFalse(d.onFrame(frame(hand(0.40f, 0.4f), hand(0.60f, 0.4f)), 800L))    // 仍只有1次
    }
}
