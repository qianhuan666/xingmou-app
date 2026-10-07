package com.xingmou.data.catalog

// 共用模块沿用精编题；覆盖项仅更换内容，难度和能力标签继承同一基准题。
val animalThemeQuestions: List<QuestionDefinition> = themedBank("AN", listOf(
    themed("P01-01", "点红色的瓢虫", listOf("🐞", "🐘", "🐸", "🐋"), 0),
    themed("P01-02", "点蓝色的鲸鱼", listOf("🐞", "🐸", "🐋", "🦆"), 2),
    themed("P01-03", "点黄色的小鸭", listOf("🦆", "🐞", "🐸", "🐋"), 0),
    themed("P02-01", "小乌龟旁边，哪个是圆形？", listOf("🐢 ●", "🐢 ■", "🐢 ▲", "🐢 ★"), 0),
    themed("P02-02", "小兔旁边，哪个是三角形？", listOf("🐰 ◆", "🐰 ▲", "🐰 ●", "🐰 ■"), 1),
    themed("P02-03", "小熊猫旁边，哪个是正方形？", listOf("🐼 ●", "🐼 ★", "🐼 ■", "🐼 ▲"), 2),
    themed("P03-01", "找不一样的小动物", listOf("🐶", "🐶", "🐱", "🐶"), 2),
    themed("P03-02", "找不一样的小动物", listOf("🐰", "🐰", "🐰", "🐼"), 3),
    themed("P03-03", "找不一样的小动物", listOf("🐸", "🐢", "🐸", "🐸"), 1),
    themed("P04-01", "请点小狗", listOf("🐶", "🐱", "🐰", "🐼"), 0),
    themed("P04-02", "请点小猫", listOf("🐘", "🐱", "🐸", "🐢"), 1),
    themed("P04-03", "请点小兔", listOf("🐶", "🐼", "🐰", "🦁"), 2),
    themed("M01-01", "小鸡住在农场，哪个也常住在农场？", listOf("🐄", "🐋", "🦁", "🐘"), 0, "🐔"),
    themed("M01-02", "哪个和小鸟一样会飞？", listOf("🦋", "🐶", "🐰", "🐘"), 0, "🐦"),
    themed("M01-03", "哪个和鲸鱼一样生活在水里？", listOf("🐟", "🐱", "🦁", "🐵"), 0, "🐋"),
    themed("M02-01", "刚才看到哪个？", listOf("🐶", "🐱", "🐰", "🐼"), 0, "🐶"),
    themed("M02-02", "刚才看到哪个？", listOf("🐘", "🐸", "🐢", "🐟"), 1, "🐸"),
    themed("M02-03", "刚才看到哪个？", listOf("🦊", "🦁", "🐵", "🐦"), 0, "🦊"),
    themed("M03-01", "选出刚才的顺序", listOf("🐶🐱", "🐱🐶", "🐰🐶", "🐱🐰"), 0, "🐶🐱"),
    themed("M03-02", "选出刚才的顺序", listOf("🐘🐸", "🐸🐘", "🐢🐟", "🐘🐢"), 1, "🐸🐘"),
    themed("M03-03", "选出刚才的顺序", listOf("🐟🐶🐰", "🐶🐟🐰", "🐰🐟🐶", "🐶🐰🐟"), 1, "🐶🐟🐰"),
    themed("M04-01", "刚才最后一个是谁？", listOf("🦋", "🐟", "🐸", "🐰"), 0, "🐟🐸🦋"),
    themed("M04-02", "刚才第一个是谁？", listOf("🐼", "🐶", "🐱", "🐘"), 1, "🐶🐱🐼"),
    themed("M04-03", "刚才最后一个是谁？", listOf("🐢", "🐦", "🐰", "🦊"), 1, "🐢🐰🐦"),
    named("L01-01", "🐶", listOf("小狗", "狗狗", "狗")),
    named("L01-02", "🐱", listOf("小猫", "猫咪", "猫猫", "猫")),
    named("L01-03", "🐰", listOf("小兔", "兔子", "兔兔", "兔")),
    scene("L02-01", "小狗和小猫在一起，看图说一句话吧", "🐶 🐱"),
    scene("L02-02", "小兔和小熊猫在一起，看图说一句话吧", "🐰 🐼"),
    scene("L02-03", "小鱼和鲸鱼在一起，看图说一句话吧", "🐟 🐋"),
    themed("L03-01", "请点会飞的动物", listOf("🐦", "🐘", "🐰", "🐶"), 0),
    themed("L03-02", "先点小兔", listOf("🐶", "🐰", "🐼", "🐱"), 1),
    themed("L03-03", "请点生活在水里的动物", listOf("🐟", "🐶", "🦁", "🐘"), 0),
    themed("E02-01", "哪个不是会飞的动物？", listOf("🐦", "🦋", "🐘", "🐝"), 2),
    themed("E02-02", "哪个不是生活在水里的动物？", listOf("🐟", "🐋", "🐶", "🐬"), 2),
    themed("E02-03", "哪个不是昆虫？", listOf("🐞", "🦋", "🐰", "🐝"), 2),
    themed("E03-01", "哪一组有两只小兔？", listOf("🐰", "🐰🐰", "🐰🐰🐰", "🐰🐰🐰🐰"), 1),
    themed("E03-02", "哪一组有三只小猫？", listOf("🐱", "🐱🐱", "🐱🐱🐱", "🐱🐱🐱🐱"), 2),
    themed("E03-03", "哪一组小鱼更多？", listOf("🐟", "🐟🐟🐟"), 1),
    themed("D02-01", "轻轻点小猫", listOf("🐶", "🐱", "🐼", "🐢"), 1),
    themed("D02-02", "轻轻点小兔", listOf("🐰", "🐘", "🦁", "🐸"), 0),
    themed("D02-03", "轻轻点小鱼", listOf("🐦", "🐟", "🐵", "🦊"), 1)
))

