package com.xingmou.core.domain

import com.xingmou.core.model.Port
import com.xingmou.core.model.RiskLevel
import com.xingmou.core.rule.RiskEngine
import com.xingmou.data.db.KnowledgeItemEntity

enum class KnowledgeRoute { NORMAL, CLARIFY, NOT_FOUND, REFER, SAFETY_STOP }

data class KnowledgeRetrievalResult(
    val route: KnowledgeRoute,
    val domain: String?,
    val matchedItems: List<KnowledgeItemEntity>,
    val excludedIds: List<String>,
    val riskLevel: String,
    val reason: String
)

/** 受控知识检索：只返回已审核、启用的本地条目，不负责生成新知识。 */
class KnowledgeRetriever {
    fun retrieve(
        query: String,
        port: Port,
        riskFlags: List<String> = emptyList(),
        items: List<KnowledgeItemEntity> = emptyList()
    ): KnowledgeRetrievalResult {
        val risk = RiskEngine.assessDetailed(query, riskFlags, 0)
        if (risk.level == RiskLevel.SAFETY_STOP) {
            return KnowledgeRetrievalResult(
                KnowledgeRoute.SAFETY_STOP, null, emptyList(), emptyList(), risk.level.name,
                "检测到安全风险，停止知识检索并转入安全响应。"
            )
        }
        val normalized = query.trim()
        if (normalized.length < 2) {
            return KnowledgeRetrievalResult(
                KnowledgeRoute.CLARIFY, null, emptyList(), emptyList(), risk.level.name,
                "问题信息不足，需要补充训练领域或具体情境。"
            )
        }
        if (normalized.contains("医生") || normalized.contains("医院") || normalized.contains("诊断")) {
            return KnowledgeRetrievalResult(
                KnowledgeRoute.REFER, null, emptyList(), emptyList(), risk.level.name,
                "涉及诊疗判断，应由专业人员进一步评估。"
            )
        }
        val safeItems = items.filter { it.isActive && it.verificationStatus == "verified" }
        val excluded = items.filterNot { it.isActive && it.verificationStatus == "verified" }.map { it.itemId }
        val matched = safeItems.filter { item ->
            val terms = termsFor(item)
            terms.any { term -> normalized.contains(term) && !isNegated(normalized, term) } ||
                normalized.contains(item.title) || normalized.contains(item.content.take(12))
        }
        val ranked = matched.sortedWith(compareByDescending<KnowledgeItemEntity> { score(normalized, it) }.thenBy { it.itemId })
        val selected = ranked.fold(emptyList<KnowledgeItemEntity>()) { acc, item ->
            if (acc.size >= 3) acc
            else if (item.domain != null && acc.count { it.domain == item.domain } >= 2) acc
            else acc + item
        }
        if (selected.isEmpty()) {
            val regression = normalized.contains("以前会") && (normalized.contains("现在不会") || normalized.contains("突然不会"))
            return KnowledgeRetrievalResult(
                if (regression) KnowledgeRoute.SAFETY_STOP else KnowledgeRoute.NOT_FOUND,
                null, emptyList(), excluded, if (regression) RiskLevel.SAFETY_STOP.name else risk.level.name,
                if (regression) "疑似能力倒退，需停止训练并进行人工复核。" else "未找到已审核且与问题直接匹配的知识条目。"
            )
        }
        return KnowledgeRetrievalResult(
            KnowledgeRoute.NORMAL,
            selected.mapNotNull { it.domain }.distinct().singleOrNull(),
            selected,
            excluded,
            risk.level.name,
            "仅返回已审核条目；同一领域最多 2 条，总数最多 3 条。"
        )
    }

    private fun score(query: String, item: KnowledgeItemEntity): Int =
        termsFor(item).count { query.contains(it) } * 3 +
            if (query.contains(item.title)) 5 else 0

    private fun termsFor(item: KnowledgeItemEntity): List<String> =
        item.keywordsJson.split(',', '"', '[', ']', ' ', '\n', '\r')
            .map { it.trim() }.filter { it.isNotBlank() }

    private fun isNegated(query: String, term: String): Boolean {
        val start = query.indexOf(term)
        if (start < 0) return false
        val prefix = query.substring(maxOf(0, start - 4), start)
        return listOf("不是", "没有", "无需", "不需要", "无").any { prefix.endsWith(it) }
    }
}
