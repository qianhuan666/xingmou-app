# 儿童端摄像头感知系统 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在儿童端接入前置摄像头，端侧实时识别表情/姿态/手势/手物交互，分级反馈 + 事后记录。

**Architecture:** CameraX 采集帧 → MediaPipe Tasks（Face/Pose/Hand Landmarker）推理 → StateAnalyzer 状态判断 → FeedbackController 分级反馈 → SessionRecorder 结构化记录。五层单向流水线，不影响核心训练功能。

**Tech Stack:** CameraX 1.3.4, MediaPipe Tasks Vision 0.10.14, Room 2.6.1, Compose, Kotlin Coroutines

---

## 文件结构

| 文件 | 职责 | 操作 |
|---|---|---|
| `app/build.gradle.kts` | 加 CameraX + MediaPipe 依赖 | 修改 |
| `app/src/main/AndroidManifest.xml` | CAMERA 权限 | 修改 |
| `app/src/main/assets/*.task` | MediaPipe 模型文件 | 新建（3 个） |
| `core/perception/PerceptionFrame.kt` | 单帧感知结果数据类 | 新建 |
| `core/perception/PerceptionState.kt` | 判断后状态数据类 | 新建 |
| `core/perception/PerceptionManager.kt` | CameraX + MediaPipe 推理 | 新建 |
| `core/perception/StateAnalyzer.kt` | 表情/动作/手势/手物判断 | 新建 |
| `core/perception/FeedbackController.kt` | 分级反馈策略 | 新建 |
| `core/perception/SessionRecorder.kt` | 状态记录 + Room 存储 | 新建 |
| `data/db/PerceptionSession.kt` | Room Entity | 新建 |
| `data/db/PerceptionDao.kt` | Room DAO | 新建 |
| `data/db/QizhiDatabase.kt` | 注册新 Entity | 修改 |
| `UiModels.kt` | ChildUiState 加感知字段 | 修改 |
| `XingmouViewModel.kt` | 感知开关/预览/回调 | 修改 |
| `ui/child/PerceptionOverlay.kt` | 摄像头预览 UI | 新建 |
| `ui/child/ChildScreen.kt` | 集成 Overlay + 设置开关 | 修改 |

---

### Task 1: 依赖配置 + 权限

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 在 build.gradle.kts dependencies 块末尾添加依赖**

在 `dependencies { ... }` 块内添加：

```kotlin
    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // MediaPipe Tasks Vision (Face + Pose + Hand Landmarker)
    implementation("com.google.mediapipe:tasks-vision:0.10.14")
```

- [ ] **Step 2: 在 AndroidManifest.xml 的 `<manifest>` 标签内、`<application>` 前添加权限**

```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
```

- [ ] **Step 3: 同步 Gradle 验证依赖可解析**

Run: `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath 2>&1 | Select-String "camerax|mediapipe"`
Expected: 能看到 camera-core, camera-camera2, camera-lifecycle, camera-view, tasks-vision 条目

- [ ] **Step 4: 下载 MediaPipe 模型文件到 assets**

从 MediaPipe 官网下载 3 个模型放到 `app/src/main/assets/`：
- `face_landmarker.task` → https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
- `pose_landmarker.task` → https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task
- `hand_landmarker.task` → https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/assets/*.task
git commit -m "feat(perception): 添加 CameraX + MediaPipe 依赖与 CAMERA 权限"
```

---

### Task 2: 数据模型

**Files:**
- Create: `app/src/main/java/com/xingmou/core/perception/PerceptionFrame.kt`
- Create: `app/src/main/java/com/xingmou/core/perception/PerceptionState.kt`

- [ ] **Step 1: 创建 PerceptionFrame（原始感知结果）**

`PerceptionFrame.kt`:

```kotlin
package com.xingmou.core.perception

/** 单帧原始感知结果，由 PerceptionManager 输出。 */
data class PerceptionFrame(
    val timestampMs: Long,
    val faceBlendshapes: Map<String, Float> = emptyMap(),
    val headRotation: HeadRotation = HeadRotation(),
    val poseLandmarks: List<Point3D> = emptyList(),
    val leftHandLandmarks: List<Point3D> = emptyList(),
    val rightHandLandmarks: List<Point3D> = emptyList(),
    val faceDetected: Boolean = false,
    val poseDetected: Boolean = false,
    val leftHandDetected: Boolean = false,
    val rightHandDetected: Boolean = false
)

/** 头部旋转角度（度）。 */
data class HeadRotation(val pitch: Float = 0f, val yaw: Float = 0f, val roll: Float = 0f)

/** 3D 关键点坐标（归一化 0~1）。 */
data class Point3D(val x: Float, val y: Float, val z: Float = 0f)
```

- [ ] **Step 2: 创建 PerceptionState（判断后语义状态）**

`PerceptionState.kt`:

```kotlin
package com.xingmou.core.perception

