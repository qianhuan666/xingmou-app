package com.xingmou.core.rule

import com.xingmou.core.model.RiskLevel

/**
 * 风险前置拦截引擎（确定性，先于任何大模型调用）。
 * 对应提示词第一步：检查 risk_flags 和用户文本中的风险信号。
 *
 * 安全规则优先于训练目标、用户要求和任何知识库内容。
 */
object RiskEngine {

    const val SELF_HARM = "self_harm"
    const val AGGRESSION = "aggression"
    const val SEIZURE = "seizure"
    const val BREATHING_OR_SWALLOWING = "breathing_or_swallowing"
    const val SEVERE_FALL = "severe_fall"
    const val ALTERED_CONSCIOUSNESS = "altered_consciousness"
    const val DEVELOPMENTAL_REGRESSION = "developmental_regression"
    const val WANDERING = "wandering"
    const val SUSPECTED_ACUTE_ILLNESS = "suspected_acute_illness"

    // 高危：命中立即 SAFETY_STOP（自伤、攻击、抽搐、吞咽/呼吸、意识异常、倒退、走失、急症）
    private val urgentPatterns: List<Pair<String, Regex>> = listOf(
        SELF_HARM to Regex("撞头|自伤|自残|咬自己"),
        AGGRESSION to Regex("打人|攻击|掐|踢人|伤人"),
        SEIZURE to Regex("抽搐|癫痫|痉挛|翻白眼"),
        BREATHING_OR_SWALLOWING to Regex("喘不上|呼吸困难|噎住|呛住|窒息|吞咽困难"),
        SEVERE_FALL to Regex("严重跌倒|重重摔倒|摔倒.*头晕|头部重击|摔伤后.*(昏|吐|晕)"),
        ALTERED_CONSCIOUSNESS to Regex("昏迷|叫不醒|意识不清|没反应|突然失去意识"),
        WANDERING to Regex("走丢|走失|不认识人|找不到家|陌生人带走"),
        DEVELOPMENTAL_REGRESSION to Regex("以前会.{0,8}现在(不会|不)|突然(不会|不能)|能力倒退|倒退"),
        SUSPECTED_ACUTE_ILLNESS to Regex("疑似急症|急救|高热不退|持续呕吐|严重过敏|胸痛|大出血|休克|脸色发紫|嘴唇发紫|突然倒下")
    )

    // 中危：命中进入 PAUSE_AND_SOOTHE（哭闹、恐惧、强烈拒绝、疲劳、连续失败）
    private val pausePatterns = listOf(
        Regex("一直哭|哭闹|大哭|崩溃"),
        Regex("害怕|恐惧|惊吓"),
        Regex("不做了|不想做|拒绝|抗拒|不要"),
        Regex("累了|疲惫|困了|没精神")
    )

    data class RiskAssessment(
        val level: RiskLevel,
        val matchedTypes: List<String>,
        val shouldStop: Boolean,
        val shouldPause: Boolean
    )

    fun assessDetailed(userText: String, riskFlags: List<String>, consecutiveFailures: Int): RiskAssessment {
        val flaggedTypes = riskFlags.filter { flag ->
            flag.contains("safety", ignoreCase = true) ||
                flag.contains("urgent", ignoreCase = true) ||
                flag.contains("self_harm", ignoreCase = true)
        }
        val text = userText.trim()
        val matched = urgentPatterns.mapNotNull { (type, pattern) ->
            if (pattern.find(text)?.let { !isNegated(text, it.range.first) } == true) type else null
        }.toMutableList()
        if (flaggedTypes.isNotEmpty()) matched.addAll(flaggedTypes)
        val unique = matched.distinct()
        if (unique.isNotEmpty()) {
            return RiskAssessment(RiskLevel.SAFETY_STOP, unique, shouldStop = true, shouldPause = false)
        }
        val pause = consecutiveFailures >= 2 || pausePatterns.any { pattern ->
            pattern.find(text)?.let { !isNegated(text, it.range.first) } == true
        }
        return if (pause) {
            RiskAssessment(RiskLevel.PAUSE, emptyList(), shouldStop = false, shouldPause = true)
        } else {
            RiskAssessment(RiskLevel.NONE, emptyList(), shouldStop = false, shouldPause = false)
        }
    }

    private fun isNegated(text: String, matchStart: Int): Boolean {
        val prefix = text.substring(maxOf(0, matchStart - 4), matchStart)
        return listOf("不是", "没有", "無", "无", "未", "并非", "不").any { prefix.endsWith(it) }
    }

    /**
     * 评估风险等级。
     * @param userText 用户（家长/儿童/专业）本轮文本
     * @param riskFlags 规则引擎输出的标记（如 ["urgent"]、["self_harm"]），无则空列表
     * @param consecutiveFailures 儿童连续未独立完成次数
     */
    fun assess(userText: String, riskFlags: List<String>, consecutiveFailures: Int): RiskLevel {
        return assessDetailed(userText, riskFlags, consecutiveFailures).level
    }

    /** 仅判断是否命中高危（供知识库路由使用，儿童端命中高风险不返回风险原文） */
    fun isUrgent(userText: String, riskFlags: List<String>): Boolean =
        assess(userText, riskFlags, 0) == RiskLevel.SAFETY_STOP
}
