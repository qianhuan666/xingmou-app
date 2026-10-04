package com.xingmou.data.catalog

/** 统一题目模型，基线和课程都通过同一份结构渲染。 */
enum class QuestionType { CHOICE, MEMORY, SEQUENCE, SORTING, AUDIO, OBSERVED }

/** 摄像头动作题期望的动作；null 表示非动作题。 */
enum class ExpectedAction { CLAP, RAISE_HAND, POINT_INDEX, THUMB_UP, VICTORY, OPEN_PALM }

/** 语音题检测方式：KEYWORD=说出目标词；ANY=只要有效开口。 */
enum class SpeechMode { KEYWORD, ANY }

/** 语音题的期望；keywords 为模糊匹配词表（含同义词/儿语）。 */
data class ExpectedSpeech(
    val mode: SpeechMode,
    val keywords: List<String> = emptyList()
)

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
    /** 题目示例/刺激材料；仅 MEMORY 题会在作答前暂时隐藏。 */
    val stimulus: String = "",
    val previewMs: Long = 3_000L,
    val expectedAction: ExpectedAction? = null,
    val expectedSpeech: ExpectedSpeech? = null
)

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

object QuestionCatalog {
    val baselineQuestions: List<QuestionDefinition> = listOf(
        question("BL-A-01", "baseline", "A", QuestionType.CHOICE, "请找到和目标一样的小动物", listOf("🐱", "🐶", "🐰", "🐼"), 1, stimulus = "🐶", previewMs = 0L),
        question("BL-A-02", "baseline", "A", QuestionType.CHOICE, "请点出红色的东西", listOf("🥦", "🍎", "🫐", "🍌"), 1, stimulus = "", previewMs = 0L),
        question("BL-A-03", "baseline", "A", QuestionType.CHOICE, "请找到不一样的一个", listOf("🌙", "🌙", "⭐"), 2, stimulus = "", previewMs = 0L),
        question("BL-B-01", "baseline", "B", QuestionType.MEMORY, "刚才出现的是哪一组？", listOf("🍓🚗", "🚗🌙", "🍌🚲"), 0, stimulus = "🍓🚗", previewMs = 3_000L),
        question("BL-B-02", "baseline", "B", QuestionType.MEMORY, "请找到刚才的顺序", listOf("🏠🌳🌞", "🌞🌳🏠", "🌳🌞🏠"), 1, stimulus = "🌞🌳🏠", previewMs = 3_000L),
        question("BL-B-03", "baseline", "B", QuestionType.MEMORY, "刚才最后出现的是谁？", listOf("🐸", "🦋", "🐟"), 1, stimulus = "🐟🐸🦋", previewMs = 3_000L),
        question("BL-C-01", "baseline", "C", QuestionType.CHOICE, "准备睡觉前，接下来做什么更合适？", listOf("⚽", "🪥", "🎨"), 1, stimulus = "🛏️🌙", previewMs = 0L),
        question("BL-C-02", "baseline", "C", QuestionType.CHOICE, "哪个和其他两个不是一类？", listOf("🍎", "🍌", "🚗"), 2, stimulus = "", previewMs = 0L),
        question("BL-C-03", "baseline", "C", QuestionType.CHOICE, "太阳、月亮、太阳，接下来是什么？", listOf("☀️", "🌙", "⭐"), 1, stimulus = "☀️ 🌙 ☀️ ❓", previewMs = 0L),
        question("BL-D-01", "baseline", "D", QuestionType.CHOICE, "请点一下会飞的动物", listOf("🐟", "🐦", "🐢"), 1, stimulus = "", previewMs = 0L),
        question("BL-D-02", "baseline", "D", QuestionType.CHOICE, "口渴的时候，可以选择什么？", listOf("🧸", "💧", "👟"), 1, stimulus = "", previewMs = 0L),
        question("BL-D-03", "baseline", "D", QuestionType.CHOICE, "“小猫在睡觉”，请选择对应的画面", listOf("🐱💤", "🐱⚽", "🐶🍎"), 0, stimulus = "", previewMs = 0L),
        question("BL-E-01", "baseline", "E", QuestionType.CHOICE, "朋友送你一个礼物，可能是什么心情？", listOf("😢", "😊", "😠"), 1, stimulus = "", previewMs = 0L),
        question("BL-E-02", "baseline", "E", QuestionType.CHOICE, "小朋友正在玩球，我想一起玩，可以怎么做？", listOf("💢", "🙋", "🏃"), 1, stimulus = "", previewMs = 0L),
        question("BL-E-03", "baseline", "E", QuestionType.CHOICE, "朋友指着天空说“看！”，我们可以看哪里？", listOf("👟", "☁️", "🍽️"), 1, stimulus = "🧒👉☁️", previewMs = 0L),
        question("BL-F-01", "baseline", "F", QuestionType.CHOICE, "刷牙的时候需要哪一样？", listOf("🪥", "🧦", "🥄"), 0, stimulus = "", previewMs = 0L),
        question("BL-F-02", "baseline", "F", QuestionType.OBSERVED, "请拍拍小手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👏", expectedAction = ExpectedAction.CLAP),
        question("BL-F-03", "baseline", "F", QuestionType.CHOICE, "准备出门，应该先穿哪一样？", listOf("🛏️", "👕", "🛁"), 1, stimulus = "🚪🌤️", previewMs = 0L),
        question("BL-A-04", "baseline", "A", QuestionType.CHOICE, "请点最大的圆", listOf("·", "•", "●"), 2, stimulus = "", previewMs = 0L),
        question("BL-B-04", "baseline", "B", QuestionType.MEMORY, "刚才看到哪个？", listOf("🏠", "🚗", "🌳"), 0, stimulus = "🏠", previewMs = 3_000L),
        question("BL-C-04", "baseline", "C", QuestionType.CHOICE, "杯子倒了会怎么样？", listOf("💧", "☀️", "🎈"), 0, stimulus = "", previewMs = 0L),
        question("BL-D-04", "baseline", "D", QuestionType.CHOICE, "请点可以喝的东西", listOf("🥛", "👟", "⚽"), 0, stimulus = "", previewMs = 0L),
        question("BL-E-04", "baseline", "E", QuestionType.CHOICE, "朋友帮助了我，可以怎么做？", listOf("谢谢", "抢走", "离开"), 0, stimulus = "", previewMs = 0L),
        question("BL-F-04", "baseline", "F", QuestionType.OBSERVED, "跟着做：拍手、举手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "", previewMs = 0L),
        question("BL-F-05", "baseline", "F", QuestionType.OBSERVED, "请举起一只小手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🙌", expectedAction = ExpectedAction.RAISE_HAND),
        question("BL-F-06", "baseline", "F", QuestionType.OBSERVED, "请竖起大拇指", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👍", expectedAction = ExpectedAction.THUMB_UP)
    )

    val moduleQuestionBank: List<QuestionDefinition> = listOf(
        question("P01-01", "P01", "A", QuestionType.CHOICE, "点红色", listOf("🔴", "🟡", "🔵", "🟢"), 0, stimulus = "", previewMs = 0L),
        question("P01-02", "P01", "A", QuestionType.CHOICE, "点蓝色", listOf("🟠", "🟢", "🔵", "🟣"), 2, stimulus = "", previewMs = 0L),
        question("P01-03", "P01", "A", QuestionType.CHOICE, "点黄色", listOf("🟡", "🔴", "🟢", "🔵"), 0, stimulus = "", previewMs = 0L),
        question("P02-01", "P02", "A", QuestionType.CHOICE, "点圆形", listOf("●", "■", "▲", "★"), 0, stimulus = "", previewMs = 0L),
        question("P02-02", "P02", "A", QuestionType.CHOICE, "点三角形", listOf("◆", "▲", "●", "■"), 1, stimulus = "", previewMs = 0L),
        question("P02-03", "P02", "A", QuestionType.CHOICE, "点正方形", listOf("●", "★", "■", "▲"), 2, stimulus = "", previewMs = 0L),
        question("P03-01", "P03", "A", QuestionType.CHOICE, "找不一样", listOf("🌙", "🌙", "⭐", "🌙"), 2, stimulus = "", previewMs = 0L),
        question("P03-02", "P03", "A", QuestionType.CHOICE, "找不一样", listOf("🍐", "🍐", "🍐", "🍎"), 3, stimulus = "", previewMs = 0L),
        question("P03-03", "P03", "A", QuestionType.CHOICE, "找不一样", listOf("🐱", "🐶", "🐱", "🐱"), 1, stimulus = "", previewMs = 0L),
        question("P04-01", "P04", "A", QuestionType.AUDIO, "请点小狗", listOf("🐶", "🐱", "🐰", "🐼"), 0, stimulus = "", previewMs = 0L),
        question("P04-02", "P04", "A", QuestionType.AUDIO, "请点小鸟", listOf("🐟", "🐦", "🐢", "🐸"), 1, stimulus = "", previewMs = 0L),
        question("P04-03", "P04", "A", QuestionType.AUDIO, "请点汽车", listOf("🚲", "🚌", "🚗", "✈️"), 2, stimulus = "", previewMs = 0L),
        question("M01-01", "M01", "B", QuestionType.CHOICE, "哪个和牙刷一起用？", listOf("🪥膏", "👟", "🚗", "🎈"), 0, stimulus = "🪥", previewMs = 0L),
        question("M01-02", "M01", "B", QuestionType.CHOICE, "哪个和鞋子一起穿？", listOf("🧦", "🥄", "🧸", "🍎"), 0, stimulus = "👟", previewMs = 0L),
        question("M01-03", "M01", "B", QuestionType.CHOICE, "哪个和雨天最有关系？", listOf("☂️", "🪥", "⚽", "📕"), 0, stimulus = "🌧️", previewMs = 0L),
        question("M02-01", "M02", "B", QuestionType.MEMORY, "刚才看到哪个？", listOf("🍓", "🚗", "🌙", "🧸"), 0, stimulus = "🍓", previewMs = 3_000L),
        question("M02-02", "M02", "B", QuestionType.MEMORY, "刚才看到哪个？", listOf("🐶", "🚲", "🍌", "⭐"), 1, stimulus = "🚲", previewMs = 3_000L),
        question("M02-03", "M02", "B", QuestionType.MEMORY, "刚才看到哪个？", listOf("🌻", "🍎", "🚌", "🐟"), 0, stimulus = "🌻", previewMs = 3_000L),
        question("M03-01", "M03", "B", QuestionType.SEQUENCE, "选出刚才的顺序", listOf("☀️🌙", "🌙☀️", "⭐☀️", "🌙⭐"), 0, stimulus = "☀️🌙", previewMs = 3_000L),
        question("M03-02", "M03", "B", QuestionType.SEQUENCE, "选出刚才的顺序", listOf("🚗🍎", "🍎🚗", "🍌🚲", "🚗🍌"), 1, stimulus = "🍎🚗", previewMs = 3_000L),
        question("M03-03", "M03", "B", QuestionType.SEQUENCE, "选出刚才的顺序", listOf("🐟🐶⭐", "🐶🐟⭐", "⭐🐟🐶", "🐶⭐🐟"), 1, stimulus = "🐶🐟⭐", previewMs = 3_000L),
        question("M04-01", "M04", "B", QuestionType.MEMORY, "刚才最后一个是谁？", listOf("🦋", "🐟", "🐸", "🐰"), 0, stimulus = "🐟🐸🦋", previewMs = 3_000L),
        question("M04-02", "M04", "B", QuestionType.MEMORY, "刚才第一个是谁？", listOf("🌙", "🍎", "🚗", "⭐"), 1, stimulus = "🍎🚗🌙", previewMs = 3_000L),
        question("M04-03", "M04", "B", QuestionType.MEMORY, "刚才最后一个是谁？", listOf("☀️", "🌧️", "☁️", "🌙"), 1, stimulus = "☀️☁️🌧️", previewMs = 3_000L),
        question("L01-01", "L01", "D", QuestionType.OBSERVED, "这是什么？说出它的名字", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🍎", expectedSpeech = ExpectedSpeech(SpeechMode.KEYWORD, listOf("苹果"))),
        question("L01-02", "L01", "D", QuestionType.OBSERVED, "这是什么？说出它的名字", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🐶", expectedSpeech = ExpectedSpeech(SpeechMode.KEYWORD, listOf("小狗", "狗狗"))),
        question("L01-03", "L01", "D", QuestionType.OBSERVED, "这是什么？说出它的名字", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🚗", expectedSpeech = ExpectedSpeech(SpeechMode.KEYWORD, listOf("汽车", "车车"))),
        question("L02-01", "L02", "D", QuestionType.OBSERVED, "看图说一句话吧", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🐱 💤", expectedSpeech = ExpectedSpeech(SpeechMode.ANY)),
        question("L02-02", "L02", "D", QuestionType.OBSERVED, "看图说一句话吧", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "小朋友 ⚽", expectedSpeech = ExpectedSpeech(SpeechMode.ANY)),
        question("L02-03", "L02", "D", QuestionType.OBSERVED, "看图说一句话吧", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "妈妈 🍎", expectedSpeech = ExpectedSpeech(SpeechMode.ANY)),
        question("L03-01", "L03", "D", QuestionType.AUDIO, "请点会飞的动物", listOf("🐦", "🐟", "🐢", "🐶"), 0, stimulus = "", previewMs = 0L),
        question("L03-02", "L03", "D", QuestionType.AUDIO, "先点水果", listOf("🚗", "🍎", "👟", "🐶"), 1, stimulus = "", previewMs = 0L),
        question("L03-03", "L03", "D", QuestionType.AUDIO, "请点能喝的东西", listOf("🥛", "🧦", "⚽", "📕"), 0, stimulus = "", previewMs = 0L),
        question("L04-01", "L04", "D", QuestionType.CHOICE, "口渴了，选哪个？", listOf("💧", "🧸", "👟", "🚗"), 0, stimulus = "", previewMs = 0L),
        question("L04-02", "L04", "D", QuestionType.CHOICE, "想休息，选哪个？", listOf("⚽", "🛏️", "🍎", "🚲"), 1, stimulus = "", previewMs = 0L),
        question("L04-03", "L04", "D", QuestionType.CHOICE, "想上厕所，选哪个？", listOf("🚻", "🎨", "🎈", "📕"), 0, stimulus = "", previewMs = 0L),
        question("E01-01", "E01", "C", QuestionType.CHOICE, "下雨出门带什么？", listOf("☂️", "🧢", "🪥", "🥄"), 0, stimulus = "", previewMs = 0L),
        question("E01-02", "E01", "C", QuestionType.CHOICE, "肚子饿了怎么办？", listOf("⚽", "🍚", "🛏️", "🎨"), 1, stimulus = "", previewMs = 0L),
        question("E01-03", "E01", "C", QuestionType.CHOICE, "天冷了穿什么？", listOf("🧥", "🩳", "泳圈", "扇子"), 0, stimulus = "", previewMs = 0L),
        question("E02-01", "E02", "C", QuestionType.SORTING, "哪个不是水果？", listOf("🍎", "🍌", "🚗", "🍓"), 2, stimulus = "", previewMs = 0L),
        question("E02-02", "E02", "C", QuestionType.SORTING, "哪个不是动物？", listOf("🐶", "🐱", "📕", "🐟"), 2, stimulus = "", previewMs = 0L),
        question("E02-03", "E02", "C", QuestionType.SORTING, "哪个不是衣服？", listOf("👕", "🧦", "🥄", "👖"), 2, stimulus = "", previewMs = 0L),
        question("E03-01", "E03", "C", QuestionType.SORTING, "哪一组有两个？", listOf("🍎", "🍎🍎", "🍎🍎🍎", "🍎🍎🍎🍎"), 1, stimulus = "", previewMs = 0L),
        question("E03-02", "E03", "C", QuestionType.SORTING, "哪一组有三个？", listOf("⭐", "⭐⭐", "⭐⭐⭐", "⭐⭐⭐⭐"), 2, stimulus = "", previewMs = 0L),
        question("E03-03", "E03", "C", QuestionType.SORTING, "哪边更多？", listOf("🍌", "🍌🍌🍌"), 1, stimulus = "", previewMs = 0L),
        question("E04-01", "E04", "C", QuestionType.SEQUENCE, "睡觉前先做什么？", listOf("🪥", "⚽", "🎨", "🚲"), 0, stimulus = "", previewMs = 0L),
        question("E04-02", "E04", "C", QuestionType.SEQUENCE, "洗手第一步是什么？", listOf("擦手", "🚰", "关灯", "穿鞋"), 1, stimulus = "", previewMs = 0L),
        question("E04-03", "E04", "C", QuestionType.SEQUENCE, "出门前先做什么？", listOf("睡觉", "👟", "洗澡", "吃糖"), 1, stimulus = "", previewMs = 0L),
        question("S01-01", "S01", "E", QuestionType.CHOICE, "收到礼物是什么心情？", listOf("😊", "😢", "😠", "😴"), 0, stimulus = "", previewMs = 0L),
        question("S01-02", "S01", "E", QuestionType.CHOICE, "玩具坏了是什么心情？", listOf("😊", "😢", "😴", "😮"), 1, stimulus = "", previewMs = 0L),
        question("S01-03", "S01", "E", QuestionType.CHOICE, "被抢玩具是什么心情？", listOf("😊", "😠", "😴", "😄"), 1, stimulus = "", previewMs = 0L),
        question("S02-01", "S02", "E", QuestionType.OBSERVED, "和大人轮流拍手三次", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👏 ↔️ 👏", previewMs = 0L),
        question("S02-02", "S02", "E", QuestionType.OBSERVED, "等大人说“轮到你”再点这里", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "⏳", previewMs = 0L),
        question("S02-03", "S02", "E", QuestionType.OBSERVED, "和大人一起看同一个东西", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🧑 👉 ⭐", previewMs = 0L),
        question("S03-01", "S03", "E", QuestionType.CHOICE, "想一起玩，可以怎么做？", listOf("🙋", "💢", "🏃", "🙅"), 0, stimulus = "", previewMs = 0L),
        question("S03-02", "S03", "E", QuestionType.CHOICE, "别人说你好，可以怎么做？", listOf("👋", "🙈", "💢", "🏃"), 0, stimulus = "", previewMs = 0L),
        question("S03-03", "S03", "E", QuestionType.CHOICE, "轮到别人时，可以怎么做？", listOf("抢走", "⏳", "离开", "大叫"), 1, stimulus = "", previewMs = 0L),
        question("D01-01", "D01", "F", QuestionType.SEQUENCE, "洗手第一步是什么？", listOf("🚰", "擦干", "关灯", "看电视"), 0, stimulus = "", previewMs = 0L),
        question("D01-02", "D01", "F", QuestionType.SEQUENCE, "穿衣第一步是什么？", listOf("👕", "穿鞋", "出门", "睡觉"), 0, stimulus = "", previewMs = 0L),
        question("D01-03", "D01", "F", QuestionType.SEQUENCE, "吃饭前先做什么？", listOf("洗手", "看电视", "穿鞋", "画画"), 0, stimulus = "", previewMs = 0L),
        question("D02-01", "D02", "F", QuestionType.CHOICE, "轻轻点小星星", listOf("🌙", "⭐", "☁️", "☀️"), 1, stimulus = "", previewMs = 0L),
        question("D02-02", "D02", "F", QuestionType.CHOICE, "轻轻点小花", listOf("🌼", "🍎", "🚗", "🐟"), 0, stimulus = "", previewMs = 0L),
        question("D02-03", "D02", "F", QuestionType.CHOICE, "轻轻点小球", listOf("🎈", "⚽", "📕", "🧸"), 1, stimulus = "", previewMs = 0L),
        question("D03-01", "D03", "F", QuestionType.OBSERVED, "跟着节奏拍拍手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👏  👏  👏", expectedAction = ExpectedAction.CLAP),
        question("D03-02", "D03", "F", QuestionType.OBSERVED, "小手举起来，再放下", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🙌  ⬇️", expectedAction = ExpectedAction.RAISE_HAND),
        question("D03-03", "D03", "F", QuestionType.OBSERVED, "跟着做：拍手、举手、拍手", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "👏 🙌 👏", previewMs = 0L),
        question("D03-04", "D03", "F", QuestionType.OBSERVED, "用手指一指", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "☝️", expectedAction = ExpectedAction.POINT_INDEX),
        question("D03-05", "D03", "F", QuestionType.OBSERVED, "比一个耶", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "✌️", expectedAction = ExpectedAction.VICTORY),
        question("D03-06", "D03", "F", QuestionType.OBSERVED, "张开小手掌", listOf("自己完成", "帮助后完成", "还没完成"), null, stimulus = "🖐️", expectedAction = ExpectedAction.OPEN_PALM)
    )

    val firstCourseQuestions: List<QuestionDefinition> = moduleQuestionBank.filter { it.moduleId == "M02" }

    val fullCourseQuestions: List<QuestionDefinition> = moduleQuestionBank
}

