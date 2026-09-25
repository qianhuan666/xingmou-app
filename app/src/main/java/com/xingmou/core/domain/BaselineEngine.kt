package com.xingmou.core.domain

import com.google.gson.Gson
import com.xingmou.data.catalog.QuestionCatalog
import com.xingmou.data.catalog.QuestionDefinition

enum class BaselineStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, NEEDS_RETEST }

data class BaselineAnswer(
    val questionId: String,
    val domain: String,
    val selectedOption: Int,
    val correct: Boolean?,
    val promptCount: Int = 0,
    val reactionMs: Long? = null
)

data class BaselineSession(
    val version: Int = 1,
    val status: BaselineStatus = BaselineStatus.NOT_STARTED,
    val currentIndex: Int = 0,
    val answers: List<BaselineAnswer> = emptyList(),
    val startedAt: Long? = null,
    val completedAt: Long? = null
)

class BaselineEngine(
    private val questions: List<QuestionDefinition> = QuestionCatalog.baselineQuestions
) {
    private val gson = Gson()

    fun newSession(now: Long): BaselineSession = BaselineSession(
        status = BaselineStatus.IN_PROGRESS,
        startedAt = now
    )

    fun currentQuestion(session: BaselineSession): QuestionDefinition? =
        questions.getOrNull(session.currentIndex)

    fun answer(session: BaselineSession, option: Int, now: Long): BaselineSession {
        val question = currentQuestion(session) ?: return session
        val normalized = option.coerceIn(0, question.options.lastIndex)
        val answer = BaselineAnswer(
            questionId = question.id,
            domain = question.domain,
            selectedOption = normalized,
            correct = question.correctOption?.let { it == normalized }
        )
        val nextIndex = session.currentIndex + 1
        val completed = nextIndex >= questions.size
        return session.copy(
            status = if (completed) BaselineStatus.COMPLETED else BaselineStatus.IN_PROGRESS,
            currentIndex = nextIndex.coerceAtMost(questions.size),
            answers = session.answers + answer,
            completedAt = if (completed) now else null
        )
    }

    fun scores(session: BaselineSession): Map<String, Int> =
        session.answers.groupBy { it.domain }.mapValues { (_, answers) ->
            val scored = answers.mapNotNull { it.correct }
            if (scored.isEmpty()) 0 else scored.count { it } * 100 / scored.size
        }

    fun toJson(session: BaselineSession): String = gson.toJson(session)

    fun fromJson(json: String?): BaselineSession? = json?.takeIf { it.isNotBlank() }?.let {
        runCatching { gson.fromJson(it, BaselineSession::class.java) }.getOrNull()
    }
}
