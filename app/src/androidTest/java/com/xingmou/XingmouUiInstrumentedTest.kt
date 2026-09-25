package com.xingmou

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 阶段 6：验证三端口核心可达、儿童休息闭环和辅助设置入口。 */
class XingmouUiInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetLocalPreferences() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("xingmou_accessibility", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun childCanPauseResumeAndOpenAccessibilitySettings() {
        composeRule.onNodeWithText("和小星一起练习").assertIsDisplayed()
        composeRule.onNodeWithText("先休息").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("休息时间").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("准备好了，继续").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("辅助设置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("辅助设置").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun baselineCanStartAndComplete() {
        val startNodes = composeRule.onAllNodesWithText("开始基线").fetchSemanticsNodes()
        if (startNodes.isNotEmpty()) {
            composeRule.onNodeWithText("开始基线").performScrollTo().performClick()
        } else {
            composeRule.onNodeWithText("重新开始").performScrollTo().performClick()
        }
        val answers = listOf("圆形", "蓝色", "方形", "小球", "先拿杯子", "自己试试")
        answers.forEach { answer ->
            composeRule.onAllNodesWithText(answer).onFirst().performScrollTo().performClick()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("已完成六题起点小测").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("已完成六题起点小测").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun allPortsAreReachable() {
        composeRule.onNodeWithText("家长端").performClick()
        composeRule.onNodeWithText("家庭观察与支持").assertIsDisplayed()
        composeRule.onNodeWithText("专业端").performClick()
        composeRule.onNodeWithText("专业审核工作台").assertIsDisplayed()
        composeRule.onNodeWithText("儿童端").performClick()
        composeRule.onNodeWithText("和小星一起练习").assertIsDisplayed()
    }
}
