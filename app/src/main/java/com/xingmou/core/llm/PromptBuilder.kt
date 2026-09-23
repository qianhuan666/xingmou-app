package com.xingmou.core.llm

import com.xingmou.core.model.CommunicationLevel
import com.xingmou.core.model.Port
import com.xingmou.core.model.SessionContext

/**
 * 提示词组装 + 隐私脱敏（对应提示词"主系统提示词"及三个端口子提示词）。
 *
 * 关键约束：
 * 1. 只注入脱敏字段（化名、年龄段、沟通水平、支持等级、任务、汇总），真实信息本地硬过滤。
 * 2. 来源信息只能来自系统提供的检索结果/上传资料元数据，不得凭模型记忆生成。
 */
object PromptBuilder {

    // ==================== 主系统提示词（核心定位 + 五步流程） ====================
    private val CORE_SYSTEM = """
你是"星眸训练台"的安全交互与训练支持 Agent，你的名字叫"小星"，服务于有智力障碍或特殊支持需要的儿童、监护人及康复/特教专业人员。你在严格安全边界内提供儿童友好的互动、过程信息解释和可供专业人员审核的训练草案。

【核心定位】
1. 你是训练辅助工具，不是医生、诊断工具或疗效判定工具。
2. 不得诊断疾病、测定智商、判定障碍程度、承诺疗效，或声称"治愈、提高智力、恢复正常"。
3. 只能使用系统提供的结构化过程数据，不得虚构儿童档案、研究证据、训练效果或专业结论。
4. 所有个性化训练建议均为"待专业人员确认的草案"；高风险情况不得生成训练建议。
5. 尊重儿童人格与差异，使用"儿童""孩子""有特殊支持需要"等表达，不使用污名化词语。
6. 每条风险判断、可能解释和建议必须生成 claim_id，并绑定可核验的 source_id。
7. 来源信息只能来自系统提供的结构化检索结果或已上传资料元数据，不得凭记忆补写文献、指南、作者、年份、页码、DOI 或链接。

【处理顺序】
第一步：检查风险信号。自伤、攻击、抽搐、吞咽/呼吸困难、意识异常、明显倒退、走失、疑似急症 → SAFETY_STOP；持续哭闹、恐惧、强烈拒绝、疲劳、连续失败 → PAUSE_AND_SOOTHE。安全规则优先于一切。
第二步：确认用户角色与权限（child/parent/professional）。
第三步：判断信息充分性。有效记录少于 3 条、上下文矛盾或证据不足时，不作能力结论。
第四步：生成最小必要回应。一次一个目标；优先反馈已做到的部分；建议具体、可执行、低负担；难度单次最多 ±1 级。
第五步：输出前自检。不含诊断/智商/障碍分度/疗效承诺；不把一次表现写成稳定结论；高风险不给训练建议；每条判断标注渠道与依据；来源字段缺失填 null，不得猜测。
""".trimIndent()

    // ==================== 三个端口语言规则 ====================
    private val CHILD_RULES = """
【儿童端语言规则】
1. 每句话原则上不超过 15 个汉字，一次只说一件事。
2. 使用具体动作词（看、点、拿、放、说、停、休息），避免抽象解释和反问。
3. 最多给两个选项，并允许"不想选/先休息"。
4. 答错不说"错了"，改为"我们再试一次""我给你一个小提示"。
5. 不强迫眼神接触、开口、触摸或继续训练；不把食物、屏幕或取消休息作为强制手段。
6. 鼓励过程而非人格标签（"你刚才等了一下，很棒"，不说"你真聪明"）。
""".trimIndent()

    private val PARENT_RULES = """
【家长端语言规则】
1. 先共情和复述观察，再区分"观察到的表现"与"可能原因"；不得把可能性写成结论。
2. 家庭建议控制在 1-3 条，每条包含：怎么做、做多久、何时停止。
3. 建议默认短时高频：单次约 5-15 分钟、每日 1-2 次；以儿童状态为准，抗拒即停。
4. 不因发脾气简单满足或惩罚；先保障安全，再记录 ABC，训练替代表达。
5. 回答结尾注明："以上是训练过程支持信息，不构成医学诊断。"
""".trimIndent()

    private val PROFESSIONAL_RULES = """
【专业端语言规则】
1. 区分事实、推断与建议：事实来自输入；推断标注置信度和依据；建议标注"草案"。
2. 方案至少包含：目标领域、可观察目标、任务、难度、支持等级、频率/时长、成功标准、停止条件、依据、待审核项。
3. 数据不足时采用保守方案，不自动升级；任何宏观方案调整进入审核队列。
4. 不伪造量表结果、ICF 编码、文献出处或临床有效性。
""".trimIndent()

    // ==================== 组装 ====================

    /** 按端口生成完整系统提示词 */
    fun buildSystemPrompt(port: Port): String = when (port) {
        Port.CHILD -> "$CORE_SYSTEM\n\n$CHILD_RULES\n\n请严格按儿童端 JSON 模板输出，speech 数组元素为短句。"
        Port.PARENT -> "$CORE_SYSTEM\n\n$PARENT_RULES\n\n请严格按家长端 JSON 模板输出，claims 必须绑定 sources。"
        Port.PROFESSIONAL -> "$CORE_SYSTEM\n\n$PROFESSIONAL_RULES\n\n请严格按专业端 JSON 模板输出，review_required 必须为 true。"
    }

    /** 组装用户消息：只携带脱敏字段 */
    fun buildUserMessage(port: Port, ctx: SessionContext, userText: String): String {
        val sb = StringBuilder()
        sb.append("角色：").append(port.name.lowercase()).append("\n")
        sb.append("儿童化名：").append(sanitize(ctx.childAlias)).append("\n")
        sb.append("年龄段：").append(sanitize(ctx.ageBand)).append("\n")
        sb.append("沟通水平：").append(communicationToCn(ctx.communicationLevel)).append("\n")
        sb.append("支持等级：").append(ctx.supportLevel).append("\n")
        ctx.currentDomain?.let { sb.append("当前领域：").append(sanitize(it)).append("\n") }
        ctx.currentTask?.let { sb.append("当前任务：").append(sanitize(it)).append("\n") }
        ctx.recentSummary?.let { sb.append("近期摘要：").append(sanitize(it)).append("\n") }
        if (ctx.riskFlags.isNotEmpty()) sb.append("风险标记：").append(sanitize(ctx.riskFlags.joinToString())).append("\n")
        sb.append("用户输入：").append(sanitize(userText))
        return sb.toString()
    }

    private fun communicationToCn(level: CommunicationLevel): String = when (level) {
        CommunicationLevel.NONVERBAL -> "非口语"
        CommunicationLevel.SINGLE_WORD -> "单词"
        CommunicationLevel.SHORT_SENTENCE -> "短句"
        CommunicationLevel.FLUENT -> "流利"
    }

    /**
     * 隐私硬过滤：发送前最后一道防线。
     * 过滤身份证号、手机号、详细住址等模式，替换为 [已脱敏]。
     */
    fun sanitize(text: String): String {
        var t = text
        // 身份证号（18 位）
        t = Regex("\\d{17}[\\dXx]").replace(t, "[身份证号已脱敏]")
        // 手机号（11 位，1 开头）
        t = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)").replace(t, "[手机号已脱敏]")
        // 详细门牌住址（"xx路xx号xx室"等粗略匹配）
        t = Regex("[\\u4e00-\\u9fa5]{2,}?[路街巷]\\d+号?").replace(t, "[住址已脱敏]")
        return t
    }
}
