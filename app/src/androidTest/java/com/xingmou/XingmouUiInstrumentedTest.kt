package com.xingmou

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
        composeRule.onNodeWithContentDescription("开始六题起点小测").assertIsDisplayed()
    }

    @Test
    fun childEncouragementPanelIsReachable() {
        composeRule.onNodeWithText("我的小星星").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("最近感觉").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("不用于比较或排名", substring = true).assertIsDisplayed()
    }

    @Test
    fun childCourseMapShowsTwentyLevelsAndHonestAvailability() {
        composeRule.onNodeWithText("课程地图（20关）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1. 图片配对", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("20. 生活顺序", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("当前先开放第一关", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun coreScreenRemainsReachableInLandscapeAndPortrait() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()
        composeRule.onNodeWithText("和小星一起练习").assertIsDisplayed()
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitForIdle()
        composeRule.onNodeWithText("和小星一起练习").assertIsDisplayed()
    }

    @Test
    fun accessibilitySwitchesHaveTalkBackDescriptions() {
        composeRule.onNodeWithText("辅助设置").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("大字体 开关").performClick()
        composeRule.onNodeWithContentDescription("高对比 开关").performClick()
        composeRule.onNodeWithText("辅助设置").assertIsDisplayed()
    }

    @Test
    fun allPortsAreReachable() {
        composeRule.onNodeWithText("家长端").performClick()
        composeRule.onNodeWithText("家庭观察与支持").assertIsDisplayed()
        composeRule.onNodeWithText("今日家庭任务").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("本周家庭回顾").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("5 分钟陪练示范").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存今天的观察").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("专业端").performClick()
        composeRule.onNodeWithText("专业审核工作台").assertIsDisplayed()
        composeRule.onNodeWithText("训练报表").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("分段正确率趋势").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("正确率").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("六域 / 模块聚合").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("最近训练记录").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("量表转录与复评").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("12 方法库").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ABA").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("家庭支持").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存量表记录").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("尚无量表复评记录。", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("能力画像", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("专业个案流程").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("当前阶段").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("阶段备注").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("签署并推进到下一阶段").performScrollTo().assertIsDisplayed()
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
