package com.xingmou.core.rule

import com.xingmou.core.model.RiskLevel

/**
 * 风险前置拦截引擎（确定性，先于任何大模型调用）。
 * 对应提示词第一步：检查 risk_flags 和用户文本中的风险信号。
 *
 * 安全规则优先于训练目标、用户要求和任何知识库内容。
 */
object RiskEngine {

    // 高危：命中立即 SAFETY_STOP（自伤、攻击、抽搐、吞咽/呼吸、意识异常、倒退、走失、急症）
    private val urgentPatterns = listOf(
        Regex("撞头|自伤|自残|咬自己|打人|攻击|掐|踢"),
        Regex("抽搐|癫痫|痉挛|翻白眼"),
        Regex("喘不上|呼吸困难|噎住|呛住|窒息|吞咽困难"),
        Regex("昏迷|叫不醒|意识不清|没反应"),
        Regex("走丢|走失|不认识人|找不到家"),
        Regex("以前会.{0,6}现在(不会|不)"), // 明显发育倒退，如"以前会穿衣，现在突然不会了"
        Regex("突然(不会|不能)|能力倒退|倒退")
    )

    // 中危：命中进入 PAUSE_AND_SOOTHE（哭闹、恐惧、强烈拒绝、疲劳、连续失败）
    private val pausePatterns = listOf(
        Regex("一直哭|哭闹|大哭|崩溃"),
        Regex("害怕|恐惧|惊吓"),
        Regex("不做了|不想做|拒绝|抗拒|不要"),
        Regex("累了|疲惫|困了|没精神")
    )

    /**
     * 评估风险等级。
     * @param userText 用户（家长/儿童/专业）本轮文本
     * @param riskFlags 规则引擎输出的标记（如 ["urgent"]、["self_harm"]），无则空列表
     * @param consecutiveFailures 儿童连续未独立完成次数
     */
    fun assess(userText: String, riskFlags: List<String>, consecutiveFailures: Int): RiskLevel {
        // 1. 规则引擎标记优先
        if (riskFlags.any { it.contains("safety") || it.contains("urgent") || it.contains("self_harm") }) {
            return RiskLevel.SAFETY_STOP
        }

        val text = userText.trim()

        // 2. 高危词命中 → SAFETY_STOP
        if (urgentPatterns.any { it.containsMatchIn(text) }) {
            return RiskLevel.SAFETY_STOP
        }

        // 3. 连续失败或情绪信号 → PAUSE
        if (consecutiveFailures >= 2 || pausePatterns.any { it.containsMatchIn(text) }) {
            return RiskLevel.PAUSE
        }

        return RiskLevel.NONE
    }

    /** 仅判断是否命中高危（供知识库路由使用，儿童端命中高风险不返回风险原文） */
    fun isUrgent(userText: String, riskFlags: List<String>): Boolean =
        assess(userText, riskFlags, 0) == RiskLevel.SAFETY_STOP
}