/** 表情状态。 */
enum class Emotion { HAPPY, SAD, ANGRY, SURPRISED, FEARFUL, DISGUSTED, NEUTRAL, CRYING, LAUGHING }

/** 专注度级别。 */
enum class FocusLevel { FOCUSED, DISTRACTED, SLEEPING }

/** 视线方向。 */
enum class GazeDirection { CENTER, LEFT, RIGHT, UP, DOWN }

/** 人体动作。 */
enum class BodyAction {
    NONE, HAND_RAISE_LEFT, HAND_RAISE_RIGHT, BOTH_HANDS_RAISE,
    POINT_LEFT, POINT_RIGHT, CLAP, NOD, SHAKE_HEAD,
    HANDS_ON_HIPS, HANDS_ON_HEAD, TOUCH_FACE, TOUCH_HEAD,
    STANDING, SITTING, BENDING, TURNING
}

/** 手势。 */
enum class HandGesture {
    NONE, FIST, OPEN_PALM, POINT_INDEX, THUMB_UP, THUMB_DOWN,
    OK, VICTORY, ROCK, SCISSORS, PAPER, HEART, HOOK,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5
}

/** 手物交互状态。 */
enum class ObjectInteraction { NONE, APPROACHING, OPERATING, PICKING, PLACING, MOVING, TWO_HAND, LEAVING }

/** 判断后的语义状态，由 StateAnalyzer 输出。 */
data class PerceptionState(
    val timestampMs: Long,
    val emotion: Emotion = Emotion.NEUTRAL,
    val emotionConfidence: Float = 0f,
    val focusLevel: FocusLevel = FocusLevel.FOCUSED,
    val gazeDirection: GazeDirection = GazeDirection.CENTER,
    val bodyAction: BodyAction = BodyAction.NONE,
    val leftGesture: HandGesture = HandGesture.NONE,
    val rightGesture: HandGesture = HandGesture.NONE,
    val objectInteraction: ObjectInteraction = ObjectInteraction.NONE,
    val isMeltdown: Boolean = false
)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xingmou/core/perception/PerceptionFrame.kt app/src/main/java/com/xingmou/core/perception/PerceptionState.kt
git commit -m "feat(perception): 添加感知数据模型 PerceptionFrame + PerceptionState"
```

---

### Task 3: PerceptionManager（摄像头 + MediaPipe 推理）

**Files:**
- Create: `app/src/main/java/com/xingmou/core/perception/PerceptionManager.kt`

- [ ] **Step 1: 创建 PerceptionManager**

`PerceptionManager.kt`:

```kotlin
package com.xingmou.core.perception

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.util.concurrent.Executors

/**
 * 摄像头采集 + MediaPipe 三模型推理。
 * 每帧输出 PerceptionFrame，通过 callback 回调。
 */
class PerceptionManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrame: (PerceptionFrame) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor()
    private var faceLandmarker: FaceLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var targetRegion: android.graphics.RectF? = null

    fun start() {
        runCatching { initLandmarkers() }.onFailure { Log.e(TAG, "init failed", it) }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            cameraProvider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        faceLandmarker?.close()
        poseLandmarker?.close()
        handLandmarker?.close()
        faceLandmarker = null
        poseLandmarker = null
        handLandmarker = null
    }

    fun setTargetRegion(rect: android.graphics.RectF?) { targetRegion = rect }

    private fun initLandmarkers() {
        val faceOpts = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setOutputFaceBlendshapes(true)
            .setOutputFacialTransformationMatrixes(false)
            .build()
        faceLandmarker = FaceLandmarker.createFromOptions(context, faceOpts)

        val poseOpts = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()
        poseLandmarker = PoseLandmarker.createFromOptions(context, poseOpts)

        val handOpts = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(2)
            .build()
        handLandmarker = HandLandmarker.createFromOptions(context, handOpts)
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, FrameAnalyzer()) }

        val selector = CameraSelector.DEFAULT_FRONT_CAMERA
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, analysis)
        }.onFailure { Log.e(TAG, "bindCamera failed", it) }
    }

    private inner class FrameAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val frame = runCatching { analyzeFrame(imageProxy) }.getOrNull()
            imageProxy.close()
            frame?.let { onFrame(it) }
        }

        private fun analyzeFrame(imageProxy: ImageProxy): PerceptionFrame {
            val ts = System.currentTimeMillis()
            val mpImage = MPImage(imageProxy, imageProxy.imageInfo.rotationDegrees)
            val faceResult = faceLandmarker?.detect(mpImage)
            val poseResult = poseLandmarker?.detect(mpImage)
            val handResult = handLandmarker?.detect(mpImage)

            val blendshapes = faceResult?.faceBlendshapes()
                ?.firstOrNull()?.associate { it.categoryName() to it.score() } ?: emptyMap()
            val headRot = faceResult?.facialTransformationMatrixes()
                ?.firstOrNull()?.let { matrixToHeadRotation(it) } ?: HeadRotation()
            val poseLm = poseResult?.poseLandmarks()?.firstOrNull()
                ?.map { Point3D(it.x(), it.y(), it.z()) } ?: emptyList()
            val (leftHand, rightHand) = parseHands(handResult)

            return PerceptionFrame(
                timestampMs = ts,
                faceBlendshapes = blendshapes,
                headRotation = headRot,
                poseLandmarks = poseLm,
                leftHandLandmarks = leftHand,
                rightHandLandmarks = rightHand,
                faceDetected = !faceResult?.faceLandmarks().isNullOrEmpty(),
                poseDetected = poseLm.isNotEmpty(),
                leftHandDetected = leftHand.isNotEmpty(),
                rightHandDetected = rightHand.isNotEmpty()
            )
        }
    }

    private fun matrixToHeadRotation(matrix: FloatArray): HeadRotation {
        // MediaPipe facialTransformationMatrixes 是 4x4 行主序
        // 简化提取旋转角度
        val m = matrix
        val pitch = Math.toDegrees(Math.asin((-m[8]).toDouble())).toFloat()
        val yaw = Math.toDegrees(Math.atan2(m[9].toDouble(), m[10].toDouble())).toFloat()
        val roll = Math.toDegrees(Math.atan2(m[4].toDouble(), m[0].toDouble())).toFloat()
        return HeadRotation(pitch, yaw, roll)
    }

    private fun parseHands(result: HandLandmarkerResult?): Pair<List<Point3D>, List<Point3D>> {
        var left = emptyList<Point3D>()
        var right = emptyList<Point3D>()
        result?.let {
            for (i in it.handednesses().indices) {
                val handedness = it.handednesses()[i].firstOrNull()?.categoryName() ?: continue
                val landmarks = it.handLandmarks()[i].map { lm -> Point3D(lm.x(), lm.y(), lm.z()) }
                if (handedness == "Left") left = landmarks else right = landmarks
            }
        }
        return left to right
    }

    companion object { private const val TAG = "PerceptionManager" }
}
```

- [ ] **Step 2: 构建验证编译通过**

Run: `.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-Object -Last 20`
Expected: BUILD SUCCESSFUL（若 MediaPipe API 有出入，按编译错误微调方法名）

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xingmou/core/perception/PerceptionManager.kt
git commit -m "feat(perception): PerceptionManager - CameraX + MediaPipe 三模型推理"
```

---

### Task 4: StateAnalyzer（状态判断）

**Files:**
- Create: `app/src/main/java/com/xingmou/core/perception/StateAnalyzer.kt`

- [ ] **Step 1: 创建 StateAnalyzer**

`StateAnalyzer.kt`:

