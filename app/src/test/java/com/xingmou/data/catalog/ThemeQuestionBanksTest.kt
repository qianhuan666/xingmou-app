package com.xingmou.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.xingmou.core.domain.PlanQuestionPolicy
import com.xingmou.core.domain.QuestionEvaluator
import com.xingmou.core.perception.SpeechAnswerDetector
import com.xingmou.core.perception.SpeechHit

class ThemeQuestionBanksTest {
    @Test
    fun eachThemeHasTheSameSixtyNineQuestionShape() {
        val base = QuestionCatalog.starterCourseQuestions
        listOf(animalThemeQuestions, trafficThemeQuestions, householdThemeQuestions).forEach { themed ->
            assertEquals(69, themed.size)
            assertEquals(base.map { it.moduleId }, themed.map { it.moduleId })
            assertEquals(base.map { it.domain }, themed.map { it.domain })
            assertEquals(base.map { it.type }, themed.map { it.type })
            assertEquals(base.map { it.difficulty }, themed.map { it.difficulty })
            assertEquals(base.map { it.skillTag }, themed.map { it.skillTag })
            assertEquals(base.map { it.expectedAction }, themed.map { it.expectedAction })
            assertEquals(base.map { it.previewMs }, themed.map { it.previewMs })
            assertEquals(base.map { it.options.size }, themed.map { it.options.size })
            base.zip(themed).forEach { (source, actual) ->
                assertEquals("correctOption mismatch at ${source.id}", source.correctOption, actual.correctOption)
            }
            assertEquals(base.map { it.id }, themed.map { it.id.substringAfter('-') })
            assertTrue(themed.all { it.correctOption == null || it.correctOption in it.options.indices })
            assertTrue(themed.filter { it.type == QuestionType.OBSERVED }.all { it.correctOption == null })
        }
        val all = animalThemeQuestions + trafficThemeQuestions + householdThemeQuestions
        assertEquals(207, all.map { it.id }.distinct().size)
    }

    @Test
    fun everyMemoryTargetAndNamingKeywordCanBeEvaluated() {
        listOf(animalThemeQuestions, trafficThemeQuestions, householdThemeQuestions).forEach { bank ->
            bank.filter { it.moduleId in setOf("M02", "M03", "M04") }.forEach { q ->
                val target = q.options[q.correctOption!!]
                when (q.moduleId) {
                    "M02", "M03" -> assertEquals(q.stimulus, target)
                    "M04" -> assertTrue(if (q.prompt.contains("第一")) q.stimulus.startsWith(target) else q.stimulus.endsWith(target))
                }
                assertEquals(1, q.options.count { it == target })
                assertEquals(true, QuestionEvaluator.evaluate(q, q.correctOption).correct)
            }
            bank.filter { it.moduleId == "L01" }.forEach { q ->
                q.expectedSpeech!!.keywords.forEach { keyword ->
                    assertTrue("blank keyword for ${q.id}", keyword.isNotBlank())
                    val hit = SpeechAnswerDetector.evaluate(keyword, q.expectedSpeech)
                    assertTrue("${q.id}: $keyword", hit is SpeechHit.SPOKEN && hit.matchedKeyword != null)
                }
            }
        }
    }

    @Test
    fun difficultyAdaptationKeepsTheThemedCorrectAnswer() {
        listOf("动物", "交通", "生活用品").forEach { theme ->
            QuestionCatalog.starterCourseQuestions.forEach { base ->
                val full = InterestThemeSkins.apply(base, theme)
                (1..5).forEach { level ->
                    val trimmed = PlanQuestionPolicy.run { base.forDifficulty(level) }
                    val actual = InterestThemeSkins.apply(trimmed, theme)
                    assertEquals(trimmed.options.size, actual.options.size)
                    assertEquals(level, actual.difficulty)
                    full.correctOption?.let { answer ->
                        assertEquals(full.options[answer], actual.options[actual.correctOption!!])
                    }
                }
            }
        }
    }

    @Test
    fun themedSpeechAndMemoryFieldsStayCoherent() {
        listOf(animalThemeQuestions, trafficThemeQuestions, householdThemeQuestions).forEach { themed ->
            themed.filter { it.moduleId == "L01" }.forEach { question ->
                assertTrue(question.stimulus.isNotBlank())
                assertTrue(question.expectedSpeech?.keywords?.isNotEmpty() == true)
            }
            themed.filter { it.type == QuestionType.MEMORY || it.moduleId == "M03" }.forEach { question ->
                assertEquals(3_000L, question.previewMs)
                assertTrue(question.stimulus.isNotBlank())
            }
        }
    }

    @Test
    fun skinningUsesTheFullThemeBankForCuratedQuestions() {
        val source = QuestionCatalog.starterCourseQuestions.first { it.id == "P04-03" }
        val themed = InterestThemeSkins.apply(source, "交通")
        assertEquals("TR-${source.id}", themed.id)
        assertEquals(listOf("🚗", "🚂", "🚢", "🚁"), themed.options)
        assertEquals(2, themed.correctOption)
    }
}
