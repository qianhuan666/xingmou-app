package com.xingmou.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineDifficultyPolicyTest {
    @Test
    fun scoreMapsToFiveDifficultyBands() {
        assertEquals(1, BaselineDifficultyPolicy.difficultyForScore(0))
        assertEquals(1, BaselineDifficultyPolicy.difficultyForScore(39))
        assertEquals(2, BaselineDifficultyPolicy.difficultyForScore(40))
        assertEquals(3, BaselineDifficultyPolicy.difficultyForScore(60))
        assertEquals(4, BaselineDifficultyPolicy.difficultyForScore(75))
        assertEquals(5, BaselineDifficultyPolicy.difficultyForScore(90))
        assertEquals(5, BaselineDifficultyPolicy.difficultyForScore(100))
    }

    @Test
    fun weakestDomainModulesComeFirst() {
        val queue = BaselineDifficultyPolicy.moduleSeeds(mapOf("A" to 80, "B" to 20, "C" to 60, "D" to 70, "E" to 90, "F" to 50))
        assertEquals("B", queue.first().domain)
        assertEquals(1, queue.first().initialDifficulty)
        assertEquals(22, queue.size)
        assertEquals(22, queue.map { it.moduleId }.toSet().size)
    }

    @Test
    fun missingDomainUsesNeutralDifficulty() {
        val queue = BaselineDifficultyPolicy.moduleSeeds(emptyMap())
        assertTrue(queue.all { it.baselineScore == 50 && it.initialDifficulty == 2 })
    }
}