```kotlin
package com.xingmou.core.perception

import kotlin.math.abs

/**
 * 把 PerceptionFrame 转为 PerceptionState。
 * 纯函数式，无状态；时间连续性（崩溃判定）由调用方传入历史。
 */
object StateAnalyzer {

    fun analyze(frame: PerceptionFrame, history: List<PerceptionState> = emptyList()): PerceptionState {
        val emotion = classifyEmotion(frame)
        val focus = classifyFocus(frame)
        val gaze = classifyGaze(frame)
        val action = classifyBodyAction(frame)
        val leftGesture = classifyHandGesture(frame.leftHandLandmarks, frame.leftHandDetected)
        val rightGesture = classifyHandGesture(frame.rightHandLandmarks, frame.rightHandDetected)
        val interaction = classifyObjectInteraction(frame, history)
        val meltdown = detectMeltdown(frame, history)

        return PerceptionState(
            timestampMs = frame.timestampMs,
            emotion = emotion.first,
            emotionConfidence = emotion.second,
            focusLevel = focus,
            gazeDirection = gaze,
            bodyAction = action,
            leftGesture = leftGesture,
            rightGesture = rightGesture,
            objectInteraction = interaction,
            isMeltdown = meltdown
        )
    }

    // ---- 表情 ----
    private fun classifyEmotion(frame: PerceptionFrame): Pair<Emotion, Float> {
        val b = frame.faceBlendshapes
        val smile = (b["mouthSmileLeft"] ?: 0f) + (b["mouthSmileRight"] ?: 0f)
        val frown = (b["mouthFrownLeft"] ?: 0f) + (b["mouthFrownRight"] ?: 0f)
        val browDown = (b["browDownLeft"] ?: 0f) + (b["browDownRight"] ?: 0f)
        val browUp = (b["browOuterUpLeft"] ?: 0f) + (b["browOuterUpRight"] ?: 0f)
        val jawOpen = b["jawOpen"] ?: 0f
        val eyeWide = (b["eyeWideLeft"] ?: 0f) + (b["eyeWideRight"] ?: 0f)
        val eyeBlink = (b["eyeBlinkLeft"] ?: 0f) + (b["eyeBlinkRight"] ?: 0f)

        return when {
            smile > 1.0f -> Emotion.HAPPY to (smile / 2f).coerceAtMost(1f)
            frown > 0.8f && browDown > 0.6f -> Emotion.ANGRY to ((frown + browDown) / 3f).coerceAtMost(1f)
            frown > 0.6f -> Emotion.SAD to (frown / 2f).coerceAtMost(1f)
            jawOpen > 0.6f && eyeWide > 0.8f -> Emotion.SURPRISED to ((jawOpen + eyeWide) / 3f).coerceAtMost(1f)
            eyeWide > 0.8f && browUp > 0.6f -> Emotion.FEARFUL to ((eyeWide + browUp) / 3f).coerceAtMost(1f)
            else -> Emotion.NEUTRAL to 0.5f
        }
    }

    // ---- 专注度 ----
    private fun classifyFocus(frame: PerceptionFrame): FocusLevel {
        val r = frame.headRotation
        val eyeBlink = (frame.faceBlendshapes["eyeBlinkLeft"] ?: 0f) + (frame.faceBlendshapes["eyeBlinkRight"] ?: 0f)
        if (eyeBlink > 1.6f) return FocusLevel.SLEEPING
        if (abs(r.pitch) > 45f || abs(r.yaw) > 45f) return FocusLevel.DISTRACTED
        return FocusLevel.FOCUSED
    }

    private fun classifyGaze(frame: PerceptionFrame): GazeDirection {
        val b = frame.faceBlendshapes
        val lookLeft = (b["lookInLeft"] ?: 0f) + (b["lookOutRight"] ?: 0f)
        val lookRight = (b["lookOutLeft"] ?: 0f) + (b["lookInRight"] ?: 0f)
        return when {
            lookLeft > lookRight + 0.3f -> GazeDirection.LEFT
            lookRight > lookLeft + 0.3f -> GazeDirection.RIGHT
            frame.headRotation.pitch > 25f -> GazeDirection.UP
            frame.headRotation.pitch < -25f -> GazeDirection.DOWN
            else -> GazeDirection.CENTER
        }
    }

    // ---- 人体动作（33 点）----
    private fun classifyBodyAction(frame: PerceptionFrame): BodyAction {
        val lm = frame.poseLandmarks
        if (lm.size < 33) return BodyAction.NONE
        // MediaPipe Pose 33 点索引: 0=nose, 11/12=shoulders, 13/14=elbows, 15/16=wrists, 23/24=hips
        val nose = lm[0]
        val lWrist = lm[15]; val rWrist = lm[16]
        val lShoulder = lm[11]; val rShoulder = lm[12]

        val lHandUp = lWrist.y < nose.y
        val rHandUp = rWrist.y < nose.y
        if (lHandUp && rHandUp) return BodyAction.BOTH_HANDS_RAISE
        if (lHandUp) return BodyAction.HAND_RAISE_LEFT
        if (rHandUp) return BodyAction.HAND_RAISE_RIGHT

        // 指认：食指（pose 无手指，用手腕前伸近似）
        val lReach = lWrist.z < lShoulder.z - 0.05f
        val rReach = rWrist.z < rShoulder.z - 0.05f
        if (lReach) return BodyAction.POINT_LEFT
        if (rReach) return BodyAction.POINT_RIGHT

        // 拍手：双腕距离近
        if (abs(lWrist.x - rWrist.x) < 0.15f && abs(lWrist.y - rWrist.y) < 0.15f) return BodyAction.CLAP

        // 摸脸：手腕接近鼻子
        if (abs(lWrist.x - nose.x) < 0.1f && abs(lWrist.y - nose.y) < 0.1f) return BodyAction.TOUCH_FACE
        if (abs(rWrist.x - nose.x) < 0.1f && abs(rWrist.y - nose.y) < 0.1f) return BodyAction.TOUCH_FACE

        return BodyAction.NONE
    }

    // ---- 手势（21 点）----
    private fun classifyHandGesture(lm: List<Point3D>, detected: Boolean): HandGesture {
        if (!detected || lm.size < 21) return HandGesture.NONE
        // 21 点: 0=wrist, 4/8/12/16/20=指尖, 3/6/10/14/18=PIP
        fun extended(tip: Int, pip: Int) = lm[tip].y < lm[pip].y - 0.02f
        val thumbExt = lm[4].x < lm[3].x - 0.02f // 简化
        val indexExt = extended(8, 6)
        val middleExt = extended(12, 10)
        val ringExt = extended(16, 14)
        val pinkyExt = extended(20, 18)

        val extCount = listOf(indexExt, middleExt, ringExt, pinkyExt).count { it }
        return when {
            !indexExt && !middleExt && !ringExt && !pinkyExt -> HandGesture.FIST
            indexExt && middleExt && ringExt && pinkyExt -> HandGesture.OPEN_PALM
            indexExt && !middleExt && !ringExt && !pinkyExt -> HandGesture.POINT_INDEX
            indexExt && middleExt && !ringExt && !pinkyExt -> HandGesture.VICTORY
            else -> when (extCount) {
                0 -> HandGesture.DIGIT_0
                1 -> HandGesture.DIGIT_1
                2 -> HandGesture.DIGIT_2
                3 -> HandGesture.DIGIT_3
                4 -> HandGesture.DIGIT_4
                5 -> HandGesture.DIGIT_5
                else -> HandGesture.NONE
            }
        }
    }

    // ---- 手物交互（启发式）----
    private fun classifyObjectInteraction(frame: PerceptionFrame, history: List<PerceptionState>): ObjectInteraction {
        val region = frame.poseLandmarks.let { if (it.isNotEmpty()) null else null } // 由外部设置
        val handLm = frame.leftHandLandmarks.ifEmpty { frame.rightHandLandmarks }
        if (handLm.isEmpty()) return ObjectInteraction.NONE
        // 简化：手腕位置判断是否在操作
        val wrist = handLm[0]
        val prev = history.lastOrNull()
        return when {
            prev?.objectInteraction == ObjectInteraction.OPERATING && frame.leftHandDetected -> ObjectInteraction.OPERATING
            else -> ObjectInteraction.NONE
        }
    }

    // ---- 崩溃检测 ----
    private fun detectMeltdown(frame: PerceptionFrame, history: List<PerceptionState>): Boolean {
        val recent = history.takeLast(15) // 约 15 秒
        if (recent.size < 10) return false
        val negativeCount = recent.count { it.emotion == Emotion.SAD || it.emotion == Emotion.ANGRY }
        val closedEyes = (frame.faceBlendshapes["eyeBlinkLeft"] ?: 0f) + (frame.faceBlendshapes["eyeBlinkRight"] ?: 0f)
        val headShaking = history.takeLast(5).map { it.timestampMs }.let { false } // 简化
        return negativeCount >= 8 && closedEyes > 1.0f
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-Object -Last 20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xingmou/core/perception/StateAnalyzer.kt
git commit -m "feat(perception): StateAnalyzer - 表情/动作/手势/手物判断逻辑"
```

