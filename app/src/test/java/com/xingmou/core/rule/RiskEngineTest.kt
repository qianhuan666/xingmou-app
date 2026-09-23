package com.xingmou.core.rule

import com.xingmou.core.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEngineTest {
    @Test fun allUrgentRiskTypesAreDetected() {
        val cases = mapOf(
            "孩子今天撞头三次" to RiskEngine.SELF_HARM,
            "孩子一直打人攻击" to RiskEngine.AGGRESSION,
            "刚才抽搐了" to RiskEngine.SEIZURE,
            "孩子呼吸困难" to RiskEngine.BREATHING_OR_SWALLOWING,
            "孩子摔倒后头晕" to RiskEngine.SEVERE_FALL,
            "孩子叫不醒" to RiskEngine.ALTERED_CONSCIOUSNESS,
            "以前会穿衣，现在突然不会了" to RiskEngine.DEVELOPMENTAL_REGRESSION,
            "孩子走失了" to RiskEngine.WANDERING,
            "疑似急症需要急救" to RiskEngine.SUSPECTED_ACUTE_ILLNESS
        )
        cases.forEach { (text, type) ->
            val result = RiskEngine.assessDetailed(text, emptyList(), 0)
            assertEquals(RiskLevel.SAFETY_STOP, result.level)
            assertTrue(type in result.matchedTypes)
        }
    }

    @Test fun pauseHasLowerPriorityThanSafetyStop() {
        assertEquals(RiskLevel.PAUSE, RiskEngine.assess("孩子一直哭", emptyList(), 0))
        assertEquals(RiskLevel.PAUSE, RiskEngine.assess("普通训练", emptyList(), 2))
        assertEquals(RiskLevel.SAFETY_STOP, RiskEngine.assess("孩子呼吸困难但一直哭", emptyList(), 2))
    }

    @Test fun negatedSafetyPhraseDoesNotTrigger() {
        assertEquals(RiskLevel.NONE, RiskEngine.assess("不是呼吸困难", emptyList(), 0))
    }
}
