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

data class CourseEncouragement(
    val points: Int,
    val completedRounds: Int,
    val trendLabel: String,
    val rewardMessage: String
)

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

    /** 儿童端只展示鼓励性进度，不展示排名或能力分数。 */
    fun encouragement(progress: CourseProgress, records: List<TrainingRecordEntity>): CourseEncouragement {
        val courseIds = questions.map { it.id }.toSet()
        val courseRecords = records.filter { it.taskId in courseIds }
        val successful = courseRecords.count { it.correct }
        val recent = courseRecords.takeLast(3)
        val trend = when {
            courseRecords.isEmpty() -> "刚刚开始"
            recent.size >= 3 && recent.all { it.correct } -> "越来越熟悉"
            recent.size >= 2 && recent.all { !it.correct } -> "可以慢一点"
            else -> "保持自己的节奏"
        }
        val reward = when {
            progress.isComplete -> "这一关完成了，今天做得很棒。"
            progress.completedCount == 0 -> "先完成一个小活动，就会点亮第一颗小星星。"
            else -> "已经点亮 ${progress.completedCount} 颗小星星，继续按自己的节奏。"
        }
        return CourseEncouragement(
            points = (successful * 10).coerceAtMost(progress.total * 10),
            completedRounds = if (progress.isComplete) 1 else 0,
            trendLabel = trend,
            rewardMessage = reward
        )
    }
}