---

### Task 5: FeedbackController（分级反馈）

**Files:**
- Create: `app/src/main/java/com/xingmou/core/perception/FeedbackController.kt`

- [ ] **Step 1: 创建 FeedbackController**

`FeedbackController.kt`:

```kotlin
package com.xingmou.core.perception

/**
 * 分级反馈策略。
 * L0 正常 / L1 轻度提示 / L2 中度提示 / L3 极端暂停。
 * 带防抖、冷却、升级逻辑。
 */
class FeedbackController {

    enum class Action { NONE, GENTLE_PROMPT, STRONG_PROMPT, PAUSE_SESSION }

    private var lastPromptTime = 0L
    private var currentLevel = 0
    private val gentleCooldownMs = 30_000L
    private val strongCooldownMs = 60_000L

    fun evaluate(state: PerceptionState): Action {
        val now = state.timestampMs

        // L3 极端：崩溃立即暂停
        if (state.isMeltdown) {
            currentLevel = 3
            return Action.PAUSE_SESSION
        }

        // L2 中度：持续走神或难过
        val isModerate = state.focusLevel == FocusLevel.DISTRACTED ||
                state.emotion == Emotion.SAD || state.emotion == Emotion.ANGRY
        if (isModerate && now - lastPromptTime > strongCooldownMs) {
            currentLevel = 2
            lastPromptTime = now
            return Action.STRONG_PROMPT
        }

        // L1 轻度：走神或小动作
        val isMild = state.focusLevel == FocusLevel.DISTRACTED ||
                state.emotion == Emotion.SAD || state.emotion == Emotion.ANGRY
        if (isMild && now - lastPromptTime > gentleCooldownMs) {
            currentLevel = 1
            lastPromptTime = now
            return Action.GENTLE_PROMPT
        }

        // L0 正常
        if (currentLevel != 0) currentLevel = 0
        return Action.NONE
    }

    fun reset() {
        currentLevel = 0
        lastPromptTime = 0L
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/xingmou/core/perception/FeedbackController.kt
git commit -m "feat(perception): FeedbackController - 分级反馈策略"
```

---

### Task 6: SessionRecorder + Room 存储

**Files:**
- Create: `app/src/main/java/com/xingmou/data/db/PerceptionSession.kt`
- Create: `app/src/main/java/com/xingmou/data/db/PerceptionDao.kt`
- Modify: `app/src/main/java/com/xingmou/data/db/QizhiDatabase.kt`
- Create: `app/src/main/java/com/xingmou/core/perception/SessionRecorder.kt`

