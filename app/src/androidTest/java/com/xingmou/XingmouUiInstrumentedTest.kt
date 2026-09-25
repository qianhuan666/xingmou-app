package com.xingmou

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
    fun baselineEntryIsReachable() {
        composeRule.onNodeWithText("六题起点小测").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun allPortsAreReachable() {
        composeRule.onNodeWithText("家长端").performClick()
        composeRule.onNodeWithText("家庭观察与支持").assertIsDisplayed()
        composeRule.onNodeWithText("今日家庭任务").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("5 分钟陪练示范").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存今天的观察").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("专业端").performClick()
        composeRule.onNodeWithText("专业审核工作台").assertIsDisplayed()
        composeRule.onNodeWithText("训练报表").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("正确率").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("六域 / 模块聚合").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("最近训练记录").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("家庭反馈").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("儿童端").performClick()
        composeRule.onNodeWithText("和小星一起练习").assertIsDisplayed()
    }

    @Test
    fun parentFeedbackAppearsOnProfessionalTimelineAfterRefresh() {
        composeRule.onNodeWithText("家长端").performClick()
        composeRule.onNodeWithText("保存今天的观察").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("观察已保存到当前儿童档案。", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("专业端").performClick()
        composeRule.onNodeWithText("刷新本地记录").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("心情：平稳", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("心情：平稳", substring = true).performScrollTo().assertIsDisplayed()
    }
}
