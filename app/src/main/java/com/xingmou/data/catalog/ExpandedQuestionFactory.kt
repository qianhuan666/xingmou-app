package com.xingmou.data.catalog

/**
 * 本地正式训练变式题库。
 *
 * 每个 Web 模块提供 L1-L5、每级 V1-V3 三个稳定变式。题干、刺激和目标答案均
 * 在本地固定生成，避免运行时随机造成无法复盘；后续可以逐模块替换为人工审核
 * 的图像/音频素材，而不改变题目 ID、难度和方案映射。
 */
object ExpandedQuestionFactory {
    private const val SOURCE = "EXPANDED_BANK_V1"

    fun build(): List<QuestionDefinition> = TaskCatalog.all.flatMap { task ->
        (1..5).flatMap { level -> (1..3).map { variant -> questionFor(task.id, task.domain, level, variant) } }
    }

    private fun questionFor(module: String, domain: String, level: Int, variant: Int): QuestionDefinition = when (module) {
        "P01" -> color(module, domain, level, variant)
        "P02" -> shape(module, domain, level, variant)
        "P03" -> visualSearch(module, domain, level, variant)
        "P04" -> audioAttention(module, domain, level, variant)
        "M01" -> objectPairing(module, domain, level, variant)
        "M02" -> flipMemory(module, domain, level, variant)
        "M03" -> sequenceMemory(module, domain, level, variant)
        "M04" -> workingMemory(module, domain, level, variant)
        "L01" -> naming(module, domain, level, variant)
        "L02" -> sentence(module, domain, level, variant)
        "L03" -> instruction(module, domain, level, variant)
        "L04" -> aac(module, domain, level, variant)
        "E01" -> cause(module, domain, level, variant)
        "E02" -> sorting(module, domain, level, variant)
        "E03" -> quantity(module, domain, level, variant)
        "E04" -> planSequence(module, domain, level, variant)
        "S01" -> emotion(module, domain, level, variant)
        "S02" -> turnTaking(module, domain, level, variant)
        "S03" -> socialRule(module, domain, level, variant)
        "D01" -> lifeSequence(module, domain, level, variant)
        "D02" -> fineMotor(module, domain, level, variant)
        "D03" -> imitation(module, domain, level, variant)
        else -> error("Unsupported question module: $module")
    }

    private fun color(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val items = listOf("🔴" to "红色", "🔵" to "蓝色", "🟡" to "黄色", "🟢" to "绿色", "🟣" to "紫色", "🟠" to "橙色")
        val target = items[(level * 3 + variant) % items.size]
        return choice(module, domain, level, variant, "请找到${target.second}，再点一下。", target.first, items.map { it.first }, "颜色识别")
    }

    private fun shape(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val items = listOf("●" to "圆形", "▲" to "三角形", "■" to "正方形", "★" to "星形", "◆" to "菱形", "⬟" to "六边形")
        val target = items[(level * 3 + variant) % items.size]
        return choice(module, domain, level, variant, "请找到${target.second}。", target.first, items.map { it.first }, "形状辨认")
    }

    private fun visualSearch(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val sets = listOf(
            listOf("🌙", "🌙", "⭐", "🌙"), listOf("🍎", "🍎", "🍎", "🍐"),
            listOf("🐱", "🐶", "🐱", "🐱"), listOf("🔵", "🔵", "🟢", "🔵"),
            listOf("▲", "▲", "▲", "■"), listOf("🚗", "🚗", "🚌", "🚗")
        )
        val base = sets[(level * 3 + variant) % sets.size]
        val odd = base.indexOfFirst { value -> base.count { it == value } == 1 }
        return item(module, domain, level, variant, "请在${if (level >= 4) "快速扫一眼后" else "仔细看"}找出不一样的一个。", base, odd, skill = "视觉搜索")
    }