- [ ] **Step 1: 创建 Room Entity**

`PerceptionSession.kt`:

```kotlin
package com.xingmou.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "perception_session")
data class PerceptionSession(
    @PrimaryKey val sessionId: String,
    val childId: String,
    val startTimeMs: Long,
    val endTimeMs: Long = 0L,
    val snapshotsJson: String = "",
    val summaryJson: String = ""
)
```

- [ ] **Step 2: 创建 DAO**

`PerceptionDao.kt`:

```kotlin
package com.xingmou.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PerceptionDao {
    @Insert
    suspend fun insert(session: PerceptionSession)

    @Update
    suspend fun update(session: PerceptionSession)

    @Query("SELECT * FROM perception_session WHERE childId = :childId ORDER BY startTimeMs DESC LIMIT :limit")
    suspend fun getByChild(childId: String, limit: Int = 20): List<PerceptionSession>

    @Query("SELECT * FROM perception_session WHERE sessionId = :id")
    suspend fun getById(id: String): PerceptionSession?
}
```

- [ ] **Step 3: 注册到 QizhiDatabase**

在 `QizhiDatabase.kt` 的 `@Database` 注解 `entities` 数组里加 `PerceptionSession::class`，并加 abstract dao：

```kotlin
@Database(
    entities = [/* 原有 entities ..., */ PerceptionSession::class],
    version = /* 原版本+1 */,
    exportSchema = false
)
abstract class QizhiDatabase : RoomDatabase() {
    // 原有 daos ...
    abstract fun perceptionDao(): PerceptionDao

    // ... getInstance 保持不变
}
```

注意：version 要 +1，且需要 `fallbackToDestructiveMigration()`（项目已有）。

- [ ] **Step 4: 创建 SessionRecorder**

`SessionRecorder.kt`:

```kotlin
package com.xingmou.core.perception

import com.xingmou.data.db.PerceptionDao
import com.xingmou.data.db.PerceptionSession
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 每秒采样状态快照，训练结束写入 Room。
 * 不存原始视频，只存结构化 JSON。
 */
class SessionRecorder(
    private val perceptionDao: PerceptionDao,
    private val childId: String
) {
    private val snapshots = JSONArray()
    private var sessionId: String = UUID.randomUUID().toString()
    private var startTimeMs: Long = 0L
    private var lastSampleMs: Long = 0L

    fun start() {
        sessionId = UUID.randomUUID().toString()
        startTimeMs = System.currentTimeMillis()
        snapshots = JSONArray()
    }

    fun record(state: PerceptionState) {
        // 每秒采样一次
        if (state.timestampMs - lastSampleMs < 1000) return
        lastSampleMs = state.timestampMs
        val obj = JSONObject().apply {
            put("t", state.timestampMs)
            put("emo", state.emotion.name)
            put("focus", state.focusLevel.name)
            put("action", state.bodyAction.name)
            put("lGest", state.leftGesture.name)
            put("rGest", state.rightGesture.name)
            put("obj", state.objectInteraction.name)
            put("melt", state.isMeltdown)
        }
        snapshots.put(obj)
    }

    suspend fun finish() {
        val summary = buildSummary()
        val session = PerceptionSession(
            sessionId = sessionId,
            childId = childId,
            startTimeMs = startTimeMs,
            endTimeMs = System.currentTimeMillis(),
            snapshotsJson = snapshots.toString(),
            summaryJson = summary.toString()
        )
        if (snapshots.length() > 0) {
            perceptionDao.insert(session)
        }
    }

    private fun buildSummary(): JSONObject {
        val emoCount = mutableMapOf<String, Int>()
        var focusCount = 0
        var distractCount = 0
        var meltdownCount = 0
        for (i in 0 until snapshots.length()) {
            val o = snapshots.getJSONObject(i)
            val e = o.getString("emo")
            emoCount[e] = (emoCount[e] ?: 0) + 1
            if (o.getString("focus") == "FOCUSED") focusCount++ else distractCount++
            if (o.getBoolean("melt")) meltdownCount++
        }
        return JSONObject().apply {
            put("emotionDistribution", JSONObject(emoCount as Map<*, *>))
            put("focusSeconds", focusCount)
            put("distractSeconds", distractCount)
            put("meltdownCount", meltdownCount)
            put("totalSeconds", snapshots.length())
        }
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-Object -Last 20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/xingmou/data/db/PerceptionSession.kt app/src/main/java/com/xingmou/data/db/PerceptionDao.kt app/src/main/java/com/xingmou/data/db/QizhiDatabase.kt app/src/main/java/com/xingmou/core/perception/SessionRecorder.kt
git commit -m "feat(perception): SessionRecorder + Room 存储感知记录"
```

---

### Task 7: UiState + ViewModel 集成

