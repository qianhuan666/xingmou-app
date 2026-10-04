# 观察题摄像头动作检测 + 麦克风语音识别 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让拍手/举手/手势题由摄像头自动判定、命名/说话题由麦克风语音判定，检测失败/拒绝权限时回退现有手动三按钮。

**Architecture:** 纯 Kotlin 检测器（`KeywordMatcher`、`ActionGestureDetector`）+ 纯 Kotlin 会话状态机（`DetectSessionController`）承载全部可单测逻辑；`XingmouViewModel` 做薄适配（权限检查、摄像头引用计数、作答路由）；`SpeechAnswerDetector` 封装 Android `SpeechRecognizer`；Compose 层新增权限门 `QuestionPermissionGate` 和检测面板，嵌入现有 BaselineCard / CurriculumPlayerCard。

**Tech Stack:** Kotlin、CameraX 1.3.4、MediaPipe tasks-vision 0.10.14（已接入）、Android SpeechRecognizer、Jetpack Compose、JUnit4（testImplementation 已配置）。

设计文档：`docs/superpowers/specs/2026-10-04-camera-speech-answer-detection-design.md`

---

## 文件结构

新增：

- `app/src/main/java/com/xingmou/core/perception/KeywordMatcher.kt` — 语音文本归一化与关键词匹配（纯 Kotlin）
- `app/src/main/java/com/xingmou/core/perception/ActionGestureDetector.kt` — 时序动作检测（纯 Kotlin）
- `app/src/main/java/com/xingmou/core/perception/DetectSessionController.kt` — 检测会话状态机（纯 Kotlin）
- `app/src/main/java/com/xingmou/core/perception/SpeechAnswerDetector.kt` — SpeechRecognizer 薄封装
- `app/src/main/java/com/xingmou/ui/child/QuestionPermissionGate.kt` — 运行时权限说明卡/申请/去设置
- `app/src/test/java/com/xingmou/core/perception/KeywordMatcherTest.kt`
- `app/src/test/java/com/xingmou/core/perception/ActionGestureDetectorTest.kt`
- `app/src/test/java/com/xingmou/core/perception/DetectSessionControllerTest.kt`

修改：

- `app/src/main/java/com/xingmou/data/catalog/QuestionCatalog.kt` — 枚举、字段、题目打标
- `app/src/main/java/com/xingmou/core/perception/PerceptionManager.kt` — 暴露 liteMode
- `app/src/main/java/com/xingmou/UiModels.kt` — AutoDetectState 等
- `app/src/main/java/com/xingmou/XingmouViewModel.kt` — 会话编排、引用计数、作答路由
- `app/src/main/java/com/xingmou/ui/child/ChildScreen.kt` — ObservedAnswerArea/检测面板/接线
- `app/src/main/java/com/xingmou/ui/XingmouApp.kt` — 新回调接线
- `app/src/main/AndroidManifest.xml` — RECORD_AUDIO + queries

所有命令在仓库根目录 `c:\Users\Zgl\Desktop\AI\xingmou-app` 下执行（PowerShell，用分号不用 `&&`）。

---

### Task 0: 验证 JVM 单测可运行

**Files:**
- Create: `app/src/test/java/com/xingmou/SmokeTest.kt`

- [ ] **Step 1: 写一个最小测试**

```kotlin
package com.xingmou

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test
    fun junitRuns() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 2: 运行并确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.xingmou.SmokeTest"`
Expected: BUILD SUCCESSFUL，1 test passed。

- [ ] **Step 3: 提交**

```powershell
git add app/src/test/java/com/xingmou/SmokeTest.kt
git commit -m "test: 验证 JVM 单测链路"
```

---

### Task 1: 题库枚举与字段

**Files:**
- Modify: `app/src/main/java/com/xingmou/data/catalog/QuestionCatalog.kt:4-20`

- [ ] **Step 1: 新增枚举与字段**

在 `enum class QuestionType` 之后新增：

```kotlin
/** 摄像头动作题期望的动作；null 表示非动作题。 */
enum class ExpectedAction { CLAP, RAISE_HAND, POINT_INDEX, THUMB_UP, VICTORY, OPEN_PALM }

/** 语音题检测方式：KEYWORD=说出目标词；ANY=只要有效开口。 */
enum class SpeechMode { KEYWORD, ANY }

/** 语音题的期望；keywords 为模糊匹配词表（含同义词/儿语）。 */
data class ExpectedSpeech(
    val mode: SpeechMode,
    val keywords: List<String> = emptyList()
)
```

把 `QuestionDefinition` 数据类改为（末尾增加两个字段，保留其余参数与默认值）：

```kotlin
data class QuestionDefinition(
    val id: String,
    val version: Int,
    val moduleId: String,
    val domain: String,
    val type: QuestionType,
    val prompt: String,
    val options: List<String>,
    val correctOption: Int? = null,
    val sourceRef: String = "local-v0.7b",
    val assetKey: String = "training_star",
    val stimulus: String = "",
    val previewMs: Long = 3_000L,
    val expectedAction: ExpectedAction? = null,
    val expectedSpeech: ExpectedSpeech? = null
)
```

同步修改文件内私有的 `question(...)` 工厂函数签名（透传两个新字段）：

