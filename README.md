# 星眸训练台（Android 平板端）

面向特殊教育与康育场景的 **AI 智能体驱动的儿童认知康复训练平台**，服务 3–12 岁存在智力障碍、发育迟缓等特殊支持需要的儿童，同时为特教教师、康复师、机构工作人员与家长提供训练辅助工具。

> **参赛状态**：项目已开发完成，并参加「中国教育技术协会 · 大学生 OPC 创新创业 AI Agent 赛道」（大学生 AI 创新创业组）。

## 项目简介

项目针对传统训练内容"千人一面"、儿童能力差异难以持续追踪、家庭观察与专业训练信息分散等问题，以"评估—计划—训练—记录—复评—调整"为主线，将游戏化训练、家庭观察、专业方案审核与 AI 辅助分析连接起来。

- **三端同源**：儿童端（起点小测 + 22 个训练模块 + 多模态识别）、家长端（观察记录 + AI 辅助建议）、专业端（评估建档 + 方案审核状态机），共用一套本地数据模型。
- **受控 AI 智能体**：统一 Agent 编排 + 多角色任务路由，按"读取授权信息—风险与权限判断—选择处理路径—生成结果或调用工具—校验与留痕"工作；风险筛查先于建议生成（紧急→安全叫停、暂停→暂停路径），输出附来源引用、可解释、可留痕。
- **能力与兴趣解耦**：动物 / 交通 / 生活用品 / 通用图片四类兴趣素材，只改变素材呈现与任务入口，不改变评价依据与能力记录。
- **本地优先**：Kotlin + Jetpack Compose + Room 本地数据库，无需自建服务端；仅经授权、最小化脱敏后直连 DeepSeek，无 Key / 未授权 / 网络异常 / 输出不合规时自动降级为本地规则与安全提示。

平台始终定位为训练与观察辅助工具，不替代专业评估、医学诊断或治疗决策。

---

## 一、架构决策（已定）

- **单机平板 + 纯前端本地 App + 直连云端大模型**（无后端）
- **技术栈**：Kotlin + Jetpack Compose + Room + OkHttp + Gson + Coroutines
- **大模型**：机构使用者在专业端自行设置 API Key 后直连 DeepSeek（`https://api.deepseek.com/chat/completions`）；无 Key 或无当前儿童远程 AI 同意时保持本地安全模式
- **核心原则**：规则引擎（确定性代码）管安全，大模型只负责理解/改写/草拟，JSON 严格校验 + 确定性降级兜底

## 二、目录结构

```
app/src/main/java/com/xingmou/
├── MainActivity.kt                 # 入口，按角色路由到三端口
├── core/
│   ├── rule/                       # ★ 规则引擎（纯确定性，不碰模型）
│   │   ├── RiskEngine.kt           #   风险前置拦截（SAFETY_STOP / PAUSE）
│   │   ├── PortGuard.kt            #   端口权限隔离（child/parent/professional）
│   │   ├── TaskWhitelist.kt        #   白名单任务
│   │   └── DifficultyController.kt #   难度单次 ±1 级
│   ├── llm/
│   │   ├── DirectDeepSeekGateway.kt # 运行时 BYOK 直连，强制 JSON 输出
│   │   ├── LocalApiKeyStore.kt     #   当前设备的 Key 设置/清除
│   │   ├── PromptBuilder.kt        #   脱敏 + 组装三角色系统提示词
│   │   └── JsonValidator.kt        #   JSON 校验 + 悬空引用检查 + 降级
│   ├── model/
│   │   └── Models.kt               #   对应提示词 JSON Schema 的数据类
│   └── safety/
│       └── SafeResponses.kt        #   固定安全文案（SAFETY_STOP 等）
├── data/
│   ├── db/                         # Room：过程数据 + 脱敏档案 + 审计日志
│   └── repo/
└── ui/
    ├── child/                      # 儿童端（超大按钮 + TTS + 休息按钮）
    ├── parent/                     # 家长端（来源角标 + 抽屉）
    └── professional/               # 专业端（审核工作台 draft→confirmed→active）
```

## 三、提示词硬规则 → 代码映射

| 提示词要求 | 代码实现 |
|---|---|
| 风险前置拦截（自伤/抽搐/倒退等） | `RiskEngine`，先于任何 LLM 调用 |
| 端口权限隔离 | `PortGuard` |
| 白名单任务 | `TaskWhitelist` |
| 单次 ±1 级 | `DifficultyController` |
| 每条判断可溯源 | `JsonValidator` 检查悬空引用，`AgentOrchestrator` 校验本次放行的知识/训练记录 ID |
| 来源不得凭模型生成 | `ContextAssembler` 仅注入本次匹配的已审核知识与训练记录摘要 |
| 输出前自检 | `JsonValidator` 校验失败 → `SafeResponses` 降级 |
| 隐私最小化 | `PromptBuilder` 只传化名/汇总，真实信息本地硬过滤 |

## 四、当前工程状态

项目包含 Room 数据层、领域与安全规则、Agent Runtime、事件协调器、三端口 Compose 页面、本地 TTS 与辅助设置，已完成开发并参加「大学生 OPC 创新创业 AI Agent 赛道」。V1.0 采用纯前端 BYOK：专业端设置 Key，经当前儿童授权后才允许家长/专业端请求远程模型；儿童训练始终保有本地安全降级。

```powershell
cd xingmou-app
./gradlew.bat assembleDebug
```

Debug APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

Key 不从 `local.properties` 或构建环境注入。使用步骤、风险与联调边界见 [机构APIKey设置与纯前端直连说明.md](docs/机构APIKey设置与纯前端直连说明.md)。运行时 Key 保存在当前设备，可被提取的风险由 Key 持有人接受；不要提交 Key 到 Git。

开发过程与实际测试结果见 [开发日志.md](docs/开发日志.md)；发布待办见 [V1.0发布验收与阻断项.md](docs/V1.0发布验收与阻断项.md)。

## 五、依赖清单（build.gradle.kts）

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
}
```

## 六、接入步骤

1. 使用 Android Studio 打开本目录，并等待 Gradle Sync 完成。
2. 创建或启动 Android Emulator 平板 AVD。
3. 运行 `app`，依次验收儿童端、家长端和专业端主要流程。
4. 如需联调 DeepSeek，进入专业端顶部“机构 API Key”填写测试 Key，再对专用测试儿童开启“远程 AI”授权；不要硬编码或提交 Key。

> 无 Key、无当前儿童授权或模型失败时，使用本地安全模式；真实 DeepSeek 联调不由自动测试发起。