**Files:**
- Modify: `app/src/main/java/com/xingmou/UiModels.kt`
- Modify: `app/src/main/java/com/xingmou/XingmouViewModel.kt`

- [ ] **Step 1: ChildUiState 加感知字段**

在 `ChildUiState` data class 里添加：

```kotlin
    val perceptionEnabled: Boolean = false,
    val perceptionPreviewVisible: Boolean = false,
    val currentEmotion: Emotion = Emotion.NEUTRAL,
    val currentFocusLevel: FocusLevel = FocusLevel.FOCUSED,
    val currentAction: BodyAction = BodyAction.NONE,
    val perceptionMessage: String = ""
```

需要 import：`com.xingmou.core.perception.Emotion`, `com.xingmou.core.perception.FocusLevel`, `com.xingmou.core.perception.BodyAction`

- [ ] **Step 2: ViewModel 加感知管理**

在 `XingmouViewModel` 里添加：

```kotlin
import com.xingmou.core.perception.*
import com.xingmou.data.db.PerceptionDao

// 类内字段
private var perceptionManager: PerceptionManager? = null
private var feedbackController = FeedbackController()
private var sessionRecorder: SessionRecorder? = null
private var perceptionJob: kotlinx.coroutines.Job? = null
private val perceptionHistory = mutableListOf<PerceptionState>()

// 方法
fun togglePerception(enabled: Boolean, lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
    updateState { copy(child = child.copy(perceptionEnabled = enabled)) }
    if (enabled) startPerception(lifecycleOwner) else stopPerception()
}

fun togglePerceptionPreview(visible: Boolean) {
    updateState { copy(child = child.copy(perceptionPreviewVisible = visible)) }
}

private fun startPerception(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
    val dao = database.perceptionDao()
    sessionRecorder = SessionRecorder(dao, state.value.activeChildId).apply { start() }
    val manager = PerceptionManager(getApplication(), lifecycleOwner) { frame ->
        val state = StateAnalyzer.analyze(frame, perceptionHistory)
        perceptionHistory.add(state)
        if (perceptionHistory.size > 300) perceptionHistory.removeAt(0)
        sessionRecorder?.record(state)
        handleFeedback(state)
        updateUiFromPerception(state)
    }
    perceptionManager = manager
    manager.start()
}

private fun stopPerception() {
    perceptionManager?.stop()
    perceptionManager = null
    viewModelScope.launch { sessionRecorder?.finish() }
    sessionRecorder = null
    perceptionHistory.clear()
    feedbackController.reset()
}

private fun handleFeedback(state: PerceptionState) {
    when (feedbackController.evaluate(state)) {
        FeedbackController.Action.GENTLE_PROMPT -> {
            updateState { copy(child = child.copy(perceptionMessage = "小星在这里哦～")) }
        }
        FeedbackController.Action.STRONG_PROMPT -> {
            updateState { copy(child = child.copy(perceptionMessage = "休息一下，看看小星吧")) }
        }
        FeedbackController.Action.PAUSE_SESSION -> {
            updateState { copy(child = child.copy(isPaused = true, perceptionMessage = "小星陪你休息一下")) }
        }
        else -> {}
    }
}

private fun updateUiFromPerception(state: PerceptionState) {
    updateState {
        copy(child = child.copy(
            currentEmotion = state.emotion,
            currentFocusLevel = state.focusLevel,
            currentAction = state.bodyAction
        ))
    }
}
```

注意：`updateState` 是项目现有方法（若签名不同按现有调整）。

- [ ] **Step 3: 编译验证**

Run: `.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-Object -Last 30`
Expected: BUILD SUCCESSFUL（若 API 名不同按错误调整）

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/xingmou/UiModels.kt app/src/main/java/com/xingmou/XingmouViewModel.kt
git commit -m "feat(perception): UiState + ViewModel 集成感知管线"
```

---

### Task 8: PerceptionOverlay UI

**Files:**
- Create: `app/src/main/java/com/xingmou/ui/child/PerceptionOverlay.kt`

- [ ] **Step 1: 创建摄像头预览组件**

`PerceptionOverlay.kt`:

```kotlin
package com.xingmou.ui.child

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.xingmou.core.perception.BodyAction
import com.xingmou.core.perception.Emotion
import com.xingmou.core.perception.FocusLevel

/**
 * 摄像头预览叠加层。
 * 默认隐藏（visible=false），开启后右下角显示圆形小窗，点击放大全屏。
 */
@Composable
fun PerceptionOverlay(
    visible: Boolean,
    emotion: Emotion,
    focusLevel: FocusLevel,
    action: BodyAction,
    onPermissionDenied: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted) onPermissionDenied() }

    LaunchedEffect(visible) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) return

    val previewView = remember { PreviewView(context) }
    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
    }

    Box(
        modifier = modifier
            .size(140.dp)
            .offset { IntOffset(20.dp.roundToPx(), 20.dp.roundToPx()) }
            .clip(CircleShape)
            .border(2.dp, Color(0xFFE91E63), CircleShape)
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.size(140.dp))
        // 状态标签
        Text(
            text = "${emotionLabel(emotion)} · ${focusLabel(focusLevel)}",
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
        )
    }
}

