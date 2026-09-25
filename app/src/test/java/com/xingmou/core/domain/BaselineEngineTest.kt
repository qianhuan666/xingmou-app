package com.xingmou.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineEngineTest {
    private val engine = BaselineEngine()

    @Test
    fun sixQuestionsCanCompleteAndProduceDomainScores() {
        var session = engine.newSession(1L)
        repeat(6) { session = engine.answer(session, 0, it.toLong() + 2) }
        assertEquals(BaselineStatus.COMPLETED, session.status)
        assertEquals(6, session.answers.size)
        assertEquals(100, engine.scores(session)["A"])
        assertEquals(0, engine.scores(session)["F"])
        assertNull(engine.currentQuestion(session))
    }

    @Test
    fun sessionRoundTripsAsJson() {
        val original = engine.answer(engine.newSession(1L), 1, 2L)
        val restored = engine.fromJson(engine.toJson(original))
        assertNotNull(restored)
        assertEquals(original, restored)
    }
}