val trafficThemeQuestions: List<QuestionDefinition> = themedBank("TR", listOf(
    themed("P01-01", "点红色的消防车", listOf("🚒", "🚕", "🚙", "🚓"), 0),
    themed("P01-02", "点蓝色的小汽车", listOf("🚒", "🚕", "🚙", "🚑"), 2),
    themed("P01-03", "点黄色的出租车", listOf("🚕", "🚒", "🚙", "🚓"), 0),
    themed("P02-01", "小汽车旁边，哪个是圆形？", listOf("🚗 ●", "🚗 ■", "🚗 ▲", "🚗 ★"), 0),
    themed("P02-02", "公交车旁边，哪个是三角形？", listOf("🚌 ◆", "🚌 ▲", "🚌 ●", "🚌 ■"), 1),
    themed("P02-03", "火车旁边，哪个是正方形？", listOf("🚂 ●", "🚂 ★", "🚂 ■", "🚂 ▲"), 2),
    themed("P03-01", "找不一样的交通工具", listOf("🚗", "🚗", "🚌", "🚗"), 2),
    themed("P03-02", "找不一样的交通工具", listOf("✈️", "✈️", "✈️", "🚁"), 3),
    themed("P03-03", "找不一样的交通工具", listOf("🚢", "⛵", "🚢", "🚢"), 1),
    themed("P04-01", "请点汽车", listOf("🚗", "🚌", "🚲", "🚂"), 0),
    themed("P04-02", "请点飞机", listOf("🚢", "✈️", "🚲", "🚗"), 1),
    themed("P04-03", "请点轮船", listOf("🚗", "🚂", "🚢", "🚁"), 2),
    themed("M01-01", "哪个和汽车一样在公路上行驶？", listOf("🚌", "✈️", "🚢", "⛵"), 0, "🚗"),
    themed("M01-02", "哪个和直升机一样在天上飞？", listOf("✈️", "🚲", "🚢", "🚂"), 0, "🚁"),
    themed("M01-03", "哪个和轮船一样在水上航行？", listOf("⛵", "🚗", "🚌", "🚂"), 0, "🚢"),
    themed("M02-01", "刚才看到哪个？", listOf("🚗", "🚌", "✈️", "🚲"), 0, "🚗"),
    themed("M02-02", "刚才看到哪个？", listOf("🚂", "🚢", "🚓", "🚑"), 1, "🚢"),
    themed("M02-03", "刚才看到哪个？", listOf("🚁", "🚕", "🏍️", "⛵"), 0, "🚁"),
    themed("M03-01", "选出刚才的顺序", listOf("🚗🚌", "🚌🚗", "🚲🚗", "🚌🚲"), 0, "🚗🚌"),
    themed("M03-02", "选出刚才的顺序", listOf("🚂🚢", "🚢🚂", "🚁✈️", "🚂🚁"), 1, "🚢🚂"),
    themed("M03-03", "选出刚才的顺序", listOf("🚲🚗🚌", "🚗🚲🚌", "🚌🚲🚗", "🚗🚌🚲"), 1, "🚗🚲🚌"),
    themed("M04-01", "刚才最后一个是哪个？", listOf("✈️", "🚗", "🚌", "🚲"), 0, "🚗🚌✈️"),
    themed("M04-02", "刚才第一个是哪个？", listOf("🚂", "🚢", "⛵", "🚁"), 1, "🚢⛵🚂"),
    themed("M04-03", "刚才最后一个是哪个？", listOf("🚓", "🚑", "🚕", "🚒"), 1, "🚓🚕🚑"),
    named("L01-01", "🚗", listOf("汽车", "小汽车", "车车")),
    named("L01-02", "✈️", listOf("飞机", "小飞机", "飞飞")),
    named("L01-03", "🚢", listOf("轮船", "大船", "船船", "船")),
    scene("L02-01", "汽车和公交车在一起，看图说一句话吧", "🚗 🚌"),
    scene("L02-02", "飞机和直升机在一起，看图说一句话吧", "✈️ 🚁"),
    scene("L02-03", "轮船和帆船在一起，看图说一句话吧", "🚢 ⛵"),
    themed("L03-01", "请点能在天上飞的交通工具", listOf("✈️", "🚗", "🚲", "🚢"), 0),
    themed("L03-02", "先点公交车", listOf("🚗", "🚌", "🚂", "🚲"), 1),
    themed("L03-03", "请点能在水上航行的交通工具", listOf("🚢", "🚂", "🚗", "🚲"), 0),
    themed("E02-01", "哪个不是在公路上行驶的交通工具？", listOf("🚗", "🚌", "🚢", "🚕"), 2),
    themed("E02-02", "哪个不是能在天上飞的交通工具？", listOf("✈️", "🚁", "🚲", "🛩️"), 2),
    themed("E02-03", "哪个不是在水上航行的交通工具？", listOf("🚢", "⛵", "🚂", "🚤"), 2),
    themed("E03-01", "哪一组有两辆汽车？", listOf("🚗", "🚗🚗", "🚗🚗🚗", "🚗🚗🚗🚗"), 1),
    themed("E03-02", "哪一组有三辆公交车？", listOf("🚌", "🚌🚌", "🚌🚌🚌", "🚌🚌🚌🚌"), 2),
    themed("E03-03", "哪一组自行车更多？", listOf("🚲", "🚲🚲🚲"), 1),
    themed("D02-01", "轻轻点小汽车", listOf("🚲", "🚗", "🚌", "🚂"), 1),
    themed("D02-02", "轻轻点飞机", listOf("✈️", "🚁", "🚢", "🚕"), 0),
    themed("D02-03", "轻轻点轮船", listOf("⛵", "🚢", "🚓", "🚑"), 1)
))