```kotlin
private fun question(
    id: String,
    moduleId: String,
    domain: String,
    type: QuestionType,
    prompt: String,
    options: List<String>,
    correctOption: Int?,
    stimulus: String = "",
    previewMs: Long = 0L,
    expectedAction: ExpectedAction? = null,
    expectedSpeech: ExpectedSpeech? = null
): QuestionDefinition =
    QuestionDefinition(id, 1, moduleId, domain, type, prompt, options, correctOption, "WEB_BANK_V1",
        stimulus = stimulus, previewMs = previewMs,
        expectedAction = expectedAction, expectedSpeech = expectedSpeech)
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（现有所有 question() 调用靠默认值不受影响）。

- [ ] **Step 3: 提交**

```powershell
git add app/src/main/java/com/xingmou/data/catalog/QuestionCatalog.kt
git commit -m "feat(child): QuestionDefinition 增加动作/语音检测标记字段"
```

---

### Task 2: KeywordMatcher（TDD）

**Files:**
- Create: `app/src/test/java/com/xingmou/core/perception/KeywordMatcherTest.kt`
- Create: `app/src/main/java/com/xingmou/core/perception/KeywordMatcher.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.xingmou.core.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeywordMatcherTest {
    @Test
    fun `命中关键词返回该词`() {
        assertEquals("苹果", KeywordMatcher.matches("这是苹果", listOf("苹果")))
    }

    @Test
    fun `忽略空格标点和大小写`() {
        assertEquals("dog", KeywordMatcher.matches("  It's a DOG! ", listOf("dog")))
    }

    @Test
    fun `同义词或儿语命中`() {
        assertEquals("狗狗", KeywordMatcher.matches("是狗狗", listOf("小狗", "狗狗")))
    }

    @Test
    fun `未命中返回null`() {
        assertNull(KeywordMatcher.matches("香蕉", listOf("苹果")))
    }

    @Test
    fun `空文本与纯标点返回null`() {
        assertNull(KeywordMatcher.matches("   ", listOf("苹果")))
        assertNull(KeywordMatcher.matches("。。！", listOf("苹果")))
    }

    @Test
    fun `空词表返回null`() {
        assertNull(KeywordMatcher.matches("苹果", emptyList()))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.xingmou.core.perception.KeywordMatcherTest"`
Expected: 编译失败，`unresolved reference: KeywordMatcher`。

- [ ] **Step 3: 实现**

```kotlin
package com.xingmou.core.perception

/**
 * 语音识别文本与目标词的模糊匹配。
 * 归一化：转小写、去空白与中英文标点后做包含匹配。
 */
object KeywordMatcher {

    private val noise = Regex(
        "[\\s\\p{Punct}，。！？、；：“”‘’（）【】《》…—·,.!?;:()\\[\\]{}<>\"'/\\\\|~`@#\$%^&*_+=]+"
    )

    fun normalize(text: String): String = text.lowercase().replace(noise, "")

    /** 返回命中的原始关键词（词表中的写法），未命中返回 null。 */
    fun matches(text: String, keywords: List<String>): String? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null
        for (keyword in keywords) {
            val target = normalize(keyword)
            if (target.isNotEmpty() && normalized.contains(target)) return keyword
        }
        return null
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.xingmou.core.perception.KeywordMatcherTest"`
Expected: 6 tests passed。

- [ ] **Step 5: 提交**

```powershell
git add app/src/main/java/com/xingmou/core/perception/KeywordMatcher.kt app/src/test/java/com/xingmou/core/perception/KeywordMatcherTest.kt
git commit -m "feat(child): 新增语音关键词模糊匹配器"
```

---

### Task 3: ActionGestureDetector（TDD）

关键点：手部 21 点索引 0=手腕、5/9/13/17=四根掌骨、指尖 8/12/16/20、拇指尖 4；姿态 33 点索引 11/12 肩、15/16 腕。判定规则见设计文档 3.1。

**Files:**
- Create: `app/src/test/java/com/xingmou/core/perception/ActionGestureDetectorTest.kt`
- Create: `app/src/main/java/com/xingmou/core/perception/ActionGestureDetector.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
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
        List(33) { Point3D(0.5f, 0.5f) }.also {
            it[11] = Point3D(0.4f, shoulderY); it[12] = Point3D(0.6f, shoulderY)
            it[15] = Point3D(0.4f, lWristY); it[16] = Point3D(0.6f, rWristY)
        }

    // ---------- 拍手 ----------
    @Test
    fun `窗口内两次拍手命中`() {
        val d = ActionGestureDetector(ExpectedAction.CLAP)
        var t = 0L
        assertFalse(d.onFrame(frame(hand(0.42f, 0.4f), hand(0.58f, 0.4f)), t)) // 远
        t = 200; assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), t)) // 接触
        t = 400; assertFalse(d.onFrame(frame(hand(0.42f, 0.4f), hand(0.58f, 0.4f)), t)) // 分开=1次
        t = 600; assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), t))
        t = 800; assertTrue(d.onFrame(frame(hand(0.42f, 0.4f), hand(0.58f, 0.4f)), t))  // 第2次
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
        assertFalse(d.onFrame(frame(hand(0.42f, 0.85f), hand(0.58f, 0.85f)), t))
        t = 200; assertFalse(d.onFrame(frame(hand(0.47f, 0.85f), hand(0.53f, 0.85f)), t))
        t = 400; assertFalse(d.onFrame(frame(hand(0.42f, 0.85f), hand(0.58f, 0.85f)), t))
        t = 600; assertFalse(d.onFrame(frame(hand(0.47f, 0.85f), hand(0.53f, 0.85f)), t))
        t = 800; assertFalse(d.onFrame(frame(hand(0.42f, 0.85f), hand(0.58f, 0.85f)), t))
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
        assertFalse(d.onFrame(frame(hand(0.42f, 0.4f), hand(0.58f, 0.4f)), 0L))
        assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), 200L))
        assertFalse(d.onFrame(frame(hand(0.42f, 0.4f), hand(0.58f, 0.4f)), 400L)) // 1次
        d.reset()
        assertFalse(d.onFrame(frame(hand(0.47f, 0.4f), hand(0.53f, 0.4f)), 600L))    // 新计数
        assertFalse(d.onFrame(frame(hand(0.42f, 0.4f), hand(0.58f, 0.4f)), 800L))    // 仍只有1次
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.xingmou.core.perception.ActionGestureDetectorTest"`
Expected: 编译失败，`unresolved reference: ActionGestureDetector`。

- [ ] **Step 3: 实现**

```kotlin
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
```

注意：`HandGesture` 枚举已存在于 `core/perception`（`StateAnalyzer` 在用），直接引用，无需新建。

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.xingmou.core.perception.ActionGestureDetectorTest"`
Expected: 9 tests passed。若个别阈值用例失败，优先检查测试构造点的坐标，不放宽生产阈值。

- [ ] **Step 5: 提交**

```powershell
git add app/src/main/java/com/xingmou/core/perception/ActionGestureDetector.kt app/src/test/java/com/xingmou/core/perception/ActionGestureDetectorTest.kt
git commit -m "feat(child): 新增拍手/举手/静态手势时序检测器"
```

---

### Task 4: DetectSessionController 会话状态机（TDD）

**Files:**
- Create: `app/src/test/java/com/xingmou/core/perception/DetectSessionControllerTest.kt`
- Create: `app/src/main/java/com/xingmou/core/perception/DetectSessionController.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
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
}
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.xingmou.core.perception.DetectSessionControllerTest"`
Expected: 编译失败，unresolved reference。

- [ ] **Step 3: 实现**

```kotlin
package com.xingmou.core.perception

enum class DetectPhase { IDLE, AWAIT_PERMISSION, DETECTING, RETRYING, MANUAL_FALLBACK, HIT }

data class DetectSessionState(
    val questionId: String,
    val isCamera: Boolean,
    val phase: DetectPhase,
    val remainingMs: Long,
    val timeoutMs: Long
)

/**
 * 观察题自动检测会话状态机。纯 Kotlin，时间由调用方注入，便于单测。
 *
 * 生命周期：start → AWAIT_PERMISSION → onPermissionReady → begin →
 * DETECTING ──命中──▶ HIT(触发 onAutoAnswer)
 *           ──超时──▶ RETRYING(触发 onEncourage) ──再超时──▶ MANUAL_FALLBACK
 * MANUAL_FALLBACK 下检测器不停，仍可 onHit 自动作答。
 */
class DetectSessionController(private val timeoutMs: Long = 10_000L) {

    var session: DetectSessionState? = null
        private set

    var onAutoAnswer: ((String, String?) -> Unit)? = null
    var onEncourage: ((Boolean) -> Unit)? = null

    private var phaseStartMs = 0L

    @Synchronized
    fun start(questionId: String, isCamera: Boolean) {
        session = DetectSessionState(questionId, isCamera, DetectPhase.AWAIT_PERMISSION, timeoutMs, timeoutMs)
    }

    @Synchronized
    fun onPermissionReady(available: Boolean) {
        val s = session ?: return
        if (s.phase != DetectPhase.AWAIT_PERMISSION) return
        session = if (available) {
            s.copy(phase = DetectPhase.DETECTING, remainingMs = timeoutMs)
        } else {
            s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
        }
    }

    @Synchronized
    fun chooseManual() {
        val s = session ?: return
        if (s.phase == DetectPhase.AWAIT_PERMISSION) {
            session = s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
        }
    }

    @Synchronized
    fun onHit(meta: String?, nowMs: Long) {
        val s = session ?: return
        if (s.phase != DetectPhase.DETECTING &&
            s.phase != DetectPhase.RETRYING &&
            s.phase != DetectPhase.MANUAL_FALLBACK) return
        session = s.copy(phase = DetectPhase.HIT, remainingMs = 0L)
        onAutoAnswer?.invoke(s.questionId, meta)
    }

    /** 硬件不可用（模拟器 liteMode、识别服务缺失）时强制转手动兜底。 */
    @Synchronized
    fun forceManual() {
        val s = session ?: return
        if (s.phase == DetectPhase.DETECTING || s.phase == DetectPhase.RETRYING) {
            session = s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
        }
    }

    @Synchronized
    fun begin(nowMs: Long) {
        // ViewModel 在权限就绪并真正开始接收数据时调用，锚定计时起点
        phaseStartMs = nowMs
    }

    @Synchronized
    fun tick(nowMs: Long) {
        val s = session ?: return
        when (s.phase) {
            DetectPhase.DETECTING -> {
                val remaining = (timeoutMs - (nowMs - phaseStartMs)).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    phaseStartMs = nowMs
                    session = s.copy(phase = DetectPhase.RETRYING, remainingMs = timeoutMs)
                    onEncourage?.invoke(s.isCamera)
                } else {
                    session = s.copy(remainingMs = remaining)
                }
            }
            DetectPhase.RETRYING -> {
                val remaining = (timeoutMs - (nowMs - phaseStartMs)).coerceAtLeast(0L)
                if (remaining <= 0L) {
                    session = s.copy(phase = DetectPhase.MANUAL_FALLBACK, remainingMs = 0L)
                } else {
                    session = s.copy(remainingMs = remaining)
                }
            }
            else -> Unit
        }
    }

    @Synchronized
    fun stop() {
        session = null
    }
}
```

计时起点说明：`phaseStartMs` 默认为 0，因此测试里 `onPermissionReady(true)` 后直接 `tick(10_000)` 即超时；真机上 ViewModel 会在权限就绪、硬件开始接收数据时调用 `begin(now)` 锚定真实起点。**给测试类再追加两个用例**（同文件内）：

```kotlin
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
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.xingmou.core.perception.DetectSessionControllerTest"`
Expected: 12 tests passed。

- [ ] **Step 5: 提交**

```powershell
git add app/src/main/java/com/xingmou/core/perception/DetectSessionController.kt app/src/test/java/com/xingmou/core/perception/DetectSessionControllerTest.kt
git commit -m "feat(child): 新增观察题检测会话状态机"
```

---

### Task 5: SpeechAnswerDetector 语音识别封装

**Files:**
- Create: `app/src/main/java/com/xingmou/core/perception/SpeechAnswerDetector.kt`

这是 Android API 封装，无法 JVM 单测；Task 12 在真机/模拟器上验证（模拟器无服务时回调 onUnavailable）。

- [ ] **Step 1: 实现**

```kotlin
package com.xingmou.core.perception

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.xingmou.data.catalog.ExpectedSpeech
import java.util.Locale

/**
 * 系统语音识别薄封装。
 * onSpeech(text) 每段识别结果回调一次（含 partial）；
 * 由 ViewModel 结合 ExpectedSpeech 决定「开口即完成 / 关键词命中」。
 * onUnavailable() 表示设备无识别服务或发生不可恢复错误，调用方应转手动兜底。
 */
class SpeechAnswerDetector(
    private val context: Context,
    private val onSpeech: (String) -> Unit,
    private val onUnavailable: () -> Unit
) : RecognitionListener {

    private var recognizer: SpeechRecognizer? = null
    private var failed = false

    fun isAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun start() {
        if (!isAvailable()) {
            onUnavailable()
            return
        }
        failed = false
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { recognizer?.startListening(intent) }.onFailure {
            Log.w(TAG, "startListening failed", it); onUnavailable()
        }
    }

    fun stop() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    override fun onResults(results: Bundle?) = emit(results)
    override fun onPartialResults(partialResults: Bundle?) = emit(partialResults)

    private fun emit(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (text.isNotBlank()) onSpeech(text)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Unit // 继续听，直到题目超时
            else -> {
                if (!failed) {
                    failed = true
                    Log.w(TAG, "speech recognizer error=$error")
                    onUnavailable()
                }
            }
        }
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        private const val TAG = "SpeechAnswerDetector"

        /** ANY：有效文本 ≥2 字符即开口；KEYWORD：返回命中词，说了没命中返回空串标记。 */
        fun evaluate(text: String, expected: ExpectedSpeech): SpeechHit {
            val normalized = KeywordMatcher.normalize(text)
            if (normalized.length < 2) return SpeechHit.NONE
            return when (expected.mode) {
                com.xingmou.data.catalog.SpeechMode.ANY -> SpeechHit.SPOKEN(null)
                com.xingmou.data.catalog.SpeechMode.KEYWORD -> {
                    val kw = KeywordMatcher.matches(text, expected.keywords)
                    if (kw != null) SpeechHit.SPOKEN(kw) else SpeechHit.SPOKEN(null)
                }
            }
        }
    }
}

/** 语音判定结果。NONE=无效；SPOKEN(matchedKeyword) 开口即完成，meta 非 null 为命中目标词。 */
sealed class SpeechHit {
    data object NONE : SpeechHit()
    data class SPOKEN(val matchedKeyword: String?) : SpeechHit()
}
```

- [ ] **Step 2: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```powershell
git add app/src/main/java/com/xingmou/core/perception/SpeechAnswerDetector.kt
git commit -m "feat(child): 新增系统语音识别封装与开口/关键词判定"
```

---

### Task 6: Manifest 权限与服务可见性

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 在现有 CAMERA 权限旁新增 RECORD_AUDIO，并在 manifest 内新增 queries**

把：

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
```

改为：

```xml
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
```

在 `<manifest ...>` 下、`<application` 之前加入：

```xml
    <queries>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
    </queries>
```

- [ ] **Step 2: 编译 + 清单合并验证**

Run: `.\gradlew.bat :app:processDebugManifest`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```powershell
git add app/src/main/AndroidManifest.xml
git commit -m "feat(child): 声明麦克风权限与语音识别服务可见性"
```

---

### Task 7: AutoDetectState UI 模型 + PerceptionManager 暴露 liteMode

**Files:**
- Modify: `app/src/main/java/com/xingmou/UiModels.kt`（ChildUiState 约 118-175 行区域）
- Modify: `app/src/main/java/com/xingmou/core/perception/PerceptionManager.kt`

- [ ] **Step 1: UiModels 末尾或 ChildUiState 之前新增**

```kotlin
enum class DetectMode { NONE, CAMERA, SPEECH }
enum class DetectPhase { IDLE, AWAIT_PERMISSION, DETECTING, RETRYING, MANUAL_FALLBACK, HIT }

/** 观察题自动检测的界面状态；NONE 时题目走普通选项渲染。 */
data class AutoDetectState(
    val questionId: String = "",
    val mode: DetectMode = DetectMode.NONE,
    val phase: DetectPhase = DetectPhase.IDLE,
    val hint: String = "",
    val remainingMs: Long = 0L,
    val timeoutMs: Long = 10_000L,
    val permission: String = "",
    val matchedKeyword: String? = null
)
```

在 `data class ChildUiState(...)` 参数列表中（`perceptionPreview` 附近）新增：

```kotlin
    val autoDetect: AutoDetectState = AutoDetectState(),
```

- [ ] **Step 2: PerceptionManager 暴露精简模式**

在 `PerceptionManager` 中把 `private var liteMode = false` 改为：

```kotlin
    @Volatile var liteMode: Boolean = false
        private set
```

（`start()` 里失败分支已有 `liteMode = true`，保持不变。）

- [ ] **Step 3: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```powershell
git add app/src/main/java/com/xingmou/UiModels.kt app/src/main/java/com/xingmou/core/perception/PerceptionManager.kt
git commit -m "feat(child): 新增自动检测界面状态并暴露感知精简模式"
```

---

### Task 8: ViewModel 会话编排、摄像头引用计数、作答路由

**Files:**
- Modify: `app/src/main/java/com/xingmou/XingmouViewModel.kt`

这是最大的一块。全部新增逻辑为增量，不改现有作答/落库方法签名。

- [ ] **Step 1: 新增 import**

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.xingmou.AutoDetectState
import com.xingmou.DetectMode
import com.xingmou.DetectPhase
import com.xingmou.core.perception.ActionGestureDetector
import com.xingmou.core.perception.DetectSessionController
import com.xingmou.core.perception.SpeechAnswerDetector
import com.xingmou.core.perception.SpeechHit
import com.xingmou.data.catalog.ExpectedAction
import com.xingmou.data.catalog.ExpectedSpeech
import com.xingmou.data.catalog.QuestionDefinition
```

- [ ] **Step 2: 在现有 `private var perceptionManager` 等字段旁新增字段**

```kotlin
    // 观察题自动检测
    private val detectController = DetectSessionController()
    private var actionDetector: ActionGestureDetector? = null
    private var speechDetector: SpeechAnswerDetector? = null
    private var detectTickerJob: kotlinx.coroutines.Job? = null
    private var guardPerception = false
    private var questionCameraWanted = false
    private var savedPreviewEnabled: Boolean? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
```

- [ ] **Step 3: 重构感知开关为引用计数**

把现有 `togglePerception` / `startPerception()` / `stopPerception()` 中对 manager 的启停改为引用计数（保留 Feedback/SessionRecorder 仅属于守护开关）。整体替换 `startPerception()` 与 `stopPerception()` 两个私有方法为：

```kotlin
    private var guardInited = false

    private fun startPerception() {
        guardPerception = true
        if (!guardInited) {
            guardInited = true
            initGuardFeedback()
        }
        refreshPerception()
        _uiState.update { it.copy(child = it.child.copy(perceptionEnabled = true)) }
    }

    private fun stopPerception() {
        guardPerception = false
        feedbackController?.reset()
        refreshPerception()
        _uiState.update {
            it.copy(child = it.child.copy(
                perceptionEnabled = false,
                perceptionEmotion = "",
                perceptionFocus = "",
                perceptionFeedback = ""
            ))
        }
    }

    private fun initGuardFeedback() {
        val app = getApplication<Application>()
        feedbackController = FeedbackController { feedback ->
            _uiState.update { it.copy(child = it.child.copy(perceptionFeedback = feedback.message)) }
            if (feedback.level == FeedbackController.Feedback.Level.L3_PAUSE) {
                _uiState.update { it.copy(child = it.child.copy(isPaused = true)) }
            }
        }
        sessionRecorder = SessionRecorder(database.perceptionSessionDao(), childId)
    }

    /** 引用计数：守护开关或动作题任一方需要就启动，双方都不需要才停止。 */
    private fun refreshPerception() {
        val need = guardPerception || questionCameraWanted
        val app = getApplication<Application>()
        if (need && perceptionManager == null) {
            val mgr = PerceptionManager(app) { frame ->
                if (guardPerception) {
                    val state = StateAnalyzer.analyze(frame, lastMeltdown)
                    lastMeltdown = state.isMeltdown
                    sessionRecorder?.onState(state)
                    feedbackController?.onState(state)
                    _uiState.update {
                        it.copy(child = it.child.copy(
                            perceptionEmotion = state.emotion.name,
                            perceptionFocus = state.focusLevel.name
                        ))
                    }
                }
                if (questionCameraWanted) {
                    val detector = actionDetector
                    val qid = detectController.session?.questionId
                    if (detector != null && qid != null) {
                        val now = System.currentTimeMillis()
                        val hit = runCatching { detector.onFrame(frame, now) }.getOrDefault(false)
                        if (hit) mainHandler.post { detectController.onHit(null, now) }
                    }
                }
            }
            perceptionManager = mgr
            mgr.start()
        } else if (!need && perceptionManager != null) {
            perceptionManager?.stop()
            perceptionManager = null
        }
    }
```

注意：原来 `onCleared()` 里的 `stopPerception()` 保持不变（会把 guardPerception 置 false；另需在 onCleared 里补 `endDetectSession()`，见 Step 6）。`togglePerceptionPreview` 不改。

- [ ] **Step 4: 回调接线（控制器事件）——放在 init { } 块内、现有初始化代码之后**

```kotlin
        detectController.onAutoAnswer = { questionId, matchedKeyword ->
            autoAnswerDetected(questionId, matchedKeyword)
        }
        detectController.onEncourage = { isCamera ->
            val msg = if (isCamera) "再来一次，让小星看到你的小手～" else "没关系，再大声说一次～"
            _uiState.update { it.copy(child = it.child.copy(perceptionFeedback = msg)) }
        }
        viewModelScope.launch {
            _uiState.collect { snapshot ->
                syncDetectSession(snapshot)
            }
        }
```

- [ ] **Step 5: 新增会话编排方法（放在感知系统分区内）**

```kotlin
    // ---------- 观察题自动检测会话 ----------
    private fun currentDetectQuestion(snapshot: XingmouUiState): QuestionDefinition? {
        val baseline = snapshot.baseline
        if (baseline.isOpen && baseline.status == com.xingmou.core.domain.BaselineStatus.IN_PROGRESS) {
            baseline.question?.let { return it }
        }
        val child = snapshot.child
        val player = child.curriculumPlayer
        if (!child.isPaused && !child.isSafetyStopped && !player.finished && !player.isWorking) {
            player.question?.let { return it }
        }
        return null
    }

    private fun syncDetectSession(snapshot: XingmouUiState) {
        val q = currentDetectQuestion(snapshot)
        val isDetectable = q != null && (q.expectedAction != null || q.expectedSpeech != null)
        val activeId = detectController.session?.questionId
        when {
            !isDetectable -> {
                if (activeId != null) endDetectSession()
            }
            q!!.id != activeId -> beginDetectSession(q)
        }
    }

    private fun beginDetectSession(q: QuestionDefinition) {
        endDetectSession(restorePreview = false)
        val isCamera = q.expectedAction != null
        actionDetector = q.expectedAction?.let { ActionGestureDetector(it) }
        detectController.start(q.id, isCamera)
        pushDetectState(q, DetectPhase.AWAIT_PERMISSION)

        val permission = if (isCamera) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
        val granted = ContextCompat.checkSelfPermission(getApplication(), permission) ==
            PackageManager.PERMISSION_GRANTED

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val available = if (isCamera) {
                granted
            } else {
                granted && SpeechAnswerDetector(getApplication(), {}, {}).isAvailable()
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (detectController.session?.questionId != q.id) return@withContext
                detectController.onPermissionReady(available)
                if (available) startDetectionHardware(q) else pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
            }
        }
    }

    private fun startDetectionHardware(q: QuestionDefinition) {
        detectController.begin(System.currentTimeMillis())
        startDetectTicker()
        when {
            q.expectedAction != null -> {
                savedPreviewEnabled = savedPreviewEnabled ?: _uiState.value.child.perceptionPreview
                _uiState.update { it.copy(child = it.child.copy(perceptionPreview = true)) }
                questionCameraWanted = true
                refreshPerception()
                viewModelScope.launch {
                    delay(1_500)
                    if (perceptionManager?.liteMode == true &&
                        detectController.session?.questionId == q.id) {
                        forceManualFallback(q.id)
                    }
                }
            }
            q.expectedSpeech != null -> {
                val expected = q.expectedSpeech
                val detector = SpeechAnswerDetector(
                    getApplication(),
                    onSpeech = { text ->
                        if (detectController.session?.questionId != q.id) return@SpeechAnswerDetector
                        when (val hit = SpeechAnswerDetector.evaluate(text, expected)) {
                            is SpeechHit.SPOKEN -> detectController.onHit(hit.matchedKeyword, System.currentTimeMillis())
                            SpeechHit.NONE -> Unit
                        }
                    },
                    onUnavailable = { mainHandler.post { forceManualFallback(q.id) } }
                )
                speechDetector = detector
                detector.start()
            }
        }
        pushDetectState(q, DetectPhase.DETECTING)
    }

    private fun forceManualFallback(questionId: String) {
        if (detectController.session?.questionId != questionId) return
        // 状态机迁移到 MANUAL_FALLBACK；停止硬件但保留会话（ticker 下次刷新会把新相位推给 UI）
        detectController.forceManual()
        questionCameraWanted = false
        speechDetector?.stop(); speechDetector = null
        refreshPerception()
        val q = currentDetectQuestion(_uiState.value) ?: return
        pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
    }

    private fun startDetectTicker() {
        detectTickerJob?.cancel()
        detectTickerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100)
                detectController.tick(System.currentTimeMillis())
                val s = detectController.session ?: break
                _uiState.update { st ->
                    st.copy(child = st.child.copy(autoDetect = st.child.autoDetect.copy(
                        questionId = s.questionId,
                        mode = if (s.isCamera) DetectMode.CAMERA else DetectMode.SPEECH,
                        phase = s.phase,
                        remainingMs = s.remainingMs,
                        timeoutMs = s.timeoutMs
                    )))
                }
            }
        }
    }

    private fun autoAnswerDetected(questionId: String, matchedKeyword: String?) {
        val q = currentDetectQuestion(_uiState.value) ?: return
        if (q.id != questionId) return
        questionCameraWanted = false
        speechDetector?.stop(); speechDetector = null
        refreshPerception()
        detectTickerJob?.cancel()
        if (matchedKeyword != null) {
            _uiState.update { it.copy(child = it.child.copy(perceptionFeedback = "对啦，是$matchedKeyword！")) }
        }
        pushDetectState(q, DetectPhase.HIT)
        // 路由到现有作答通道，等价于自动点「自己完成」(option 0)
        val isBaseline = _uiState.value.baseline.question?.id == questionId
        if (isBaseline) answerBaseline(0) else answerCurriculumActivity(0)
    }

    private fun pushDetectState(q: QuestionDefinition, phase: DetectPhase) {
        val isCamera = q.expectedAction != null
        val hint = when (phase) {
            DetectPhase.AWAIT_PERMISSION -> if (isCamera) "小星想看看你的小手" else "小星想听听你的声音"
            DetectPhase.DETECTING, DetectPhase.HIT ->
                if (isCamera) "小星正在看你的小手～" else "小星在听哦，大声说出来～"
            DetectPhase.RETRYING ->
                if (isCamera) "再来一次，让小星看到你的小手～" else "没关系，再大声说一次～"
            DetectPhase.MANUAL_FALLBACK -> "没关系，也可以点这里告诉我"
            DetectPhase.IDLE -> ""
        }
        val permission = if (isCamera) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
        _uiState.update {
            it.copy(child = it.child.copy(autoDetect = AutoDetectState(
                questionId = q.id,
                mode = if (isCamera) DetectMode.CAMERA else DetectMode.SPEECH,
                phase = phase,
                hint = hint,
                remainingMs = detectController.session?.remainingMs ?: 0L,
                permission = permission,
                matchedKeyword = null
            )))
        }
    }

    private fun endDetectSession(restorePreview: Boolean = true) {
        detectTickerJob?.cancel()
        questionCameraWanted = false
        actionDetector = null
        speechDetector?.stop(); speechDetector = null
        refreshPerception()
        if (restorePreview) {
            savedPreviewEnabled?.let { saved ->
                _uiState.update { it.copy(child = it.child.copy(perceptionPreview = saved)) }
            }
        }
        savedPreviewEnabled = null
        detectController.stop()
        _uiState.update { it.copy(child = it.child.copy(autoDetect = AutoDetectState())) }
    }

    // 供 UI 调用
    fun onDetectPermissionResolved(granted: Boolean) {
        val q = currentDetectQuestion(_uiState.value) ?: return
        if (detectController.session?.questionId != q.id) return
        detectController.onPermissionReady(granted)
        if (granted) startDetectionHardware(q) else pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
    }

    fun onDetectChooseManual() {
        detectController.chooseManual()
        val q = currentDetectQuestion(_uiState.value) ?: return
        pushDetectState(q, DetectPhase.MANUAL_FALLBACK)
    }
```

补充 import：

```kotlin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
```

- [ ] **Step 6: onCleared 补一行**

在现有 `onCleared()` 的 `stopPerception()` 之后加 `endDetectSession()`。

- [ ] **Step 7: 编译并处理告警**

根状态类型已确认是 `XingmouUiState`（`XingmouViewModel.kt:157` `StateFlow<XingmouUiState>`），Step 4/5 的类型名直接可用。

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。若 `initGuardFeedback()` 里 `val app = getApplication<Application>()` 提示未使用变量（原内联实现里 app 有用，新方法体不再直接使用），删掉该局部变量行即可。

- [ ] **Step 8: 提交**

```powershell
git add app/src/main/java/com/xingmou/XingmouViewModel.kt
git commit -m "feat(child): 接入观察题检测会话、摄像头引用计数与自动作答路由"
```

---

### Task 9: QuestionPermissionGate 权限门 Composable

**Files:**
- Create: `app/src/main/java/com/xingmou/ui/child/QuestionPermissionGate.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.xingmou.ui.child

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 运行时权限说明卡：点「好呀」才拉起系统弹窗；系统不再给弹窗时改为「去设置打开」。
 * onResult(true) 表示已授权可开始检测；onManual 表示孩子选择手动作答。
 */
@androidx.compose.runtime.Composable
fun QuestionPermissionGate(
    permission: String,
    onResult: (Boolean) -> Unit,
    onManual: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun granted() = ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    var grantedState by remember { mutableStateOf(granted()) }
    var deniedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            grantedState = true
            onResult(true)
        } else {
            deniedOnce = true
            onResult(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && granted()) {
                grantedState = true
                onResult(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isCamera = permission == Manifest.permission.CAMERA
    val emoji = if (isCamera) "📷" else "🎤"
    val ask = if (isCamera) "小星想看看你的小手" else "小星想听听你的声音"
    // deniedOnce 且系统不再解释 → 只能去设置
    val mustOpenSettings = deniedOnce && !shouldShowRationale(context, permission)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(ask, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (grantedState) { onResult(true); return@Button }
                if (mustOpenSettings) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    launcher.launch(permission)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text(if (mustOpenSettings) "去设置打开" else "好呀") }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("先点按钮做")
        }
    }
}

private fun shouldShowRationale(
    context: android.content.Context,
    permission: String
): Boolean {
    val activity = context as? android.app.Activity ?: return false
    return activity.shouldShowRequestPermissionRationale(permission)
}
```

- [ ] **Step 2: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 提交**

```powershell
git add app/src/main/java/com/xingmou/ui/child/QuestionPermissionGate.kt
git commit -m "feat(child): 新增观察题运行时权限申请门"
```

---

### Task 10: 检测面板与题目卡接线

**Files:**
- Modify: `app/src/main/java/com/xingmou/ui/child/ChildScreen.kt`
- Modify: `app/src/main/java/com/xingmou/ui/XingmouApp.kt:736-763`

- [ ] **Step 1: ChildScreen 新增回调参数**

在 `ChildScreen(...)` 参数列表末尾（`modifier` 之前）加：

```kotlin
    onDetectPermissionResolved: (Boolean) -> Unit,
    onDetectChooseManual: () -> Unit,
```

- [ ] **Step 2: 新增 ObservedAnswerArea 与 DetectPanel（文件内 private composable）**

```kotlin
@androidx.compose.runtime.Composable
private fun ObservedAnswerArea(
    question: com.xingmou.data.catalog.QuestionDefinition,
    detect: AutoDetectState,
    isWorking: Boolean,
    onAnswer: (Int) -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    onManual: () -> Unit
) {
    val detectable = question.expectedAction != null || question.expectedSpeech != null
    val active = detectable && detect.questionId == question.id
    if (!active || detect.phase == DetectPhase.IDLE) {
        question.options.forEachIndexed { index, option ->
            OutlinedButton(
                onClick = { onAnswer(index) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .semantics { contentDescription = option }
            ) { Text(option) }
        }
        return
    }
    when (detect.phase) {
        DetectPhase.AWAIT_PERMISSION -> QuestionPermissionGate(
            permission = detect.permission,
            onResult = onPermissionResult,
            onManual = onManual
        )
        DetectPhase.DETECTING, DetectPhase.RETRYING, DetectPhase.HIT ->
            DetectPanel(detect)
        DetectPhase.MANUAL_FALLBACK, DetectPhase.IDLE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detect.hint, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            question.options.forEachIndexed { index, option ->
                OutlinedButton(
                    onClick = { onAnswer(index) },
                    enabled = !isWorking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .semantics { contentDescription = option }
                ) { Text(option) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DetectPanel(detect: AutoDetectState) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    val scale by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "scale"
    )
    val ringColor = when (detect.phase) {
        DetectPhase.HIT -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        DetectPhase.RETRYING -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.primary
    }
    val emoji = when {
        detect.phase == DetectPhase.HIT -> "✅"
        detect.mode == DetectMode.SPEECH -> "🎤"
        else -> "👏"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(132.dp)) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.25f),
                    radius = size.minDimension / 2f * if (detect.phase == DetectPhase.HIT) 1f else scale
                )
            }
            Text(emoji, fontSize = 56.sp)
        }
        Text(detect.hint, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detect.phase == DetectPhase.DETECTING || detect.phase == DetectPhase.RETRYING) {
            val progress = if (detect.timeoutMs > 0)
                detect.remainingMs.toFloat() / detect.timeoutMs else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        TextButton(onClick = { /* 手动入口由 onManual 提供，见调用处 */ }, enabled = false) {
            Text("")
        }
    }
}
```

补充 import（文件顶部）：

```kotlin
import com.xingmou.AutoDetectState
import com.xingmou.DetectPhase
import com.xingmou.DetectMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
```

（`Box`、`Arrangement`、`Column`、`TextButton`、`sp` 等文件中已 import，重复的删掉。）

把 `DetectPanel` 末尾那个空的 `TextButton` 整块删除（占位无作用）；「我想用手点」入口通过 `ObservedAnswerArea` 增加一个 `onManual` 文字按钮实现——在 DETECTING/RETRYING/HIT 分支的 `DetectPanel` 下方由调用卡片放置一个 `TextButton(onClick = onManual){ Text("我想用手点") }`。具体：在 `ObservedAnswerArea` 的 DETECTING 分支改为：

```kotlin
        DetectPhase.DETECTING, DetectPhase.RETRYING, DetectPhase.HIT -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            DetectPanel(detect)
            if (detect.phase != DetectPhase.HIT) {
                TextButton(onClick = onManual) { Text("我想用手点") }
            }
        }
```

- [ ] **Step 3: BaselineCard 接入**

给 `BaselineCard(...)` 增加参数 `autoDetect: AutoDetectState`、`onDetectPermissionResult: (Boolean) -> Unit`、`onDetectManual: () -> Unit`。把题目渲染处的：

```kotlin
                        question.options.forEachIndexed { index, option ->
                            OutlinedButton(
                                onClick = { onAnswer(index) },
```

开始的整个选项按钮块（约 877-890 行，到对应 `}` 结束）替换为：

```kotlin
                        ObservedAnswerArea(
                            question = question,
                            detect = autoDetect,
                            isWorking = state.isWorking,
                            onAnswer = onAnswer,
                            onPermissionResult = onDetectPermissionResult,
                            onManual = onDetectManual
                        )
```

- [ ] **Step 4: CurriculumPlayerCard 接入**

给 `CurriculumPlayerCard(...)` 增加同样三个参数。把选项渲染块（575-584 行的 `Column { question.options...chunked(2)... }`）整体替换为：

```kotlin
                ObservedAnswerArea(
                    question = question,
                    detect = autoDetect,
                    isWorking = player.isWorking,
                    onAnswer = onAnswer,
                    onPermissionResult = onDetectPermissionResult,
                    onManual = onDetectManual
                )
```

注意：普通选择题/记忆题在 `ObservedAnswerArea` 内会走「原按钮纵向排列」分支，课程卡原来是两列网格。为保持布局一致，把 `ObservedAnswerArea` 默认分支改为接收一个可选的 `normalContent: @Composable () -> Unit`：把函数签名改为

```kotlin
private fun ObservedAnswerArea(
    question: com.xingmou.data.catalog.QuestionDefinition,
    detect: AutoDetectState,
    isWorking: Boolean,
    onAnswer: (Int) -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    onManual: () -> Unit,
    normalContent: @androidx.compose.runtime.Composable () -> Unit
)
```

开头非检测分支改为直接 `normalContent()`；BaselineCard 传入原来的纵向 forEach 按钮块作为 `normalContent`，CurriculumPlayerCard 传入原来的两列网格块作为 `normalContent`。

- [ ] **Step 5: ChildScreen 内两个卡片调用处补参**

`BaselineCard(` 调用（搜索 `BaselineCard(`）补：

```kotlin
                autoDetect = state.autoDetect,
                onDetectPermissionResult = onDetectPermissionResolved,
                onDetectManual = onDetectChooseManual,
```

`CurriculumPlayerCard(`（约 314 行）补：

```kotlin
            autoDetect = state.autoDetect,
            onDetectPermissionResult = onDetectPermissionResolved,
            onDetectManual = onDetectChooseManual,
```

注意 BaselineCard 用的 `state` 是 BaselineUiState，autoDetect 在 ChildUiState 上——调用处外层有 `state: ChildUiState`，直接传 `state.autoDetect`。

- [ ] **Step 6: XingmouApp.kt 接线（736 行 ChildScreen 调用处）**

在 `onDetachPerceptionPreviewView = ...` 后面加：

```kotlin
            onDetectPermissionResolved = viewModel::onDetectPermissionResolved,
            onDetectChooseManual = viewModel::onDetectChooseManual,
```

- [ ] **Step 7: 编译**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL。逐个修正未解析引用（多为漏传参数）。

- [ ] **Step 8: 提交**

```powershell
git add app/src/main/java/com/xingmou/ui/child/ChildScreen.kt app/src/main/java/com/xingmou/ui/XingmouApp.kt
git commit -m "feat(child): 观察题渲染检测面板、权限门与手动兜底"
```

---

### Task 11: 题目打标与新增

**Files:**
- Modify: `app/src/main/java/com/xingmou/data/catalog/QuestionCatalog.kt`

先通读 `baselineQuestions` 与课程题列表，确认下列题号存在（BL-F-02、L01-01..03、L02-01..03、D03-01、D03-02 已确认存在）。

- [ ] **Step 1: 修改/打标现有题（替换整行 question(...) 调用）**

```kotlin
        question("BL-F-02", "baseline", "F", QuestionType.OBSERVED, "请拍拍小手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👏", expectedAction = ExpectedAction.CLAP),
```

```kotlin
        question("L01-01", "L01", "D", QuestionType.OBSERVED, "这是什么？说出它的名字", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🍎", expectedSpeech = ExpectedSpeech(SpeechMode.KEYWORD, listOf("苹果"))),
        question("L01-02", "L01", "D", QuestionType.OBSERVED, "这是什么？说出它的名字", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🐶", expectedSpeech = ExpectedSpeech(SpeechMode.KEYWORD, listOf("小狗", "狗狗"))),
        question("L01-03", "L01", "D", QuestionType.OBSERVED, "这是什么？说出它的名字", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🚗", expectedSpeech = ExpectedSpeech(SpeechMode.KEYWORD, listOf("汽车", "车车"))),
```

L02-01/02/03 三题题干改为「看图说一句话吧」（保持各自 stimulus），并加 `expectedSpeech = ExpectedSpeech(SpeechMode.ANY)`，例如：

```kotlin
        question("L02-01", "L02", "D", QuestionType.OBSERVED, "看图说一句话吧", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🐱 💤", expectedSpeech = ExpectedSpeech(SpeechMode.ANY)),
```

（L02-02、L02-03 同样改题干与标记，stimulus 维持原值。）

```kotlin
        question("D03-01", "D03", "F", QuestionType.OBSERVED, "跟着节奏拍拍手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👏  👏  👏", expectedAction = ExpectedAction.CLAP),
        question("D03-02", "D03", "F", QuestionType.OBSERVED, "小手举起来，再放下", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🙌  ⬇️", expectedAction = ExpectedAction.RAISE_HAND),
```

- [ ] **Step 2: 新增基线动作题**

在 `baselineQuestions` 列表末尾（列表闭合 `)` 之前）加：

```kotlin
        question("BL-G-01", "baseline", "G", QuestionType.OBSERVED, "请举起一只小手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🙌", expectedAction = ExpectedAction.RAISE_HAND),
        question("BL-G-02", "baseline", "G", QuestionType.OBSERVED, "请竖起大拇指", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👍", expectedAction = ExpectedAction.THUMB_UP)
```

在课程题列表（含 D03 的同一列表）末尾加：

```kotlin
        question("L03-01", "L03", "D", QuestionType.OBSERVED, "用手指一指", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "☝️", expectedAction = ExpectedAction.POINT_INDEX),
        question("L03-02", "L03", "D", QuestionType.OBSERVED, "比一个耶", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "✌️", expectedAction = ExpectedAction.VICTORY),
        question("L03-03", "L03", "D", QuestionType.OBSERVED, "张开小手掌", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🖐️", expectedAction = ExpectedAction.OPEN_PALM)
```

若课程题是分关卡组织（L03 关卡列表不存在），则把这三题放到 D03 同列表，id 改为 `D03-04/05/06`，moduleId 用 `"D03"`，不新建不存在的关卡引用。

- [ ] **Step 3: 确认基线抽题不会因题数变化崩溃**

搜索基线引擎如何取题（grep `baselineQuestions`），确认它按固定 6 题/模块抽取或随机截取；新增题目只扩大题库，若引擎写死下标范围则不动它，把 BL-G 两题仅作为题库扩充即可。

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: 提交**

```powershell
git add app/src/main/java/com/xingmou/data/catalog/QuestionCatalog.kt
git commit -m "feat(child): 观察题打标动作/语音检测并新增手势题"
```

---

### Task 12: 全量测试与模拟器冒烟

- [ ] **Step 1: 全部 JVM 单测**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: 全部通过（Smoke 1 + Keyword 6 + Action 9 + Session 12 = 28 个）。

- [ ] **Step 2: 构建装机**

```powershell
.\gradlew.bat :app:assembleDebug
adb -s emulator-5554 install -r "c:\Users\Zgl\Desktop\AI\xingmou-app\app\build\outputs\apk\debug\app-debug.apk"
```

- [ ] **Step 3: 普通题回归**

登录儿童 → 开始基线。普通选择题/记忆题渲染与作答与之前一致；答对/答错音效正常。

- [ ] **Step 4: 模拟器降级路径**

推进到 BL-F-02「请拍拍小手」：
- 先弹权限说明卡；授权 CAMERA 后进入检测面板（呼吸光圈+倒计时）。
- 模拟器 MediaPipe 若为 liteMode，约 1.5 秒后自动转手动兜底；否则 10 秒→鼓励→再 10 秒→手动三按钮。
- 全程不崩；手动三按钮作答结果与落库正常。
- 语音题（课程 L01）无识别服务时直接手动兜底，不报错。

- [ ] **Step 5: 真机验证（有真机时）**

- 拍手题：2.5 秒内拍两下自动判定；假动作不触发。
- 举手题：抬手保持 0.5 秒判定。
- 命名题：说「苹果」命中并听到「对啦，是苹果！」；发音模糊时开口也算完成。
- 权限三选项、拒绝→去设置→返回恢复。

- [ ] **Step 6: 收尾提交（如有调试残留）**

```powershell
git add -A
git commit -m "test(child): 完成观察题自动检测联调"
```

若工作区干净则跳过。

---

## 自查记录

- 设计覆盖：题库模型(T1)、两类检测器(T2/3/5)、状态机(T4)、权限(T6/T9)、UI 状态(T7)、VM 编排与引用计数(T8)、面板与接线(T10)、题目内容(T11)、测试(T0/12) 均有对应任务。
- 类型一致性：`ExpectedAction/ExpectedSpeech/SpeechMode`（T1）被 T3/T5/T8/T11 使用；`DetectSessionController/DetectPhase`（T4）被 T8 使用；`AutoDetectState/DetectMode/DetectPhase`（T7）被 T8/T10 使用。注意 UI 层 `DetectPhase`（UiModels）与感知层 `DetectPhase`（perception 包）同名——T8 的 import 已按需引入两者，T10 使用的是 `com.xingmou.DetectPhase`（UiModels 版）；若出现歧义，在 ChildScreen import 中移除 perception 包同名导入并使用全限定名。
- 已知需执行者现场核对点：T8 Step 7 的根 UiState 类型名；T11 Step 2 课程题列表结构；T10 Step 4 网格布局以 normalContent 方式保留。
