# 观察题自动检测设计：摄像头动作识别 + 麦克风语音识别

日期：2026-10-04
分支：Zang

## 1. 背景与目标

基线小测与课程关卡中的 OBSERVED（观察）题目前完全依靠儿童手动点按「自己完成 / 帮助后完成 / 还没完成」，例如「跟着做：拍手」「说出它的名字」。本设计让部分观察题通过设备传感能力自动判定：

- **动作题**（拍手、举手、指物、点赞、耶、张开手）：复用已接入的 CameraX + MediaPipe 手部/姿态关键点，新增时序动作检测。
- **语音题**（说出名字、看图说话）：新增 Android `SpeechRecognizer` 语音识别，开口即完成，命中目标词额外鼓励。

设计原则：

1. **自动判定 + 手动兜底**：检测命中自动作答；检测不到、权限拒绝、能力不可用时始终保留现有手动三按钮，流程永不卡死。
2. **因人而异**：语音题只要认真开口即算完成，不因发音不准、方言惩罚儿童；命中关键词仅给额外鼓励。
3. **按需申请权限**：进入检测题才申请对应权限；系统授权弹窗（仅限这一次 / 使用应用时允许 / 不允许）由系统呈现，App 不重复弹。
4. **普通题零影响**：CHOICE / MEMORY 等题型与现有作答、落库链路完全不变。
5. **检测逻辑可单测**：纯 Kotlin 检测器不依赖 Android API，阈值可调、可用合成关键点序列测试。

## 2. 题库与数据模型

文件：`app/src/main/java/com/xingmou/data/catalog/QuestionCatalog.kt`

新增枚举：

```kotlin
enum class ExpectedAction { CLAP, RAISE_HAND, POINT_INDEX, THUMB_UP, VICTORY, OPEN_PALM }

enum class SpeechMode { KEYWORD, ANY }

data class ExpectedSpeech(
    val mode: SpeechMode,
    val keywords: List<String> = emptyList()   // 模糊匹配，含同义词/儿语
)
```

`QuestionDefinition` 新增两个可空字段：

```kotlin
val expectedAction: ExpectedAction? = null
val expectedSpeech: ExpectedSpeech? = null
```

语义：

- 两者均为 null → 维持现状（普通题，或无法自动检测的复合动作/社交互动题）。
- `expectedAction != null` → 摄像头检测通道。
- `expectedSpeech != null` → 麦克风检测通道。
- 一题只使用一个通道。

复合动作（如「拍手、举手、拍手」）第一版不自动检测，保持手动。

### 题目清单（第一版）

| 题号 | 题干 | 标记 |
|---|---|---|
| BL-F-02（改） | 请拍拍小手 | CLAP |
| BL-G-01（新增） | 请举起一只小手 | RAISE_HAND |
| BL-G-02（新增） | 请竖起大拇指 | THUMB_UP |
| BL-F-04 | 拍手、举手（复合） | 保持手动 |
| L01-01 | 这是什么？说出它的名字 🍎 | KEYWORD：苹果 |
| L01-02 | 🐶 | KEYWORD：小狗、狗狗 |
| L01-03 | 🚗 | KEYWORD：汽车、车车 |
| L02-01/02/03 | 看图说一句话吧 | ANY |
| L03-01（新增） | 用手指一指 | POINT_INDEX |
| L03-02（新增） | 比一个耶 | VICTORY |
| L03-03（新增） | 张开小手掌 | OPEN_PALM |
| D03-01（改） | 跟着节奏拍拍手 | CLAP |
| D03-02（改） | 小手举起来，再放下 | RAISE_HAND |
| D03-03、S02-01/02/03 | 复合/社交互动 | 保持手动 |

判题语义不变：检测命中 = 自动选择「自己完成」(option 0)，走现有作答通道；`QuestionEvaluator` 不改动。

## 3. 检测器

### 3.1 ActionGestureDetector（纯 Kotlin，可单测）

文件：`app/src/main/java/com/xingmou/core/perception/ActionGestureDetector.kt`

```kotlin
class ActionGestureDetector(private val expected: ExpectedAction) {
    fun onFrame(frame: PerceptionFrame, nowMs: Long): Boolean
    fun reset()
}
```

坐标约定：归一化坐标，y 轴向下，前置画面已镜像（现有 `PerceptionManager` 已处理）。手部 21 点：0=手腕，9=中指根，掌心取 0/5/9/13/17 五点均值。姿态 33 点：11/12 左右肩，15/16 左右腕。

规则（阈值抽为文件顶部常量，初值如下，真机实测后调整）：