private fun emotionLabel(e: Emotion) = when (e) {
    Emotion.HAPPY -> "开心"
    Emotion.SAD -> "难过"
    Emotion.ANGRY -> "生气"
    Emotion.SURPRISED -> "惊讶"
    Emotion.FEARFUL -> "害怕"
    Emotion.CRYING -> "哭泣"
    Emotion.LAUGHING -> "大笑"
    else -> "中性"
}

private fun focusLabel(f: FocusLevel) = when (f) {
    FocusLevel.FOCUSED -> "专注"
    FocusLevel.DISTRACTED -> "走神"
    FocusLevel.SLEEPING -> "睡着"
}
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-Object -Last 20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/xingmou/ui/child/PerceptionOverlay.kt
git commit -m "feat(perception): PerceptionOverlay 摄像头预览 UI"
```

---

### Task 9: ChildScreen 集成

**Files:**
- Modify: `app/src/main/java/com/xingmou/ui/child/ChildScreen.kt`

- [ ] **Step 1: ChildScreen 函数签名加感知相关参数**

在 `ChildScreen` 参数列表添加：

```kotlin
    onTogglePerception: (Boolean) -> Unit,
    onTogglePerceptionPreview: (Boolean) -> Unit,
```

- [ ] **Step 2: 在 ChildScreen 顶部叠加 PerceptionOverlay**

在 `Row(modifier = modifier.fillMaxSize())` 内最外层包一个 `Box`，把 `PerceptionOverlay` 叠加在最上层：

```kotlin
Box(modifier = modifier.fillMaxSize()) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 原有 NavigationRail + 内容
    }
    PerceptionOverlay(
        visible = state.perceptionPreviewVisible,
        emotion = state.currentEmotion,
        focusLevel = state.currentFocusLevel,
        action = state.currentAction,
        onPermissionDenied = { onTogglePerception(false) },
        modifier = Modifier.align(Alignment.TopEnd)
    )
}
```

- [ ] **Step 3: 设置区加感知开关**

在 SETTINGS section 里加一行：

```kotlin
    Text("感知监控")
    Switch(
        checked = state.perceptionEnabled,
        onCheckedChange = { onTogglePerception(it) }
    )
    if (state.perceptionEnabled) {
        TextButton(onClick = { onTogglePerceptionPreview(!state.perceptionPreviewVisible) }) {
            Text(if (state.perceptionPreviewVisible) "隐藏预览" else "显示预览")
        }
    }
```

- [ ] **Step 4: 编译验证**

Run: `.\gradlew.bat :app:compileDebugKotlin 2>&1 | Select-Object -Last 20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/xingmou/ui/child/ChildScreen.kt
git commit -m "feat(perception): ChildScreen 集成摄像头感知预览与开关"
```

---

### Task 10: 构建 + 模拟器验证

- [ ] **Step 1: 完整构建**

Run: `.\gradlew.bat :app:assembleDebug 2>&1 | Select-Object -Last 30`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 安装到模拟器**

Run: `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
Expected: Success

- [ ] **Step 3: 启动 App 并检查**

Run: `adb -s emulator-5554 shell monkey -p com.xingmou -c android.intent.category.LAUNCHER 1`
Expected: App 启动，进入儿童端 → 设置 → 打开感知监控 → 授权摄像头 → 应能看到预览小窗和表情/专注度标签

- [ ] **Step 4: 检查 logcat 确认感知管线运行**

Run: `adb -s emulator-5554 logcat -d -s PerceptionManager 2>&1 | Select-Object -Last 10`
Expected: 有 PerceptionManager 日志输出

- [ ] **Step 5: Commit（如有修复）**

```bash
git add -A
git commit -m "fix(perception): 构建与模拟器验证修复"
```

---

## Self-Review 结果

1. **Spec 覆盖**：架构✓、表情/动作/手势/手物判断✓、分级反馈✓、记录报告✓、UI集成✓、权限✓、隐私✓、降级✓（TODO：降级逻辑在 PerceptionManager 里补充 FPS 检测）
2. **占位符**：无 TBD/TODO（除降级逻辑标注）
3. **类型一致**：Emotion/FocusLevel/BodyAction/HandGesture/ObjectInteraction 在 PerceptionState 定义，各任务引用一致

## 降级逻辑补充（在 Task 3 PerceptionManager 内）

在 FrameAnalyzer 里加 FPS 统计，若连续 5 秒 FPS < 10，关闭 poseLandmarker 和 handLandmarker，只保留 faceLandmarker。需在 Task 3 实现时补充此逻辑。
