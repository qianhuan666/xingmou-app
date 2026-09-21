package com.xingmou.core.model

import com.google.gson.annotations.SerializedName

/** 用户角色（对应提示词 {{user_role}}） */
enum class Port {
    CHILD, PARENT, PROFESSIONAL;

    companion object {
        fun from(s: String?): Port = when (s?.lowercase()) {
            "child" -> CHILD
            "parent" -> PARENT
            "professional" -> PROFESSIONAL
            else -> PARENT // 未知角色按最保守处理
        }
    }
}

/** 支持等级 L0-L4（最小辅助原则） */
enum class SupportLevel { L0, L1, L2, L3, L4, UNKNOWN }

/** 沟通水平 */
enum class CommunicationLevel { NONVERBAL, SINGLE_WORD, SHORT_SENTENCE, FLUENT }

/** 风险等级（规则引擎输出，非模型） */
enum class RiskLevel { NONE, PAUSE, SAFETY_STOP }

/**
 * 溯源判断（对应提示词 1.2 节 claim 对象）
 * 每条风险判断/可能解释/转介建议/训练建议都必须有唯一 claim_id 并绑定 source_ids
 */
data class Claim(
    @SerializedName("claim_id") val claimId: String,
    @SerializedName("claim_type") val claimType: String, // risk_signal|possible_explanation|referral_advice|training_advice
    val statement: String,
    val certainty: String,                                // observed|reported|possible|supported
    @SerializedName("source_ids") val sourceIds: List<String>,
    val limitations: String? = null,
    @SerializedName("recommended_action") val recommendedAction: String? = null
)

/** 来源（对应提示词 1.2 节 source 对象），字段缺失必须为 null，不得猜测 */
data class Source(
    @SerializedName("source_id") val sourceId: String,
    @SerializedName("source_category") val sourceCategory: String, // INDIVIDUAL_MEDICAL_RECORD|MEDICAL_LITERATURE
    @SerializedName("source_type") val sourceType: String,
    val title: String,
    @SerializedName("authors_or_organization") val authorsOrOrganization: String? = null,
    @SerializedName("publication_or_report_date") val publicationOrReportDate: String? = null,
    @SerializedName("verification_status") val verificationStatus: String, // verified|unverified|conflicting
    @SerializedName("privacy_safe_label") val privacySafeLabel: String? = null
)

/** 脱敏后的运行上下文（注入模型的唯一数据，不含真实姓名/病历/住址等） */
data class SessionContext(
    val childAlias: String,
    val ageBand: String,
    val communicationLevel: CommunicationLevel,
    val supportLevel: SupportLevel,
    val currentDomain: String? = null,
    val currentTask: String? = null,
    val recentSummary: String? = null,
    val riskFlags: List<String> = emptyList()
)

/** 儿童端输出（对应提示词第三节 JSON） */
data class ChildOutput(
    val state: String, // continue|hint|choice|pause|safety_stop
    val speech: List<String>,
    @SerializedName("next_action") val nextAction: String,
    @SerializedName("support_level") val supportLevel: String?,
    val ui: ChildUi? = null
)

data class ChildUi(
    val options: List<String> = emptyList(),
    @SerializedName("show_break_button") val showBreakButton: Boolean = true,
    @SerializedName("use_voice") val useVoice: Boolean = true
)

/** 家长端输出（对应提示词第四节 JSON） */
data class ParentOutput(
    val mode: String, // answer|clarify|insufficient_data|refer|safety_stop
    val acknowledgement: String? = null,
    val observation: String? = null,
    @SerializedName("possible_explanations") val possibleExplanations: List<String> = emptyList(),
    val claims: List<Claim> = emptyList(),
    val sources: List<Source> = emptyList(),
    @SerializedName("home_support") val homeSupport: List<HomeSupport> = emptyList(),
    @SerializedName("one_question") val oneQuestion: String? = null,
    val referral: String? = null,
    val disclaimer: String = "以上是训练过程支持信息，不构成医学诊断。"
)

data class HomeSupport(
    val action: String,
    val duration: String,
    @SerializedName("stop_when") val stopWhen: String,
    @SerializedName("based_on") val basedOn: List<String> = emptyList()
)

/** 专业端输出（对应提示词第五节 JSON），强制 review_required=true */
data class ProfessionalOutput(
    val status: String, // draft|insufficient_data|safety_stop
    val claims: List<Claim> = emptyList(),
    val sources: List<Source> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val inferences: List<Inference> = emptyList(),
    @SerializedName("plan_draft") val planDraft: PlanDraft? = null,
    @SerializedName("review_required") val reviewRequired: Boolean = true,
    @SerializedName("review_items") val reviewItems: List<String> = emptyList()
)

data class Fact(
    val statement: String,
    @SerializedName("source_channel") val sourceChannel: String
)

data class Inference(
    val statement: String,
    val confidence: String, // low|medium|high
    @SerializedName("based_on") val basedOn: List<String>
)

data class PlanDraft(
    @SerializedName("priority_domain") val priorityDomain: String,
    @SerializedName("observable_goal") val observableGoal: String,
    val task: String,
    val difficulty: Int,
    @SerializedName("support_level") val supportLevel: String,
    val frequency: String,
    val duration: String,
    @SerializedName("success_criteria") val successCriteria: String,
    @SerializedName("stop_conditions") val stopConditions: List<String>,
    @SerializedName("based_on") val basedOn: List<String>
)