val householdThemeQuestions: List<QuestionDefinition> = themedBank("HO", listOf(
    themed("P01-01", "点红色的书", listOf("📕", "📒", "📘", "📗"), 0),
    themed("P01-02", "点蓝色的书", listOf("📙", "📗", "📘", "📕"), 2),
    themed("P01-03", "点黄色的本子", listOf("📒", "📕", "📗", "📘"), 0),
    themed("P02-01", "小球旁边，哪个是圆形？", listOf("⚽ ●", "⚽ ■", "⚽ ▲", "⚽ ★"), 0),
    themed("P02-02", "书本旁边，哪个是三角形？", listOf("📕 ◆", "📕 ▲", "📕 ●", "📕 ■"), 1),
    themed("P02-03", "积木旁边，哪个是正方形？", listOf("🧱 ●", "🧱 ★", "🧱 ■", "🧱 ▲"), 2),
    themed("P03-01", "找不一样的物品", listOf("🪥", "🪥", "🥄", "🪥"), 2),
    themed("P03-02", "找不一样的物品", listOf("🧦", "🧦", "🧦", "👕"), 3),
    themed("P03-03", "找不一样的物品", listOf("📕", "🧸", "📕", "📕"), 1),
    themed("P04-01", "请点牙刷", listOf("🪥", "🧦", "🥄", "📕"), 0),
    themed("P04-02", "请点袜子", listOf("👕", "🧦", "🧢", "☂️"), 1),
    themed("P04-03", "请点杯子", listOf("📕", "🪑", "🥛", "🔑"), 2),
    themed("M01-01", "哪个和上衣一样可以穿在身上？", listOf("👖", "🥄", "📕", "🪥"), 0, "👕"),
    themed("M01-02", "穿鞋前，脚上可以先穿哪个？", listOf("🧦", "🥄", "🧸", "📕"), 0, "👟"),
    themed("M01-03", "勺子可以用来吃东西，哪个也可以？", listOf("🍴", "🔑", "🧢", "☂️"), 0, "🥄"),
    themed("M02-01", "刚才看到哪个？", listOf("🪥", "🧦", "🥄", "📕"), 0, "🪥"),
    themed("M02-02", "刚才看到哪个？", listOf("🧸", "⚽", "🎈", "🪑"), 1, "⚽"),
    themed("M02-03", "刚才看到哪个？", listOf("🔑", "🧢", "☂️", "👕"), 0, "🔑"),
    themed("M03-01", "选出刚才的顺序", listOf("🪥🧦", "🧦🪥", "🥄🪥", "🧦🥄"), 0, "🪥🧦"),
    themed("M03-02", "选出刚才的顺序", listOf("📕🧸", "🧸📕", "🎈⚽", "📕🎈"), 1, "🧸📕"),
    themed("M03-03", "选出刚才的顺序", listOf("🥄🪥📕", "🪥🥄📕", "📕🥄🪥", "🪥📕🥄"), 1, "🪥🥄📕"),
    themed("M04-01", "刚才最后一个是哪个？", listOf("🧸", "🪥", "🧦", "🥄"), 0, "🪥🧦🧸"),
    themed("M04-02", "刚才第一个是哪个？", listOf("📕", "⚽", "🎈", "🪑"), 1, "⚽🎈📕"),
    themed("M04-03", "刚才最后一个是哪个？", listOf("🔑", "☂️", "🧢", "👕"), 1, "🔑🧢☂️"),
    named("L01-01", "🪥", listOf("牙刷", "小牙刷")),
    named("L01-02", "🧦", listOf("袜子", "小袜子", "袜袜")),
    named("L01-03", "⚽", listOf("足球", "小球", "球球", "球")),
    scene("L02-01", "上衣和袜子放在一起，看图说一句话吧", "👕 🧦"),
    scene("L02-02", "小熊玩具和球放在一起，看图说一句话吧", "🧸 ⚽"),
    scene("L02-03", "书本和杯子放在一起，看图说一句话吧", "📕 🥛"),
    themed("L03-01", "请点可以穿在身上的东西", listOf("👕", "🪥", "🥄", "📕"), 0),
    themed("L03-02", "先点牙刷", listOf("📕", "🪥", "🧸", "⚽"), 1),
    themed("L03-03", "请点用来喝水的杯子", listOf("🥛", "🧦", "⚽", "📕"), 0),
    themed("E02-01", "哪个不是衣物？", listOf("👕", "🧦", "🥄", "👖"), 2),
    themed("E02-02", "哪个不是餐具？", listOf("🥄", "🍴", "🧢", "🥢"), 2),
    themed("E02-03", "哪个不是玩具？", listOf("🧸", "⚽", "🪥", "🪀"), 2),
    themed("E03-01", "哪一组有两个小球？", listOf("⚽", "⚽⚽", "⚽⚽⚽", "⚽⚽⚽⚽"), 1),
    themed("E03-02", "哪一组有三个气球？", listOf("🎈", "🎈🎈", "🎈🎈🎈", "🎈🎈🎈🎈"), 2),
    themed("E03-03", "哪一组书本更多？", listOf("📕", "📕📕📕"), 1),
    themed("D02-01", "轻轻点小球", listOf("🎈", "⚽", "📕", "🧸"), 1),
    themed("D02-02", "轻轻点小熊玩具", listOf("🧸", "🪥", "🥄", "🧦"), 0),
    themed("D02-03", "轻轻点气球", listOf("⚽", "🎈", "🪑", "🔑"), 1)
))

