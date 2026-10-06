package com.xingmou.data.catalog

import kotlin.random.Random

/**
 * 兴趣主题素材换肤：让孩子选择的兴趣（动物 / 交通 / 生活用品）真正改变课程题的视觉素材。
 *
 * 只处理「纯素材结构题」——题干是通用指令（找不一样 / 刚才看到哪个 / 选出顺序 …），
 * 认知考点和评分都与具体素材语义无关，因此可以在不改变题型、题干、正确答案索引的前提下，
 * 把 emoji 整体换成同一主题的一组素材。颜色形状、语言理解、生活常识、情绪社交、动作题
 * 以及基线测评题不换肤（它们的考点与具体素材或文字绑定）。
 *
 * 同一道题在同一主题下的素材由题目 id 作随机种子决定，保证复盘与重复进入时素材稳定一致。
 */
object InterestThemeSkins {

    /** 主题名 -> 该主题的素材池（与 ChildScreen 兴趣选项的文字保持一致）。 */
    private val pools: Map<String, List<String>> = mapOf(
        "动物" to listOf("🐶", "🐱", "🐰", "🐼", "🦁", "🐘", "🐸", "🐵", "🦊", "🐢", "🐟", "🐦"),
        "交通" to listOf("🚗", "🚌", "✈️", "🚲", "🚂", "🚢", "🚓", "🚑", "🚕", "🏍️", "⛵", "🚁"),
        "生活用品" to listOf("🪥", "🧦", "👕", "🥄", "📕", "🧸", "🎈", "⚽", "🪑", "🔑", "🧢", "☂️")
    )

    /**
     * 按主题给课程题换肤；主题为空 / “图片” / 未识别，或该题不属于可换肤模块时原样返回。
     */
    fun apply(question: QuestionDefinition, theme: String?): QuestionDefinition {
        val pool = pools[theme?.trim().orEmpty()] ?: return question
        val rng = Random(question.id.hashCode().toLong())
        return when (question.moduleId) {
            "P03" -> skinOddOneOut(question, pool, rng)
            "M02" -> skinRecognition(question, pool, rng)
            "M03" -> skinSequence(question, pool, rng)
            "M04" -> skinFirstLast(question, pool, rng)
            "L02" -> skinPictureTalk(question, pool, rng)
            else -> question
        }
    }

    /** 按 Unicode 字形簇拆分无分隔拼接的 emoji 串（兼容带变体选择符的 emoji，如 ☀️）。 */
    private fun graphemes(s: String): List<String> {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(s)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) {
            out.add(s.substring(start, end))
            start = end
            end = it.next()
        }
        return out.filter { piece -> piece.isNotBlank() }
    }

    private fun permutations(items: List<String>): List<List<String>> {
        if (items.size <= 1) return listOf(items)
        return items.indices.flatMap { i ->
            val head = items[i]
            permutations(items.filterIndexed { j, _ -> j != i }).map { tail -> listOf(head) + tail }
        }
    }

    /** 从池中取 n 个互不相同的素材。 */
    private fun take(pool: List<String>, n: Int, rng: Random): List<String> =
        pool.shuffled(rng).take(n)

    private val QuestionDefinition.answerIndex: Int
        get() = (correctOption ?: 0).coerceIn(0, (options.size - 1).coerceAtLeast(0))

    /** P03 找不一样：三个相同素材 + 一个异类，异类放在原正确答案的位置。 */
    private fun skinOddOneOut(q: QuestionDefinition, pool: List<String>, rng: Random): QuestionDefinition {
        val (major, odd) = take(pool, 2, rng)
        val answer = q.answerIndex
        val options = List(q.options.size.coerceAtLeast(2)) { index -> if (index == answer) odd else major }
        return q.copy(options = options, stimulus = "")
    }

    /** M02 记忆再认：刺激是单个素材，四个选项中正确项即该素材。 */
    private fun skinRecognition(q: QuestionDefinition, pool: List<String>, rng: Random): QuestionDefinition {
        val choices = take(pool, q.options.size.coerceIn(2, pool.size), rng).toMutableList()
        val target = choices.first()
        val answer = q.answerIndex
        // 把目标素材移动到原正确答案所在位置
        choices.removeAt(0)
        choices.add(answer, target)
        return q.copy(options = choices, stimulus = target)
    }

    /**
     * M03 顺序记忆：刺激长度与原题保持一致（2 或 3 个元素），正确选项与刺激完全相同；
     * 干扰项优先用同批素材的其它排列，不足时引入额外素材构造等长序列。
     */
    private fun skinSequence(q: QuestionDefinition, pool: List<String>, rng: Random): QuestionDefinition {
        val n = graphemes(q.stimulus).size.coerceIn(2, 4).coerceAtMost(pool.size)
        val optionCount = q.options.size.coerceAtLeast(2)
        val answer = q.answerIndex

        val seq = take(pool, n, rng)
        val target = seq.joinToString("")

        val candidates = LinkedHashSet<String>()
        permutations(seq).forEach { perm ->
            val joined = perm.joinToString("")
            if (joined != target) candidates.add(joined)
        }
        // 排列数不够（如 2 元素只有 1 种）时，用额外素材替换某位置补齐干扰项
        if (candidates.size < optionCount - 1) {
            pool.filter { it !in seq }.shuffled(rng).take(2).forEach { extra ->
                seq.indices.forEach { pos ->
                    val replaced = seq.toMutableList().also { it[pos] = extra }.joinToString("")
                    if (replaced != target) candidates.add(replaced)
                }
            }
        }
        val ordered = candidates.toList().shuffled(rng)

        val options = MutableList(optionCount) { "" }
        options[answer] = target
        var k = 0
        for (i in options.indices) {
            if (i == answer) continue
            options[i] = ordered[k % ordered.size]
            k++
        }
        return q.copy(options = options, stimulus = target)
    }

    /**
     * M04 首/末位记忆：刺激是一个素材序列（长度与原题一致），题干问第一个或最后一个；
     * 按题意把答案素材放在原正确答案位置，其余选项为不同的池内素材。
     */
    private fun skinFirstLast(q: QuestionDefinition, pool: List<String>, rng: Random): QuestionDefinition {
        val n = graphemes(q.stimulus).size.coerceIn(2, 4).coerceAtMost(pool.size)
        val seq = take(pool, n, rng)
        val askFirst = q.prompt.contains("第一")
        val answer = if (askFirst) seq.first() else seq.last()
        val stimulus = seq.joinToString("")

        // 干扰项优先用序列中出现过的其它素材（与原题一致，更有挑战），再补池内素材
        val inSeqOthers = seq.filter { it != answer }.shuffled(rng)
        val poolOthers = pool.filter { it !in seq }.shuffled(rng)
        val others = (inSeqOthers + poolOthers)
        val optionCount = q.options.size.coerceAtLeast(2)
        val options = MutableList(optionCount) { "" }
        val answerIndex = q.answerIndex
        options[answerIndex] = answer
        var oi = 0
        for (i in options.indices) {
            if (i == answerIndex) continue
            options[i] = others[oi % others.size]
            oi++
        }
        return q.copy(options = options, stimulus = stimulus)
    }

    /** L02 看图说话：开口表达题（ANY 评分，不依赖具体素材），刺激换成主题小场景。 */
    private fun skinPictureTalk(q: QuestionDefinition, pool: List<String>, rng: Random): QuestionDefinition {
        val scene = take(pool, 2, rng).joinToString("  ")
        return q.copy(stimulus = scene)
    }
}