    private fun audioAttention(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val targets = listOf("小狗" to "🐶", "小鸟" to "🐦", "汽车" to "🚗", "小猫" to "🐱", "火车" to "🚂", "小鱼" to "🐟")
        val target = targets[(level * 3 + variant) % targets.size]
        return choice(module, domain, level, variant, "请听清楚后点${target.first}。", target.second, targets.map { it.second }, "听觉注意", type = QuestionType.AUDIO)
    }

    private fun objectPairing(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val pairs = listOf(
            "🪥" to "🪥膏", "👟" to "🧦", "🌧️" to "☂️", "🍞" to "🧈", "✏️" to "📒", "🔑" to "🚪"
        )
        val pair = pairs[(level * 3 + variant) % pairs.size]
        val choices = listOf(pair.second, "⚽", "🎈", "🧸", "🍎", "🚲")
        return choice(module, domain, level, variant, "请找出和${pair.first}一起使用或最相关的东西。", pair.second, choices, "物品配对", stimulus = pair.first)
    }

    private fun flipMemory(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val targets = listOf("🍓", "🚗", "🌙", "🧸", "🐶", "🚲", "🍌", "⭐", "🌻")
        val target = targets[(level * 3 + variant) % targets.size]
        val choices = options(target, targets, level, variant)
        return item(module, domain, level, variant, "先记住刚才出现的图像，再选出它。", choices, choices.indexOf(target), skill = "短时记忆", type = QuestionType.MEMORY, stimulus = target, previewMs = 3_000L)
    }

    private fun sequenceMemory(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val sequences = listOf("☀️🌙", "🍎🚗", "🐶🐟⭐", "🔴🔵🟡", "🚲🍌🌳", "⭐🌙☀️")
        val target = sequences[(level * 3 + variant) % sequences.size]
        val distractors = listOf(target.reversed(), target.drop(1) + target.take(1), target.reversed() + target.take(1))
        val choices = (listOf(target) + distractors).distinct().take(4)
        return item(module, domain, level, variant, "先记住图像顺序，再选出刚才的顺序。", choices, 0, skill = "序列回忆", type = QuestionType.SEQUENCE, stimulus = target, previewMs = 3_000L)
    }

    private fun workingMemory(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val sequences = listOf(
            listOf("🐟", "🐸", "🦋"), listOf("🍎", "🚗", "🌙"), listOf("☀️", "☁️", "🌧️"),
            listOf("🏠", "🌳", "🌞"), listOf("🚲", "🍌", "⭐")
        )
        val sequence = sequences[(level * 3 + variant) % sequences.size]
        val symbols = listOf("🦋", "🍎", "🌧️", "🏠", "⭐", "🐸", "🚗", "☀️")
        val askLast = variant % 2 == 0
        val target = if (askLast) sequence.last() else sequence.first()
        return item(module, domain, level, variant, "记住这组图像，回答刚才${if (askLast) "最后" else "第一个"}出现的是谁？", options(target, symbols, level, variant), 0, skill = "工作记忆", type = QuestionType.MEMORY, stimulus = sequence.joinToString(""), previewMs = 3_000L)
    }

    private fun naming(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val names = listOf("🍎" to listOf("苹果"), "🐶" to listOf("小狗", "狗狗"), "🚗" to listOf("汽车", "车车"), "🍌" to listOf("香蕉"), "🚌" to listOf("公交车", "汽车"), "🐱" to listOf("小猫", "猫咪"))
        val target = names[(level * 3 + variant) % names.size]
        return observed(module, domain, level, variant, "这是什么？请说出它的名字。", target.first, ExpectedSpeech(SpeechMode.KEYWORD, target.second), "图片命名")
    }

    private fun sentence(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val scenes = listOf("🐱💤", "小朋友⚽", "妈妈🍎", "🐶🏠", "孩子📖", "爸爸🚗")
        return observed(module, domain, level, variant, "请看图说一句完整的话。", scenes[(level * 3 + variant) % scenes.size], ExpectedSpeech(SpeechMode.ANY), "句子表达")
    }