internal object ThemeQuestionBanks {
    private val banks by lazy {
        mapOf("动物" to animalThemeQuestions, "交通" to trafficThemeQuestions, "生活用品" to householdThemeQuestions)
    }
    private val indexes by lazy {
        banks.mapValues { (_, bank) -> bank.associateBy { it.id.substringAfter('-') } }
    }

    fun forTheme(theme: String?): List<QuestionDefinition> = banks[theme?.trim()].orEmpty()
    fun find(theme: String?, baseId: String): QuestionDefinition? = indexes[theme?.trim()]?.get(baseId)
}

private fun themedBank(prefix: String, overrides: List<QuestionDefinition>): List<QuestionDefinition> {
    val byId = overrides.associateBy { it.id }
    check(byId.size == overrides.size)
    return QuestionCatalog.starterCourseQuestions.map { base ->
        (byId[base.id] ?: base).copy(id = "$prefix-${base.id}", sourceRef = "INTEREST_THEME_V1")
    }
}

private fun themed(
    id: String, prompt: String, options: List<String>, correctOption: Int, stimulus: String = ""
): QuestionDefinition = QuestionCatalog.starterCourseQuestions.first { it.id == id }.copy(
    prompt = prompt, options = options, correctOption = correctOption, stimulus = stimulus
)

private fun named(id: String, stimulus: String, keywords: List<String>): QuestionDefinition =
    QuestionCatalog.starterCourseQuestions.first { it.id == id }.copy(
        stimulus = stimulus, expectedSpeech = ExpectedSpeech(SpeechMode.KEYWORD, keywords)
    )

private fun scene(id: String, prompt: String, stimulus: String): QuestionDefinition =
    QuestionCatalog.starterCourseQuestions.first { it.id == id }.copy(
        prompt = prompt, stimulus = stimulus, expectedSpeech = ExpectedSpeech(SpeechMode.ANY)
    )
