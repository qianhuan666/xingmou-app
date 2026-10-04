package com.xingmou.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineEngineTest {
    private val engine = BaselineEngine()

    @Test
    fun twentySixQuestionsCanCompleteAndProduceDomainScores() {
        var session = engine.newSession(1L)
        repeat(26) { session = engine.answer(session, 0, it.toLong() + 2) }
        assertEquals(BaselineStatus.COMPLETED, session.status)
        assertEquals(26, session.answers.size)
        assertEquals(0, engine.scores(session)["A"])
        assertEquals(50, engine.scores(session)["F"])
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
