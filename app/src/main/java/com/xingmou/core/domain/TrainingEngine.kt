package com.xingmou.core.domain

import com.xingmou.core.model.SupportLevel
import com.xingmou.core.rule.DifficultyController
import com.xingmou.core.rule.TaskWhitelist
import com.xingmou.data.db.TrainingRecordEntity

enum class TrainingGame { TARGET_SEARCH, MEMORY_MATCH, ODD_ONE_OUT }

/** 训练结果对下一题难度的影响，观察题也使用同一套策略。 */
enum class AdaptiveOutcome {
    CORRECT_INDEPENDENT,
    CORRECT_WITH_PROMPT,
    INCORRECT,
    OBSERVED_INDEPENDENT,
    OBSERVED_SUPPORTED,
    OBSERVED_NOT_COMPLETED
}

data class TrainingTask(
    val taskId: String,
    val domain: String,
    val game: TrainingGame,
    val difficulty: Int,
    val supportLevel: SupportLevel,
    val materialType: String,
    val taskName: String = taskId
)

data class TrainingResult(
    val taskId: String,
    val correct: Boolean,
    val firstCorrect: Boolean,
    val reactionMs: Long?,
    val errorType: String?,
    val promptLevel: Int,
    val adaptiveOutcome: AdaptiveOutcome? = null
)

data class AdaptiveDifficultyDecision(
    val currentDifficulty: Int,
    val nextDifficulty: Int,
    val delta: Int,
    val reason: String
)

/** 单题即时自适应：每次最多调整一级，高风险时不自动升级。 */
object AdaptiveDifficultyEngine {
    fun decide(current: Int, outcome: AdaptiveOutcome, highRisk: Boolean = false): AdaptiveDifficultyDecision {
        val safeCurrent = current.coerceIn(DifficultyController.MIN_LEVEL, DifficultyController.MAX_LEVEL)
        val desiredDelta = when (outcome) {
            AdaptiveOutcome.CORRECT_INDEPENDENT,
            AdaptiveOutcome.OBSERVED_INDEPENDENT -> 1
            AdaptiveOutcome.INCORRECT,
            AdaptiveOutcome.OBSERVED_NOT_COMPLETED -> -1
            AdaptiveOutcome.CORRECT_WITH_PROMPT,
            AdaptiveOutcome.OBSERVED_SUPPORTED -> 0
        }
        val delta = if (highRisk) minOf(desiredDelta, 0) else desiredDelta
        val next = (safeCurrent + delta.coerceIn(-1, 1)).coerceIn(
            DifficultyController.MIN_LEVEL,
            DifficultyController.MAX_LEVEL
        )
        val reason = when {
            highRisk -> "存在风险，暂不自动升难度"
            delta > 0 -> "独立完成，下一题提高一级"
            delta < 0 -> "本题未完成，下一题降低一级"
            else -> "使用提示或辅助，保持当前难度"
        }
        return AdaptiveDifficultyDecision(safeCurrent, next, next - safeCurrent, reason)
    }

    fun outcomeOf(result: TrainingResult): AdaptiveOutcome = result.adaptiveOutcome ?: when {
        !result.correct -> AdaptiveOutcome.INCORRECT
        result.firstCorrect && result.promptLevel == 0 -> AdaptiveOutcome.CORRECT_INDEPENDENT
        else -> AdaptiveOutcome.CORRECT_WITH_PROMPT
    }
}

class TrainingEngine {
    fun isTaskAllowed(task: TrainingTask): Boolean =
        task.difficulty in DifficultyController.MIN_LEVEL..DifficultyController.MAX_LEVEL &&
            task.supportLevel != SupportLevel.UNKNOWN &&
            TaskWhitelist.isAllowed(task.domain, task.taskName, task.materialType)

    fun nextDifficulty(current: Int, recentResults: List<TrainingResult>, highRisk: Boolean): Int {
        val result = recentResults.lastOrNull() ?: return current.coerceIn(1, DifficultyController.MAX_LEVEL)
        return AdaptiveDifficultyEngine.decide(current, AdaptiveDifficultyEngine.outcomeOf(result), highRisk).nextDifficulty
    }

    fun nextSupportLevel(current: SupportLevel, recentResults: List<TrainingResult>, highRisk: Boolean): SupportLevel {
        val index = supportIndex(current)
        val errors = recentResults.takeLast(3).count { !it.correct }
        val delta = if (errors >= 2) 1 else if (!highRisk && recentResults.size >= 3 && recentResults.takeLast(3).all { it.correct && it.firstCorrect }) -1 else 0
        return supportFromIndex((index + delta).coerceIn(0, 4))
    }

    fun toRecord(childId: String, domain: String, difficulty: Int, supportLevel: SupportLevel, result: TrainingResult, createdAt: Long): TrainingRecordEntity =
        TrainingRecordEntity(
            recordId = "record-${childId}-${createdAt}-${result.taskId}", childId = childId, domain = domain,
            taskId = result.taskId, difficulty = difficulty.coerceIn(1, DifficultyController.MAX_LEVEL),
            supportLevel = supportLevel.name, reactionMs = result.reactionMs, errorType = result.errorType,
            firstCorrect = result.firstCorrect, correct = result.correct, promptLevel = result.promptLevel.coerceAtLeast(0), createdAt = createdAt
        )

    private fun supportIndex(level: SupportLevel): Int = when (level) {
        SupportLevel.L0 -> 0; SupportLevel.L1 -> 1; SupportLevel.L2 -> 2; SupportLevel.L3 -> 3; SupportLevel.L4 -> 4; SupportLevel.UNKNOWN -> 2
    }
    private fun supportFromIndex(index: Int): SupportLevel = SupportLevel.entries[index]
}