    private fun instruction(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val tasks = listOf("会飞的动物" to "🐦", "水果" to "🍎", "能喝的东西" to "🥛", "交通工具" to "🚗", "可以穿的东西" to "👕", "可以阅读的东西" to "📖")
        val target = tasks[(level * 3 + variant) % tasks.size]
        return choice(module, domain, level, variant, "请听指令，点出${target.first}。", target.second, tasks.map { it.second }, "指令理解", type = QuestionType.AUDIO)
    }

    private fun aac(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val needs = listOf("口渴了" to "💧", "想休息" to "🛏️", "想上厕所" to "🚻", "想吃东西" to "🍚", "想玩球" to "⚽", "想回家" to "🏠")
        val target = needs[(level * 3 + variant) % needs.size]
        return choice(module, domain, level, variant, "${target.first}时，请选择你想表达的图片。", target.second, needs.map { it.second }, "AAC/图片选择")
    }

    private fun cause(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val causes = listOf("下雨出门" to "☂️", "肚子饿了" to "🍚", "天冷了" to "🧥", "灯太暗了" to "💡", "手脏了" to "🧼", "口渴了" to "💧")
        val target = causes[(level * 3 + variant) % causes.size]
        return choice(module, domain, level, variant, "因为${target.first}，接下来可以怎么做？", target.second, causes.map { it.second }, "因果关系")
    }

    private fun sorting(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val sets = listOf(
            "水果" to listOf("🍎", "🍌", "🍓", "🚗"), "动物" to listOf("🐶", "🐱", "🐟", "📕"),
            "衣服" to listOf("👕", "🧦", "👖", "🥄"), "交通工具" to listOf("🚗", "🚲", "🚌", "🍎"),
            "文具" to listOf("✏️", "📒", "🖍️", "🍌"), "餐具" to listOf("🥄", "🍽️", "🥣", "🧸")
        )
        val set = sets[(level * 3 + variant) % sets.size]
        return item(module, domain, level, variant, "下面${set.first}中混进了一个不是同类的，请找出来。", set.second, set.second.lastIndex, skill = "分类整理")
    }

    private fun quantity(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val counts = (1..4).map { count -> "🍎".repeat(count) }
        val targetCount = ((level + variant) % 4) + 1
        val target = counts[targetCount - 1]
        return item(module, domain, level, variant, "哪一组有${targetCount}个？", counts, targetCount - 1, skill = "数量认知")
    }

    private fun planSequence(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val steps = listOf("刷牙" to "🪥", "洗手" to "🚰", "出门" to "👟", "吃饭" to "🍚", "睡觉" to "🛏️", "穿衣" to "👕")
        val target = steps[(level * 3 + variant) % steps.size]
        return choice(module, domain, level, variant, "${target.first}前，第一步应该做什么？", target.second, steps.map { it.second }, "计划与顺序", type = QuestionType.SEQUENCE)
    }

    private fun emotion(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val scenes = listOf("收到礼物" to "😊", "玩具坏了" to "😢", "被抢玩具" to "😠", "听到好消息" to "😄", "第一次见到陌生人" to "😟", "完成挑战" to "🥳")
        val target = scenes[(level * 3 + variant) % scenes.size]
        return choice(module, domain, level, variant, "${target.first}时，可能是什么心情？", target.second, scenes.map { it.second }, "情绪识别")
    }

    private fun turnTaking(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val tasks = listOf("和大人轮流拍手三次" to "👏 ↔️ 👏", "等大人说轮到你再点这里" to "⏳", "和大人一起看同一个东西" to "🧑 👉 ⭐", "把玩具轮流传给朋友" to "🧸 ↔️ 🧸", "等待朋友完成后再开始" to "⏳ ✅")
        val target = tasks[(level * 3 + variant) % tasks.size]
        return observed(module, domain, level, variant, target.first, target.second, null, "轮流/共同注意")
    }

    private fun socialRule(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val rules = listOf("想一起玩" to "🙋", "别人说你好" to "👋", "轮到别人时" to "⏳", "需要帮助时" to "🆘", "见到老师时" to "👋", "朋友分享玩具时" to "🙏")
        val target = rules[(level * 3 + variant) % rules.size]
        return choice(module, domain, level, variant, "${target.first}，可以怎么做？", target.second, rules.map { it.second }, "社交规则")
    }

