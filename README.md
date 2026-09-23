# 星眸训练台 —— 安卓平板端

面向**有智力障碍或特殊支持需要儿童**的 AI 交互式认知训练平台（安卓平板端）。

## 一、架构决策（已定）

- **单机平板 + 纯前端本地 App + 直连云端大模型**（无后端）
- **技术栈**：Kotlin + Jetpack Compose + Room + OkHttp + Gson + Coroutines
- **大模型**：DeepSeek（`https://api.deepseek.com/chat/completions`，OpenAI 兼容格式，支持 `response_format: json_object`）
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
│   │   ├── DeepSeekClient.kt       #   直连 DeepSeek，强制 JSON 输出
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
| 每条判断可溯源 | `Claim.sourceIds` → `JsonValidator` 悬空引用校验 |
| 来源不得凭模型生成 | `PromptBuilder` 只注入 `approved_knowledge` 元数据 |
| 输出前自检 | `JsonValidator` 校验失败 → `SafeResponses` 降级 |
| 隐私最小化 | `PromptBuilder` 只传化名/汇总，真实信息本地硬过滤 |

## 四、当前工程状态

阶段 4 三端口工作台已完成：项目现在包含可编译的 Gradle Android 工程、Room 数据层、领域与安全规则、Agent Runtime、事件协调器，以及儿童端、家长端、专业端正式 Compose 页面。

```powershell
cd xingmou-app
./gradlew.bat assembleDebug
```

Debug APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

本机联调 DeepSeek 时，可在不会提交 Git 的 `local.properties` 中增加：

```properties
DEEPSEEK_API_KEY=你的本机开发密钥
```

未配置时 `DeepSeekClientFactory.createOrNull()` 返回 `null`，应用应使用 Mock 或确定性降级，不会自动发起网络模型请求。生产发布仍建议通过服务端代理或更强的密钥保护方案，避免将长期密钥直接打包进 APK。

阶段 4 已完成：三端口页面通过 `XingmouViewModel` 接入 Room、`AgentEventCoordinator` 和 `AgentOrchestrator`。儿童端支持单步训练、主动休息、连续失败暂停和安全停止；家长端支持观察输入、风险路由、已审核知识检索和来源展示；专业端支持过程分析、Agent 审计信息和 `draft → confirmed → active` 两步审核。当前自动化验收为 28 项单元测试通过、Debug APK 构建通过。尚待完成：真实 DeepSeek Key 联调和 Android 模拟器人工验收。开发过程记录见：[开发日志.md](docs/开发日志.md)。

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
4. 联调 DeepSeek 时通过本机 `local.properties` 注入开发密钥，**不要硬编码或提交密钥**。

> 说明：当前 `XingmouViewModel` 默认使用本地安全 ModelGateway 验证 Agent 闭环；即使本机配置了 DeepSeek Key，也不会在未明确切换网关前自动发送训练数据到外部服务。
