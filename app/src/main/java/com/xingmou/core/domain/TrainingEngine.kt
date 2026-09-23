package com.xingmou.core.domain

import com.xingmou.core.model.SupportLevel
import com.xingmou.core.rule.DifficultyController
import com.xingmou.core.rule.TaskWhitelist
import com.xingmou.data.db.TrainingRecordEntity

enum class TrainingGame { TARGET_SEARCH, MEMORY_MATCH, ODD_ONE_OUT }

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
    val promptLevel: Int
)

class TrainingEngine {
    fun isTaskAllowed(task: TrainingTask): Boolean =
        task.difficulty in DifficultyController.MIN_LEVEL..DifficultyController.MAX_LEVEL &&
            task.supportLevel != SupportLevel.UNKNOWN &&
            TaskWhitelist.isAllowed(task.domain, task.taskName, task.materialType)

    fun nextDifficulty(current: Int, recentResults: List<TrainingResult>, highRisk: Boolean): Int {
        if (recentResults.isEmpty()) return current.coerceIn(1, DifficultyController.MAX_LEVEL)
        val recent = recentResults.takeLast(3)
        val delta = when {
            recent.size >= 3 && recent.all { it.correct && it.firstCorrect } -> 1
            recent.count { !it.correct } >= 2 -> -1
            else -> 0
        }
        val evidence = recentResults.size >= 3
        return DifficultyController.nextLevel(current, delta, evidence, highRisk)
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
