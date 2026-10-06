package com.xingmou.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证兴趣主题换肤：可换肤模块的素材整体变为所选主题，题型/题干/正确答案索引保持不变；
 * 颜色形状/情绪社交等模块与基线题不受影响；同一题两次换肤结果稳定。
 */
class InterestThemeSkinsTest {

    private val animals = listOf("🐶", "🐱", "🐰", "🐼", "🦁", "🐘", "🐸", "🐵", "🦊", "🐢", "🐟", "🐦")
    private val vehicles = listOf("🚗", "🚌", "✈️", "🚲", "🚂", "🚢", "🚓", "🚑", "🚕", "🏍️", "⛵", "🚁")
    private val objects = listOf("🪥", "🧦", "👕", "🥄", "📕", "🧸", "🎈", "⚽", "🪑", "🔑", "🧢", "☂️")

    private fun bank(moduleId: String) =
        QuestionCatalog.moduleQuestionBank.filter { it.moduleId == moduleId }

    /** 字符串是否完全由主题池内的 emoji 拼接而成（兼容多码位 emoji）。 */
    private fun onlyUses(s: String, pool: List<String>): Boolean {
        var rest = s
        while (rest.isNotEmpty()) {
            val hit = pool.firstOrNull { rest.startsWith(it) } ?: return false
            rest = rest.removePrefix(hit)
        }
        return true
    }

    @Test
    fun oddOneOut_replacedWithTheme_andKeepsAnswerSlot() {
        bank("P03").forEach { q ->
            val skinned = InterestThemeSkins.apply(q, "交通")
            assertEquals(q.correctOption, skinned.correctOption)
            val answer = skinned.correctOption!!
            // 只有异类（正确项）一个，其余全部相同
            assertEquals(1, skinned.options.count { it == skinned.options[answer] })
            assertTrue(skinned.options.count { it != skinned.options[answer] } == skinned.options.size - 1)
            assertTrue(skinned.options.all { onlyUses(it, vehicles) })
            assertNotEquals(q.options, skinned.options)
        }
    }

    @Test
    fun recognition_targetSitsAtAnswerAndAllDistinct() {
        bank("M02").forEach { q ->
            val skinned = InterestThemeSkins.apply(q, "动物")
            val answer = skinned.correctOption!!
            assertEquals(skinned.stimulus, skinned.options[answer])
            assertEquals(skinned.options.size, skinned.options.distinct().size)
            assertTrue(onlyUses(skinned.stimulus, animals))
            assertTrue(skinned.options.all { onlyUses(it, animals) })
        }
    }

    @Test
    fun sequence_correctOptionEqualsStimulus() {
        bank("M03").forEach { q ->
            val skinned = InterestThemeSkins.apply(q, "生活用品")
            val answer = skinned.correctOption!!
            assertEquals(skinned.stimulus, skinned.options[answer])
            // 序列长度与原题保持一致（2 或 3 个元素）
            assertEquals(graphemeCount(q.stimulus), graphemeCount(skinned.stimulus))
            assertTrue(skinned.options.filterIndexed { i, _ -> i != answer }.none { it == skinned.stimulus })
            assertTrue(skinned.options.distinct().size == skinned.options.size)
            assertTrue(onlyUses(skinned.stimulus, objects))
            assertTrue(skinned.options.all { onlyUses(it, objects) })
        }
    }

    private fun graphemeCount(s: String): Int {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(s)
        var count = 0
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) { count++; end = it.next() }
        return count
    }

    @Test
    fun firstLast_answerMatchesAskedPosition() {
        bank("M04").forEach { q ->
            val skinned = InterestThemeSkins.apply(q, "交通")
            val answer = skinned.correctOption!!
            val answerEmoji = skinned.options[answer]
            if (q.prompt.contains("第一")) {
                assertTrue("问第一个，答案应在序列开头", skinned.stimulus.startsWith(answerEmoji))
            } else {
                assertTrue("问最后一个，答案应在序列结尾", skinned.stimulus.endsWith(answerEmoji))
            }
            assertEquals(skinned.options.size, skinned.options.distinct().size)
            assertTrue(skinned.options.all { onlyUses(it, vehicles) })
        }
    }

    @Test
    fun pictureTalk_onlyStimulusChanges_speechUntouched() {
        bank("L02").forEach { q ->
            val skinned = InterestThemeSkins.apply(q, "动物")
            assertEquals(q.options, skinned.options)
            assertEquals(q.expectedSpeech, skinned.expectedSpeech)
            assertTrue(onlyUses(skinned.stimulus.filter { !it.isWhitespace() }, animals))
        }
    }

    @Test
    fun nonSkinModules_unchanged() {
        listOf("P01", "P02", "S01", "D03", "M01", "L04").forEach { moduleId ->
            bank(moduleId).forEach { q ->
                assertEquals(q, InterestThemeSkins.apply(q, "动物"))
            }
        }
    }

    @Test
    fun unknownOrPictureTheme_returnsOriginal() {
        val q = bank("M02").first()
        assertEquals(q, InterestThemeSkins.apply(q, null))
        assertEquals(q, InterestThemeSkins.apply(q, ""))
        assertEquals(q, InterestThemeSkins.apply(q, "图片"))
        assertEquals(q, InterestThemeSkins.apply(q, "不存在的主题"))
    }

    @Test
    fun differentThemes_produceDifferentAssets() {
        val q = bank("P03").first()
        val animal = InterestThemeSkins.apply(q, "动物")
        val vehicle = InterestThemeSkins.apply(q, "交通")
        assertNotEquals(animal.options, vehicle.options)
        assertTrue(animal.options.all { onlyUses(it, animals) })
        assertTrue(vehicle.options.all { onlyUses(it, vehicles) })
    }

    @Test
    fun skinning_isDeterministicForSameQuestion() {
        val q = bank("M04").first()
        val a = InterestThemeSkins.apply(q, "交通")
        val b = InterestThemeSkins.apply(q, "交通")
        assertEquals(a, b)
    }
}
