package com.xingmou.core.domain

import com.xingmou.data.catalog.QuestionDefinition
import java.util.Random
import kotlin.math.abs

data class ModuleQuestionSelectionRequest(
    val moduleId: String,
    val difficulty: Int,
    val sessionId: String,
    val completedQuestionIds: Set<String> = emptySet(),
    val seenQuestionIds: Set<String> = emptySet(),
    val limit: Int = 1
)

/** 按模块、难度和训练会话稳定抽取题目，避免一次训练内重复。 */
object ModuleQuestionSelector {
    fun select(
        bank: List<QuestionDefinition>,
        request: ModuleQuestionSelectionRequest
    ): List<QuestionDefinition> {
        val targetDifficulty = request.difficulty.coerceIn(1, 5)
        val excluded = request.completedQuestionIds + request.seenQuestionIds
        val moduleQuestions = bank.filter { it.moduleId == request.moduleId && it.id !in excluded }
        if (moduleQuestions.isEmpty()) return emptyList()

        val current = moduleQuestions.filter { it.difficulty == targetDifficulty }
        val lower = moduleQuestions.filter { it.difficulty == targetDifficulty - 1 }
        val higher = moduleQuestions.filter { it.difficulty == targetDifficulty + 1 }
        val fallback = moduleQuestions
            .filter { it !in current && it !in lower && it !in higher }
            .sortedBy { abs(it.difficulty - targetDifficulty) }

        // 7:2:1 近似实现 70% 当前、20% 巩固、10% 挑战；候选不足时自动回填。
        val weighted = buildList {
            repeat(7) { addAll(current) }
            repeat(2) { addAll(lower) }
            addAll(higher)
            addAll(fallback)
        }
        val random = Random(stableSeed(request))
        return weighted.shuffled(random)
            .distinctBy { it.id }
            .take(request.limit.coerceAtLeast(1))
            .map { question ->
                PlanQuestionPolicy.run { question.forDifficulty(targetDifficulty) }
            }
    }

    private fun stableSeed(request: ModuleQuestionSelectionRequest): Long =
        listOf(request.moduleId, request.difficulty.toString(), request.sessionId,
            request.completedQuestionIds.sorted().joinToString(","), request.seenQuestionIds.sorted().joinToString(","))
            .joinToString("|")
            .fold(1125899906842597L) { hash, value -> hash * 31 + value.code }
}

