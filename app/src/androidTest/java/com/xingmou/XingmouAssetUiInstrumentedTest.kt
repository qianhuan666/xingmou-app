package com.xingmou

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xingmou.ui.child.ChildScreen
import com.xingmou.ui.theme.XingmouTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XingmouAssetUiInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Test
    fun localTrainingAssetHasAccessibleDescription() {
        composeRule.setContent {
            XingmouTheme {
                ChildScreen(
                    state = ChildUiState(
                        courseUnlocked = true,
                        courseOpen = true,
                        courseQuestionId = "M02-L1-01",
                        courseTitle = "图片配对",
                        assetKey = "training_ball"
                    ),
                    baseline = BaselineUiState(),
                    accessibility = AccessibilityUiState(speechEnabled = false),
                    onChoice = {},
                    onStartBaseline = {},
                    onResumeBaseline = {},
                    onLeaveBaseline = {},
                    onRestartBaseline = {},
                    onBaselineAnswer = {},
                    onStartCourse = {},
                    onLeaveCourse = {},
                    onResumeCourse = {},
                    onPause = {},
                    onResume = {},
                    onSpeechEnabledChange = {},
                    onSpeechRateChange = {},
                    onSpeechVolumeChange = {},
                    onLargeTextChange = {},
                    onHighContrastChange = {},
                    onSlowMotionChange = {},
                    onInterestChange = {}
                )
            }
        }
        composeRule.onNodeWithContentDescription("训练素材：图片配对").assertIsDisplayed()
    }
}
