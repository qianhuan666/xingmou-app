package com.xingmou.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDifficultyEngineTest {
    @Test
    fun independentCorrectRaisesOneLevel() {
        val decision = AdaptiveDifficultyEngine.decide(2, AdaptiveOutcome.CORRECT_INDEPENDENT)
        assertEquals(3, decision.nextDifficulty)
        assertEquals(1, decision.delta)
    }

    @Test
    fun incorrectLowersOneLevel() {
        val decision = AdaptiveDifficultyEngine.decide(4, AdaptiveOutcome.INCORRECT)
        assertEquals(3, decision.nextDifficulty)
        assertEquals(-1, decision.delta)
    }

    @Test
    fun promptKeepsDifficultyAndHighRiskCannotUpgrade() {
        assertEquals(3, AdaptiveDifficultyEngine.decide(3, AdaptiveOutcome.CORRECT_WITH_PROMPT).nextDifficulty)
        assertEquals(3, AdaptiveDifficultyEngine.decide(3, AdaptiveOutcome.CORRECT_INDEPENDENT, highRisk = true).nextDifficulty)
    }

    @Test
    fun difficultyIsBoundedAtBothEnds() {
        assertEquals(1, AdaptiveDifficultyEngine.decide(1, AdaptiveOutcome.INCORRECT).nextDifficulty)
        assertEquals(5, AdaptiveDifficultyEngine.decide(5, AdaptiveOutcome.CORRECT_INDEPENDENT).nextDifficulty)
        assertTrue(AdaptiveDifficultyEngine.decide(3, AdaptiveOutcome.OBSERVED_NOT_COMPLETED).reason.isNotBlank())
    }
}

