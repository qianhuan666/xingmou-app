package com.xingmou.core.domain

import com.xingmou.data.catalog.TaskCatalog

/** 基线完成后分配到模块的起始训练策略。 */
data class ModuleDifficultySeed(
    val moduleId: String,
    val domain: String,
    val baselineScore: Int,
    val initialDifficulty: Int,
    val priority: Int
)

object BaselineDifficultyPolicy {
    private const val DEFAULT_SCORE = 50

    /** 将能力域的 0～100 分映射到训练初始难度 L1～L5。 */
    fun difficultyForScore(score: Int): Int = when (score.coerceIn(0, 100)) {
        in 0..39 -> 1
        in 40..59 -> 2
        in 60..74 -> 3
        in 75..89 -> 4
        else -> 5
    }

    /**
     * 按弱项优先生成 22 个模块队列。缺少某域分数时使用中性分数，避免未完成基线
     * 被误判为最弱项；基线完成后每个域都会有明确分数。
     */
    fun moduleSeeds(domainScores: Map<String, Int>): List<ModuleDifficultySeed> {
        val normalized = TaskCatalog.all.map { it.domain }.distinct().associateWith { domain ->
            domainScores[domain]?.coerceIn(0, 100) ?: DEFAULT_SCORE
        }
        val domainOrder = normalized.entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .mapIndexed { index, entry -> entry.key to index }
            .toMap()
        return TaskCatalog.all.map { task ->
            val score = normalized.getValue(task.domain)
            ModuleDifficultySeed(
                moduleId = task.id,
                domain = task.domain,
                baselineScore = score,
                initialDifficulty = difficultyForScore(score),
                priority = domainOrder.getValue(task.domain)
            )
        }.sortedWith(compareBy<ModuleDifficultySeed> { it.priority }.thenBy { it.moduleId })
    }

    fun priorityModules(domainScores: Map<String, Int>): List<String> =
        moduleSeeds(domainScores).map { it.moduleId }

    fun initialDifficulty(moduleId: String, domainScores: Map<String, Int>): Int =
        moduleSeeds(domainScores).firstOrNull { it.moduleId == moduleId }?.initialDifficulty ?: 1
}