    private fun lifeSequence(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val steps = listOf("洗手" to "🚰", "穿衣" to "👕", "吃饭" to "🍚", "刷牙" to "🪥", "整理玩具" to "🧸", "出门" to "👟")
        val target = steps[(level * 3 + variant) % steps.size]
        return choice(module, domain, level, variant, "${target.first}的第一步是什么？", target.second, steps.map { it.second }, "生活步骤训练", type = QuestionType.SEQUENCE)
    }

    private fun fineMotor(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val targets = listOf("⭐" to "小星星", "🌼" to "小花", "⚽" to "小球", "🔺" to "三角形", "🍎" to "苹果", "🟢" to "绿色圆形")
        val target = targets[(level * 3 + variant) % targets.size]
        return choice(module, domain, level, variant, "请轻轻点${target.second}。", target.first, targets.map { it.first }, "精细动作/点选")
    }

    private fun imitation(module: String, domain: String, level: Int, variant: Int): QuestionDefinition {
        val actions = listOf(
            "跟着节奏拍拍手" to ("👏 👏 👏" to ExpectedAction.CLAP),
            "小手举起来，再放下" to ("🙌 ⬇️" to ExpectedAction.RAISE_HAND),
            "用手指一指" to ("☝️" to ExpectedAction.POINT_INDEX),
            "比一个耶" to ("✌️" to ExpectedAction.VICTORY),
            "张开小手掌" to ("🖐️" to ExpectedAction.OPEN_PALM),
            "竖起大拇指" to ("👍" to ExpectedAction.THUMB_UP)
        )
        val target = actions[(level * 3 + variant) % actions.size]
        return observed(module, domain, level, variant, target.first, target.second.first, null, "模仿/节律动作", expectedAction = target.second.second)
    }

    private fun choice(
        module: String, domain: String, level: Int, variant: Int, prompt: String, target: String,
        pool: List<String>, skill: String, type: QuestionType = QuestionType.CHOICE, stimulus: String = ""
    ): QuestionDefinition {
        val choices = options(target, pool, level, variant)
        return item(module, domain, level, variant, prompt, choices, choices.indexOf(target), skill, type, stimulus)
    }

    private fun observed(
        module: String, domain: String, level: Int, variant: Int, prompt: String, stimulus: String,
        speech: ExpectedSpeech?, skill: String, expectedAction: ExpectedAction? = null
    ): QuestionDefinition = item(
        module, domain, level, variant, prompt,
        listOf("自己完成", "帮助后完成", "还没完成"), null, skill, QuestionType.OBSERVED, stimulus,
        expectedAction = expectedAction, expectedSpeech = speech
    )

    private fun options(target: String, pool: List<String>, level: Int, variant: Int): List<String> {
        val count = when (level) { 1 -> 2; 2 -> 3; else -> 4 }
        val distractors = pool.filter { it != target }
        val offset = (variant + level) % distractors.size.coerceAtLeast(1)
        val rotated = (distractors.drop(offset) + distractors.take(offset))
        return (listOf(target) + rotated).distinct().take(count)
    }

    private fun item(
        module: String, domain: String, level: Int, variant: Int, prompt: String, options: List<String>,
        correct: Int?, skill: String, type: QuestionType = QuestionType.CHOICE, stimulus: String = "",
        previewMs: Long = 0L, expectedAction: ExpectedAction? = null, expectedSpeech: ExpectedSpeech? = null
    ): QuestionDefinition = QuestionDefinition(
        id = "$module-L$level-V$variant", version = 1, moduleId = module, domain = domain, type = type,
        prompt = prompt, options = options, correctOption = correct, sourceRef = SOURCE,
        stimulus = stimulus, previewMs = previewMs, expectedAction = expectedAction,
        expectedSpeech = expectedSpeech, difficulty = level, skillTag = skill, variantGroup = "$module-L$level"
    )
}

