package com.xingmou.core.domain

import com.xingmou.data.db.TrainingRecordEntity

enum class Trend { IMPROVING, STABLE, DECLINING, INSUFFICIENT_DATA }

data class TrainingAnalysis(
    val sampleCount: Int,
    val dataSufficient: Boolean,
    val accuracy: Double?,
    val firstCorrectRate: Double?,
    val averageReactionMs: Double?,
    val averagePromptLevel: Double?,
    val independentCompletionRate: Double?,
    val trend: Trend,
    val observations: List<String>,
    val warningSignals: List<String>,
    val reviewRequired: Boolean
)

class AnalysisEngine {
    fun analyze(records: List<TrainingRecordEntity>, highRisk: Boolean = false): TrainingAnalysis {
        val ordered = records.sortedBy { it.createdAt }
        val count = ordered.size
        if (count < 3) return TrainingAnalysis(count, false, null, null, null, null, null, Trend.INSUFFICIENT_DATA,
            listOf("有效记录少于 3 条，暂不判断趋势。"), if (highRisk) listOf("当前存在高风险标记。") else emptyList(), highRisk)
        val accuracy = ordered.count { it.correct }.toDouble() / count
        val firstCorrect = ordered.count { it.firstCorrect }.toDouble() / count
        val reactions = ordered.mapNotNull { it.reactionMs }
        val prompts = ordered.map { it.promptLevel.toDouble() }
        val independent = ordered.count { it.supportLevel.equals("L0", true) || it.promptLevel == 0 }.toDouble() / count
        val split = count / 2
        val firstHalf = ordered.take(split)
        val secondHalf = ordered.drop(count - split)
        val firstAccuracy = firstHalf.count { it.correct }.toDouble() / firstHalf.size
        val secondAccuracy = secondHalf.count { it.correct }.toDouble() / secondHalf.size
        val firstIndependent = firstHalf.count { it.supportLevel.equals("L0", true) || it.promptLevel == 0 }.toDouble() / firstHalf.size
        val secondIndependent = secondHalf.count { it.supportLevel.equals("L0", true) || it.promptLevel == 0 }.toDouble() / secondHalf.size
        val firstReaction = firstHalf.mapNotNull { it.reactionMs }.averageOrNull()
        val secondReaction = secondHalf.mapNotNull { it.reactionMs }.averageOrNull()
        val declining = secondAccuracy <= firstAccuracy - 0.2 && (firstReaction == null || secondReaction == null || secondReaction > firstReaction)
        val improving = secondAccuracy >= firstAccuracy + 0.2 && (firstReaction == null || secondReaction == null || secondReaction <= firstReaction)
        val trend = when { declining -> Trend.DECLINING; improving -> Trend.IMPROVING; else -> Trend.STABLE }
        val warnings = mutableListOf<String>()
        if (declining) warnings += "正确率下降且反应时间上升，建议人工复核。"
        if (secondIndependent <= firstIndependent - 0.3) warnings += "独立完成率明显下降，建议人工复核。"
        if (highRisk) warnings += "当前存在高风险标记，不生成训练升级建议。"
        return TrainingAnalysis(count, true, accuracy, firstCorrect, reactions.averageOrNull(), prompts.average(), independent, trend,
            listOf("本次分析描述训练过程表现，不构成诊断。", "共分析 $count 条有效记录。"), warnings, warnings.any { it.contains("复核") } || highRisk)
    }

    private fun List<Long>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
