package com.xingmou.core.domain

enum class PlanStatus { DRAFT, CONFIRMED, ACTIVE, REJECTED, SUPERSEDED, ARCHIVED }
enum class PlanActor { MODEL, PROFESSIONAL, SYSTEM }

data class PlanTransitionResult(val accepted: Boolean, val newStatus: PlanStatus, val reason: String? = null)

/** 训练方案的人工审核状态机，模型只能创建草案。 */
class PlanStateMachine {
    fun createDraft(actor: PlanActor): PlanTransitionResult =
        if (actor == PlanActor.MODEL) PlanTransitionResult(true, PlanStatus.DRAFT)
        else PlanTransitionResult(false, PlanStatus.DRAFT, "只有模型或受控生成流程可以创建 DRAFT。")

    fun transition(current: PlanStatus, target: PlanStatus, actor: PlanActor, reason: String? = null): PlanTransitionResult {
        if (target == PlanStatus.DRAFT) return PlanTransitionResult(false, current, "已存在的方案不能退回 DRAFT。")
        if (actor != PlanActor.PROFESSIONAL) return PlanTransitionResult(false, current, "方案状态变更必须由专业人员确认。")
        val needsReason = target == PlanStatus.REJECTED || target == PlanStatus.SUPERSEDED || target == PlanStatus.ARCHIVED
        if (needsReason && reason.isNullOrBlank()) return PlanTransitionResult(false, current, "退回、替代或归档必须填写理由。")
        val allowed = when (current to target) {
            PlanStatus.DRAFT to PlanStatus.CONFIRMED,
            PlanStatus.DRAFT to PlanStatus.REJECTED,
            PlanStatus.CONFIRMED to PlanStatus.ACTIVE,
            PlanStatus.CONFIRMED to PlanStatus.REJECTED,
            PlanStatus.ACTIVE to PlanStatus.SUPERSEDED,
            PlanStatus.ACTIVE to PlanStatus.ARCHIVED -> true
            else -> false
        }
        return if (allowed) PlanTransitionResult(true, target, reason)
        else PlanTransitionResult(false, current, "不允许从 $current 直接转换为 $target。")
    }
}
