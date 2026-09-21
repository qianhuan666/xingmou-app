package com.xingmou.core.rule

import com.xingmou.core.model.Port

/**
 * 端口权限隔离（对应提示词第二步：确认用户角色与权限）。
 *
 * 三端口边界：
 * - child：只提供短句、单步指令、鼓励、选择与休息提示；不展示诊断、高风险细节、分数排名或专业术语。
 * - parent：解释过程表现和家庭支持方法；不下诊断，不替专业人员制定高风险方案。
 * - professional：可生成带依据、风险、支持等级和审核项的结构化草案，但必须 review_required=true。
 */
object PortGuard {

    /** 各端口禁止出现的内容类型 */
    private val forbiddenByPort: Map<Port, List<String>> = mapOf(
        Port.CHILD to listOf(
            "诊断", "智力", "障碍", "风险", "分数", "排名", "评估",
            "病情", "症状", "疾病", "治疗"
        ),
        Port.PARENT to listOf(
            "诊断", "确诊", "智力低下", "低能", "弱智", "问题儿童"
        ),
        Port.PROFESSIONAL to emptyList() // 专业端可在权限内查看完整引用
    )

    /**
     * 校验某段输出文本是否越权。
     * @return 命中越权的敏感词列表（空表示合规）
     */
    fun checkLeak(port: Port, text: String): List<String> =
        forbiddenByPort[port].orEmpty().filter { it in text }

    /**
     * 判断该端口能否看到某条来源的完整信息。
     * 规则：家长端不得展示未经授权的完整医疗报告；儿童端不展示医学文献和病症标签。
     */
    fun canViewSource(port: Port, sourceCategory: String): Boolean = when (port) {
        Port.CHILD -> false // 儿童端永不展示来源
        Port.PARENT -> sourceCategory == "MEDICAL_LITERATURE" // 家长只看文献类脱敏来源
        Port.PROFESSIONAL -> true
    }

    /** 儿童端是否允许展示某类 claim（儿童端不展示风险判断/病症标签） */
    fun canShowClaimToChild(claimType: String): Boolean =
        claimType == "training_advice" // 儿童端仅可呈现训练建议，其余不展示
}
