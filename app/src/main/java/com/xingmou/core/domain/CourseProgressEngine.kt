package com.xingmou.core.domain

import com.xingmou.data.catalog.QuestionDefinition
import com.xingmou.data.catalog.QuestionCatalog
import com.xingmou.data.db.TrainingRecordEntity

data class CourseProgress(
    val completedQuestionIds: Set<String>,
    val nextQuestion: QuestionDefinition?,
    val total: Int,
    val recordCount: Int,
    val summary: String
) {
    val completedCount: Int get() = completedQuestionIds.size.coerceAtMost(total)
    val isComplete: Boolean get() = completedCount >= total
}

class CourseProgressEngine(
    private val questions: List<QuestionDefinition> = QuestionCatalog.firstCourseQuestions
) {
    fun summarize(records: List<TrainingRecordEntity>): CourseProgress {
        val ids = questions.map { it.id }.toSet()
        val completed = records.asSequence()
            .filter { it.correct && it.taskId in ids }
            .map { it.taskId }
            .toSet()
        val next = questions.firstOrNull { it.id !in completed }
        val summary = when {
            records.isEmpty() -> "尚无第一关记录"
            completed.size >= questions.size -> "第一关已完成 · 共 ${records.size} 条记录"
            else -> "已完成 ${completed.size}/${questions.size} · 共 ${records.size} 条记录"
        }
        return CourseProgress(completed, next, questions.size, records.size, summary)
    }
}