| 动作 | 规则 | 防抖 |
|---|---|---|
| CLAP | 双手均检出；两掌心距离完成「远(>0.18) → 接触(<0.09) → 远(>0.15)」为 1 次；2500ms 窗口内 ≥2 次；接触点 y<0.7 | 两次接触间隔 120~1500ms |
| RAISE_HAND | 任一手腕 y < 同侧肩 y − 0.05；姿态丢失时退回「手腕 y<0.35 且手部检出」 | 连续满足 500ms |
| POINT_INDEX / THUMB_UP / VICTORY / OPEN_PALM | 复用 `StateAnalyzer.analyzeGesture()` 单帧结果 | 连续满足 400ms |

### 3.2 SpeechAnswerDetector（Android API 薄封装）

文件：`app/src/main/java/com/xingmou/core/perception/SpeechAnswerDetector.kt`

- 系统 `SpeechRecognizer` + `RecognizerIntent`，语言 `zh-CN`，开 `EXTRA_PARTIAL_RESULTS`。
- 启动前探测识别服务可用性；不可用（模拟器常见）→ 回调「不可用」，ViewModel 转手动兜底。
- ANY：出现非空识别文本且有效内容 ≥2 字符 → 命中。
- KEYWORD：文本经 `KeywordMatcher` 归一化（去空格标点、转小写）后与关键词表包含匹配；命中 → 完成并回调匹配词；说了但未命中 → 仍完成，无额外鼓励。
- `ERROR_NO_MATCH` / `ERROR_SPEECH_TIMEOUT` → 继续听到超时；其他 error → 不可用转手动。
- 换题/离开/暂停 → `stop()` + `destroy()` 释放麦克风。

### 3.3 KeywordMatcher（纯 Kotlin，可单测）

文件：`app/src/main/java/com/xingmou/core/perception/KeywordMatcher.kt`

```kotlin
object KeywordMatcher {
    fun matches(text: String, keywords: List<String>): String?  // 返回命中的关键词，未命中 null
}
```

### 3.4 命中回调

检测器不依赖 Compose / ViewModel，命中通过回调上抛：

```kotlin
fun interface DetectorHitListener { fun onHit(meta: String? = null) }
```

## 4. 检测会话状态机（ViewModel）

文件：`XingmouViewModel.kt`

随题目 ID 创建/重建一次检测会话：

```
IDLE ──进入检测题──▶ AWAIT_PERMISSION ──授权──▶ DETECTING ──命中──▶ HIT（自动作答 option 0，停检测）
                        │                          │
                   拒绝/不可用               10s 未命中 │ 再 10s 未命中
                        ▼                          ▼
                  MANUAL_FALLBACK ◀──── RETRYING（TTS 鼓励、计时清零）
```

- DETECTING 命中 → 基线 `answerBaseline(0)` / 课程 `answerCurriculumActivity(0)`，播答对音效，停检测。
- 首次 10 秒未命中 → RETRYING，排队 TTS 鼓励（动作题「再来一次，让小星看到你的小手～」/ 语音题「没关系，再大声说一次～」）；再 10 秒未命中 → MANUAL_FALLBACK。
- MANUAL_FALLBACK 下检测器在授权有效时继续运行，自动判定与手动按钮谁先到算谁。
- 暂停 / 暂时离开 / 换题 → 结束会话，释放资源。
- 语音命中关键词 → 作答之外 `speakQueued("对啦，是XX！")`，不打断题目朗读。
- 会话回调以 questionId 校验，防止旧题回调串到新题。

### UI 状态（UiModels.kt）

```kotlin
enum class DetectMode { NONE, CAMERA, SPEECH }
enum class DetectPhase { IDLE, AWAIT_PERMISSION, DETECTING, RETRYING, MANUAL_FALLBACK, HIT }

data class AutoDetectState(
    val questionId: String = "",
    val mode: DetectMode = DetectMode.NONE,
    val phase: DetectPhase = DetectPhase.IDLE,
    val hint: String = "",
    val remainingMs: Long = 0L,
    val matchedKeyword: String? = null
)
```

挂在 ChildUiState 上，UI 只渲染状态。

### 摄像头引用计数

两个使用方：`perceptionGuard`（家长感知守护开关）与 `questionDetect`（动作题会话）。

- 任一方需要 → 确保 `PerceptionManager` 启动并复用同一实例、同一帧流。
- 两方都关闭 → `stop()`。
- 帧回调同时喂给现有 `StateAnalyzer`（情感/专注）和当前题目的 `ActionGestureDetector`。

## 5. 权限

文件：`AndroidManifest.xml`

