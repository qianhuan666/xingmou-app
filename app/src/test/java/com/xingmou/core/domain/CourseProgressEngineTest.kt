package com.xingmou.core.domain

import com.xingmou.data.db.TrainingRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseProgressEngineTest {
    private val engine = CourseProgressEngine()

    @Test
    fun onlyCorrectUniqueQuestionsAdvanceProgress() {
        val records = listOf(
            record("M02-L1-01", true, 1),
            record("M02-L1-01", true, 2),
            record("M02-L1-02", false, 3),
            record("M02-L1-03", true, 4)
        )
        val progress = engine.summarize(records)
        assertEquals(2, progress.completedCount)
        assertEquals("M02-L1-02", progress.nextQuestion?.id)
        assertFalse(progress.isComplete)
        assertEquals("已完成 2/5 · 共 4 条记录", progress.summary)
    }

    @Test
    fun fiveDifferentCorrectQuestionsCompleteFirstLevel() {
        val progress = engine.summarize((1..5).map { record("M02-L1-0$it", true, it.toLong()) })
        assertTrue(progress.isComplete)
        assertEquals(5, progress.completedCount)
        assertEquals(null, progress.nextQuestion)
    }

    private fun record(taskId: String, correct: Boolean, createdAt: Long) = TrainingRecordEntity(
        recordId = "record-$taskId-$createdAt", childId = "child", domain = "B", taskId = taskId,
        difficulty = 1, supportLevel = "L1", reactionMs = 1000, errorType = null,
        firstCorrect = correct, correct = correct, promptLevel = 0, createdAt = createdAt
    )
}