- 新增 `android.permission.RECORD_AUDIO`（CAMERA 已声明）。
- 新增 `<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`（Android 11+ 识别服务可见性）。
- 不声明摄像头/麦克风 `uses-feature` 为必需，无相关硬件的设备仍可安装，检测题降级手动。

UI：新增 `app/src/main/java/com/xingmou/ui/child/QuestionPermissionGate.kt`，封装权限检查、说明卡、系统申请 launcher、去设置跳转、`ON_RESUME` 复检，对外只暴露 `granted: Boolean`。

行为（targetSdk=36）：

1. 进入检测题：已授予（含「仅限这一次」会话内）→ 静默开检测；未授予 → 显示友好说明卡（📷「小星想看看你的小手」/🎤「小星想听听你的声音」），点「好呀」才拉起系统弹窗（Android 11+ 原生三选项：仅限这一次 / 使用应用时允许 / 不允许；Android 8-10 为允许/拒绝）。
2. 选「不允许」→ 温和提示 + 手动三按钮；「再试一次」才重新拉起系统弹窗。
3. 系统不再给弹窗（曾拒绝且 `shouldShowRequestPermissionRationale=false`）→ 「好呀」变为「去设置打开」，跳 `ACTION_APPLICATION_DETAILS_SETTINGS`，ON_RESUME 复检并恢复。
4. 「仅限这一次」会话内体验与永久授权一致；系统在后台约 1 分钟后收回是平台行为，下次做题时由说明卡承接，App 不自行重复弹系统窗。
5. 语音识别服务不可用按「未授权」同一路径处理（直接手动兜底，不报错）。

## 6. 儿童端 UI

文件：`ChildScreen.kt`（沉浸天空空间内）

- **AWAIT_PERMISSION**：居中说明卡（emoji + 一句话 + 「好呀」/「先点按钮做」）。
- **DETECTING**：大字号题干 + 柔和提示（动作「小星正在看你的小手～」/ 语音「小星在听哦，大声说出来～」）；呼吸光圈（Compose `infiniteTransition`，无素材）；底部线性倒计时（10 秒，不显示刺眼数字）；左下角低调文字按钮「我想用手点」。
- **RETRYING**：光圈淡黄 + 鼓励语，倒计时重置。
- **MANUAL_FALLBACK**：现有三个按钮，样式不变。
- **HIT**：0.4 秒绿勾反馈 + 答对音效（关键词题追加 TTS），随后自动进下一题。
- 动作题会话期间自动打开现有左上角摄像头预览浮窗，会话结束恢复家长侧预览开关；语音题显示小 🎤 动画点，不开摄像头。

题目朗读沿用现有 TTS 逻辑，题干即朗读文本。

## 7. 错误处理与降级

- MediaPipe 初始化失败 / liteMode（无手势关键点）→ 动作题直接 MANUAL_FALLBACK。
- 无语音识别服务 / 识别 error（除 NO_MATCH、TIMEOUT）→ 语音题直接 MANUAL_FALLBACK。
- 权限拒绝 / 永久拒绝 → 手动按钮 + 去设置引导。
- 模拟器：本功能全程不崩、不卡流程。

## 8. 测试计划

JVM 单元测试：

- `ActionGestureDetectorTest`：标准拍手、只合不分、腿上碰手、举手经过、举手保持、各静态手势保持/抖动、reset 清空。
- `KeywordMatcherTest`：归一化、同义词、儿语、说了但未命中、空文本/噪音。
- 检测会话状态机：命中自动作答、10s 重试、再 10s 手动、手动兜底期间自动命中、换题旧会话失效。

真机手测：

- 前置摄像头拍手/举手各 10 次命中率与误触率。
- 权限三选项路径；拒绝 → 去设置 → 返回恢复。
- 语音 KEYWORD / ANY 两种模式。

模拟器回归：

- 无识别服务 / liteMode 下全部手动兜底，流程不崩。
- CHOICE / MEMORY 题、感知守护开关、手动三按钮判题与落库结果与现状一致。

## 9. 改动范围

新增：

- `core/perception/ActionGestureDetector.kt`
- `core/perception/SpeechAnswerDetector.kt`
- `core/perception/KeywordMatcher.kt`
- `ui/child/QuestionPermissionGate.kt`
- `AutoDetectState` 等 UI 模型（UiModels.kt）
- 对应单元测试

修改：

- `data/catalog/QuestionCatalog.kt`（枚举、字段、题目打标与新增）
- `XingmouViewModel.kt`（检测会话、摄像头引用计数）
- `ui/child/ChildScreen.kt`（检测卡片与状态渲染）
- `AndroidManifest.xml`（RECORD_AUDIO、queries）

不改动：

- `QuestionEvaluator`、作答与落库链路、普通题 UI。
