package com.xingmou.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingmou.AccessibilityUiState
import com.xingmou.ChildUiState
import com.xingmou.BaselineUiState
import com.xingmou.CurriculumLevelStatus
import com.xingmou.CurriculumLevelUi
import com.xingmou.CurriculumMapUi
import com.xingmou.CurriculumPlayerUi
import com.xingmou.RainbowProfileUi
import com.xingmou.data.catalog.QuestionType
import com.xingmou.core.domain.BaselineStatus
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.components.XiaoXingMark
import com.xingmou.ui.components.domainBarColor
import com.xingmou.ui.theme.Error
import com.xingmou.R

private enum class ChildSection { TRAINING, CHAT, PROFILE, SETTINGS }

@Composable
fun ChildScreen(
    state: ChildUiState,
    baseline: BaselineUiState,
    accessibility: AccessibilityUiState,
    onStartBaseline: () -> Unit,
    onResumeBaseline: () -> Unit,
    onLeaveBaseline: () -> Unit,
    onRestartBaseline: () -> Unit,
    onBaselineAnswer: (Int) -> Unit,
    onOpenCurriculumLevel: (Int) -> Unit,
    onAnswerCurriculumActivity: (Int) -> Unit,
    onLeaveCurriculumLevel: () -> Unit,
    onChooseCurriculumInterest: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSpeechEnabledChange: (Boolean) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechVolumeChange: (Float) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onSlowMotionChange: (Boolean) -> Unit,
    onInterestChange: (String) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onSelectChatProvider: (com.xingmou.core.llm.ChatLlmProvider) -> Unit,
    onOpenApiKey: () -> Unit,
    onTogglePerception: (Boolean) -> Unit,
    onTogglePerceptionPreview: (Boolean) -> Unit,
    onAttachPerceptionPreviewView: (androidx.camera.view.PreviewView) -> Unit,
    onDetachPerceptionPreviewView: (androidx.camera.view.PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speechController = remember(context) { ChildSpeechController(context) }
    val selectedSection = remember { mutableStateOf(ChildSection.TRAINING) }
    val selectedCourseLevel = remember { mutableStateOf<Int?>(null) }
    DisposableEffect(speechController) {
        onDispose { speechController.shutdown() }
    }
    LaunchedEffect(accessibility.speechRate, accessibility.speechVolume) {
        speechController.setSpeechRate(accessibility.speechRate)
        speechController.setSpeechVolume(accessibility.speechVolume)
    }
    LaunchedEffect(state.message, accessibility.speechEnabled, state.isPaused, state.isSafetyStopped) {
        if (accessibility.speechEnabled && !state.isWorking) speechController.speak(state.message)
        if (!accessibility.speechEnabled) speechController.stop()
    }
    LaunchedEffect(state.courseQuestionId, state.courseQuestionType, state.courseStimulus, accessibility.speechEnabled) {
        if (accessibility.speechEnabled && state.courseQuestionType == QuestionType.AUDIO && state.courseStimulus.isNotBlank()) {
            speechController.speak(state.courseStimulus)
        }
    }
    val curriculumQuestion = state.curriculumPlayer.question
    LaunchedEffect(curriculumQuestion?.id, curriculumQuestion?.type, accessibility.speechEnabled) {
        val q = curriculumQuestion
        if (accessibility.speechEnabled && q != null && q.type == QuestionType.AUDIO) {
            val text = q.stimulus.ifBlank { q.prompt }
            if (text.isNotBlank()) speechController.speak(text)
        }
    }
    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text("小星", modifier = Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.titleMedium)
            NavigationRailItem(
                selected = selectedSection.value == ChildSection.TRAINING,
                onClick = { selectedSection.value = ChildSection.TRAINING },
                icon = { Text("训", style = MaterialTheme.typography.titleLarge) },
                label = { Text("训练") },
                alwaysShowLabel = true,
                modifier = Modifier.semantics { contentDescription = "儿童训练界面" }
            )
            NavigationRailItem(
                selected = selectedSection.value == ChildSection.CHAT,
                onClick = { selectedSection.value = ChildSection.CHAT },
                icon = { Text("聊", style = MaterialTheme.typography.titleLarge) },
                label = { Text("交互") },
                alwaysShowLabel = true,
                modifier = Modifier.semantics { contentDescription = "与小星对话交互模式" }
            )
            NavigationRailItem(
                selected = selectedSection.value == ChildSection.PROFILE,
                onClick = { selectedSection.value = ChildSection.PROFILE },
                icon = { Text("画", style = MaterialTheme.typography.titleLarge) },
                label = { Text("画报") },
                alwaysShowLabel = true,
                modifier = Modifier.semantics { contentDescription = "我的彩虹画像数据画报" }
            )
            NavigationRailItem(
                selected = selectedSection.value == ChildSection.SETTINGS,
                onClick = { selectedSection.value = ChildSection.SETTINGS },
                icon = { Text("设", style = MaterialTheme.typography.titleLarge) },
                label = { Text("设置") },
                alwaysShowLabel = true,
                modifier = Modifier.semantics { contentDescription = "儿童设置界面" }
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                XiaoXingMark(Modifier.size(52.dp))
                Column {
                    Text(
                        when (selectedSection.value) {
                            ChildSection.TRAINING -> "和小星一起练习"
                            ChildSection.CHAT -> "和小星说说话"
                            ChildSection.PROFILE -> "我的彩虹画像"
                            ChildSection.SETTINGS -> "儿童端设置"
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        when (selectedSection.value) {
                            ChildSection.TRAINING -> "一次只做一步，随时可以休息。"
                            ChildSection.CHAT -> "有问题、想聊天，都可以告诉小星。"
                            ChildSection.PROFILE -> "这是小星的游戏足迹，不是考试分数。"
                            ChildSection.SETTINGS -> "调整小星的呈现方式和练习偏好。"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (selectedSection.value == ChildSection.TRAINING) {
                PerceptionHomePreview(
                    state = state,
                    onAttachPreviewView = onAttachPerceptionPreviewView,
                    onDetachPreviewView = onDetachPerceptionPreviewView
                )
                BaselineCard(baseline, onStartBaseline, onResumeBaseline, onLeaveBaseline, onRestartBaseline, onBaselineAnswer)
                if (selectedCourseLevel.value == null) {
                    CurriculumMapCard(
                        map = state.curriculumMap,
                        courseUnlocked = state.courseUnlocked,
                        isWorking = state.isWorking,
                        onChooseInterest = onChooseCurriculumInterest,
                        onOpenLevel = { level ->
                            selectedCourseLevel.value = level
                            onOpenCurriculumLevel(level)
                        }
                    )
                } else {
                    TextButton(
                        onClick = {
                            selectedCourseLevel.value = null
                            onLeaveCurriculumLevel()
                        },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "返回关卡地图" }
                    ) { Text("← 返回关卡地图") }
                    CurriculumPlayerCard(
                        player = state.curriculumPlayer,
                        isPaused = state.isPaused,
                        isSafetyStopped = state.isSafetyStopped,
                        onAnswer = onAnswerCurriculumActivity,
                        onPause = onPause,
                        onResume = onResume,
                        onLeave = {
                            selectedCourseLevel.value = null
                            onLeaveCurriculumLevel()
                        }
                    )
                }
            } else if (selectedSection.value == ChildSection.CHAT) {
                ChatCard(
                    state = state,
                    onSend = onSendChatMessage,
                    onSelectProvider = onSelectChatProvider,
                    onOpenApiKey = onOpenApiKey
                )
            } else if (selectedSection.value == ChildSection.PROFILE) {
                RainbowProfileCard(state.rainbowProfile)
            } else {
                SupportCard(state)
                RewardCard(state)
                InterestCard(state, onInterestChange)
                PerceptionOverlay(
                    state = state,
                    onTogglePerception = onTogglePerception,
                    onTogglePreview = onTogglePerceptionPreview,
                    onAttachPreviewView = onAttachPerceptionPreviewView,
                    onDetachPreviewView = onDetachPerceptionPreviewView
                )
                AccessibilityCard(
                    accessibility = accessibility,
                    speechController = speechController,
                    onSpeechEnabledChange = onSpeechEnabledChange,
                    onSpeechRateChange = onSpeechRateChange,
                    onSpeechVolumeChange = onSpeechVolumeChange,
                    onLargeTextChange = onLargeTextChange,
                    onHighContrastChange = onHighContrastChange,
                    onSlowMotionChange = onSlowMotionChange
                )
            }
        }
    }
}

@Composable
private fun CurriculumPlayerCard(
    player: CurriculumPlayerUi,
    isPaused: Boolean,
    isSafetyStopped: Boolean,
    onAnswer: (Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLeave: () -> Unit
) {
    val question = player.question
    val isMemoryQuestion = question?.type == QuestionType.MEMORY
    val isPreviewing = remember(player.levelOrder, player.activityIndex, question?.id) {
        mutableStateOf(isMemoryQuestion && question?.stimulus?.isNotBlank() == true)
    }
    val previewRemainingMs = remember(player.levelOrder, player.activityIndex, question?.id) {
        mutableStateOf(question?.previewMs?.coerceAtLeast(1L) ?: 1L)
    }
    LaunchedEffect(player.levelOrder, player.activityIndex, question?.id) {
        val q = question ?: return@LaunchedEffect
        if (!isMemoryQuestion || q.stimulus.isBlank()) {
            isPreviewing.value = false
            previewRemainingMs.value = 0L
            return@LaunchedEffect
        }
        val total = q.previewMs.coerceAtLeast(1L)
        val deadline = System.currentTimeMillis() + total
        isPreviewing.value = true
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            previewRemainingMs.value = remaining
            delay(100L)
        }
        previewRemainingMs.value = 0L
        isPreviewing.value = false
    }
    SectionSurface(
        title = when {
            isSafetyStopped -> "先找身边的大人"
            isPaused -> "休息时间"
            player.finished -> "这一关结束了"
            isPreviewing.value -> "先记住这个示例"
            else -> player.levelTitle
        },
        supporting = player.message,
        containerColor = if (isSafetyStopped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    ) {
        if (isSafetyStopped) {
            Text("训练已经停止。请不要继续操作。", color = Error, style = MaterialTheme.typography.titleMedium)
        } else if (isPaused) {
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { contentDescription = "恢复儿童训练" },
                enabled = !player.isWorking
            ) { Text(if (player.isWorking) "请稍等" else "准备好了，继续") }
        } else if (player.finished) {
            Text(if (player.passed) "这一关通过了，可以去下一关了！" else "这一关结束，可以再试一次。", style = MaterialTheme.typography.titleMedium)
            Text("完成 ${player.runCompleted} / ${player.activityTotal} 个活动，答对 ${player.runCorrect} / ${player.runTotal} 题。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onLeave, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { contentDescription = "回到关卡地图" }) { Text("回到关卡地图") }
        } else if (question == null) {
            Text("这个活动暂时没有题目，请返回地图。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("第 ${player.levelOrder} 关 · 活动 ${player.activityIndex + 1} / ${player.activityTotal} · ${player.activityLabel}", style = MaterialTheme.typography.labelLarge)
            if (isPreviewing.value) {
                Text("请认真看一看，${((previewRemainingMs.value + 999L) / 1000L).coerceAtLeast(1L)} 秒后开始选择。")
                StimulusCard(question.stimulus, "记忆示例")
                LinearProgressIndicator(
                    progress = {
                        1f - (previewRemainingMs.value.toFloat() / question.previewMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                if (isMemoryQuestion != true && question.stimulus.isNotBlank()) StimulusCard(question.stimulus, "题目示例")
                Text(question.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    question.options.withIndex().toList().chunked(2).forEach { rowOptions ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            rowOptions.forEach { (optionIndex, option) ->
                                val buttonModifier = if (rowOptions.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f)
                                ChoiceButton(option, optionIndex, onAnswer, buttonModifier, !player.isWorking)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPause,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { contentDescription = "让儿童休息" },
                enabled = !player.isWorking
            ) { Text("先休息") }
            TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "暂时离开这一关" }) { Text("暂时离开这一关") }
        }
    }
}

@Composable
private fun StimulusCard(stimulus: String, label: String) {
    SectionSurface(title = label, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stimulus,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            style = enlargedVisualStyle(MaterialTheme.typography.headlineSmall, stimulus),
            textAlign = TextAlign.Center
        )
    }
}

/** Emoji/图形素材需要比普通文字更大，方便儿童在模拟器和实际设备上观察。 */
private fun enlargedVisualStyle(base: androidx.compose.ui.text.TextStyle, value: String): androidx.compose.ui.text.TextStyle =
    if (containsVisualMaterial(value)) {
        base.copy(fontSize = (base.fontSize.value * 2.5f).sp)
    } else {
        base
    }

private fun containsVisualMaterial(value: String): Boolean = value.any { character ->
    Character.isSurrogate(character) ||
        character in '\u2300'..'\u23FF' ||
        character in '\u2600'..'\u27BF'
}

@Composable
private fun CurriculumMapCard(
    map: CurriculumMapUi,
    courseUnlocked: Boolean,
    isWorking: Boolean,
    onChooseInterest: (String) -> Unit,
    onOpenLevel: (Int) -> Unit
) {
    SectionSurface(title = "关卡地图", supporting = "完成当前关卡后，下一关会自动解锁。") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("我的彩虹冒险", style = MaterialTheme.typography.titleLarge)
                Text("已完成 ${map.completedLevels} / ${map.totalLevels} 关", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${map.activeLevel} / ${map.totalLevels}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(
            progress = { map.completedLevels.toFloat() / map.totalLevels.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
        )
        Spacer(Modifier.height(8.dp))
        when {
            !courseUnlocked -> Text("完成六题起点小测后，就可以开始第一关。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            !map.interestChosen -> InterestGateway(map.interestOptions, onChooseInterest)
            else -> map.levels.forEach { level -> LevelButton(level, isWorking, onOpenLevel) }
        }
    }
}

@Composable
private fun InterestGateway(options: List<String>, onChooseInterest: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("先选一个喜欢的主题，小星会用这个主题陪你玩。", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { option ->
                OutlinedButton(onClick = { onChooseInterest(option) }, modifier = Modifier.weight(1f)) { Text(option) }
            }
        }
    }
}

@Composable
private fun LevelButton(level: CurriculumLevelUi, isWorking: Boolean, onOpenLevel: (Int) -> Unit) {
    val label = when (level.status) {
        CurriculumLevelStatus.COMPLETED -> "✓ ${level.icon} 第 ${level.order} 关 · ${level.title} · ${level.theme}"
        CurriculumLevelStatus.AVAILABLE -> "▶ ${level.icon} 第 ${level.order} 关 · ${level.title} · ${level.theme}"
        CurriculumLevelStatus.LOCKED -> "🔒 ${level.icon} 第 ${level.order} 关 · ${level.title}"
    }
    val clickable = level.status == CurriculumLevelStatus.AVAILABLE || level.status == CurriculumLevelStatus.COMPLETED
    if (clickable) {
        Button(
            onClick = { onOpenLevel(level.order) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 56.dp),
            enabled = !isWorking
        ) { Text(label) }
    } else {
        OutlinedButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 52.dp),
            enabled = false
        ) { Text(label) }
    }
}

@Composable
private fun SupportCard(state: ChildUiState) {
    SectionSurface(title = "今天的支持", containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        StatusLine("难度", "第 ${state.difficulty} 级")
        Spacer(Modifier.height(8.dp))
        StatusLine("支持", state.supportLevel.name)
        Spacer(Modifier.height(8.dp))
        StatusLine("最近状态", state.lastEvent)
        Spacer(Modifier.height(8.dp))
        Text("这里不展示分数和排名，只记录下一步需要多少支持。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RewardCard(state: ChildUiState) {
    SectionSurface(title = "我的小星星", supporting = "这里只记录练习过程中的鼓励，不用于比较或排名。") {
        StatusLine("小星星", "${state.coursePoints} 颗")
        Spacer(Modifier.height(8.dp))
        StatusLine("完成轮数", "${state.completedRounds} 轮")
        Spacer(Modifier.height(8.dp))
        StatusLine("最近感觉", state.encouragementTrend)
        Text(state.rewardMessage, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InterestCard(state: ChildUiState, onInterestChange: (String) -> Unit) {
    SectionSurface(title = "我喜欢的主题", supporting = "主题只用来调整示例素材，不改变训练目标。") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.interestOptions.forEach { option ->
                OutlinedButton(onClick = { onInterestChange(option) }, enabled = option != state.interest) { Text(option) }
            }
        }
        Text("当前主题：${state.interest}", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RainbowProfileCard(profile: RainbowProfileUi) {
    if (!profile.present) {
        SectionSurface(title = "🌈 我的彩虹画像", supporting = "完成六题起点小测后，会在这里生成你的彩虹画像。") {
            Text("先和小星做几个小游戏吧，画像会在这里等你。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    SectionSurface(title = "🌈 我的彩虹画像", supporting = "生成于 ${profile.createdLabel}") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            profile.domainBars.forEach { bar ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(bar.emoji, style = enlargedVisualStyle(MaterialTheme.typography.headlineSmall, bar.emoji))
                    Column(Modifier.weight(1f)) {
                        Text(bar.name, style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(
                            progress = { bar.score / 100f },
                            color = domainBarColor(bar.colorKey),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        )
                    }
                    Text("训练起点 ${bar.score}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Text("🤖", style = enlargedVisualStyle(MaterialTheme.typography.headlineSmall, "🤖"))
            Text(profile.narrative, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("这不是考试分数，每个人都有自己的游戏路线。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccessibilityCard(
    accessibility: AccessibilityUiState,
    speechController: ChildSpeechController,
    onSpeechEnabledChange: (Boolean) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechVolumeChange: (Float) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onSlowMotionChange: (Boolean) -> Unit
) {
    SectionSurface(title = "辅助设置", supporting = "设置只保存在本机，用来调整小星的呈现方式。") {
        SettingRow("小星朗读", "朗读儿童端短句", accessibility.speechEnabled) { onSpeechEnabledChange(it) }
        Spacer(Modifier.height(8.dp))
        Text("语速：${"%.2f".format(accessibility.speechRate)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = accessibility.speechRate,
            onValueChange = onSpeechRateChange,
            onValueChangeFinished = { if (accessibility.speechEnabled) speechController.speak("小星会用这个速度说话。") },
            valueRange = 0.75f..1.25f,
            steps = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Text("音量：${(accessibility.speechVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = accessibility.speechVolume,
            onValueChange = onSpeechVolumeChange,
            onValueChangeFinished = { if (accessibility.speechEnabled) speechController.speak("这是现在的朗读音量。") },
            valueRange = 0.5f..1.0f,
            steps = 4,
            modifier = Modifier.fillMaxWidth()
        )
        SettingRow("大字体", "增加界面文字大小", accessibility.largeText) { onLargeTextChange(it) }
        Spacer(Modifier.height(8.dp))
        SettingRow("高对比", "提高文字与表面的对比度", accessibility.highContrast) { onHighContrastChange(it) }
        Spacer(Modifier.height(8.dp))
        SettingRow("慢动效", "放慢页面变化，给更多反应时间", accessibility.slowMotion) { onSlowMotionChange(it) }
    }
}

private fun assetResource(key: String): Int = when (key) {
    "training_ball" -> R.drawable.training_ball
    "training_tree" -> R.drawable.training_tree
    "training_car" -> R.drawable.training_car
    "training_cup" -> R.drawable.training_cup
    "training_square" -> R.drawable.training_square
    "training_circle" -> R.drawable.training_circle
    "training_animal" -> R.drawable.training_animal
    "training_bird" -> R.drawable.training_bird
    else -> R.drawable.training_star
}

@Composable
private fun BaselineCard(
    state: BaselineUiState,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onLeave: () -> Unit,
    onRestart: () -> Unit,
    onAnswer: (Int) -> Unit
) {
    val question = state.question
    val isMemoryQuestion = question?.type == QuestionType.MEMORY
    val isPreviewing = remember(question?.id, state.isOpen, state.status) {
        mutableStateOf(state.isOpen && isMemoryQuestion && question?.stimulus?.isNotBlank() == true)
    }
    val previewRemainingMs = remember(question?.id, state.isOpen, state.status) {
        mutableStateOf(question?.previewMs?.coerceAtLeast(1L) ?: 1L)
    }
    LaunchedEffect(question?.id, state.isOpen, state.status) {
        if (!state.isOpen || !isMemoryQuestion || question?.stimulus.isNullOrBlank()) {
            isPreviewing.value = false
            previewRemainingMs.value = 0L
            return@LaunchedEffect
        }
        val total = question.previewMs.coerceAtLeast(1L)
        val deadline = System.currentTimeMillis() + total
        isPreviewing.value = true
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            previewRemainingMs.value = remaining
            delay(100L)
        }
        previewRemainingMs.value = 0L
        isPreviewing.value = false
    }
    SectionSurface(
        title = "六题起点小测",
        supporting = state.message,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        when (state.status) {
            BaselineStatus.NOT_STARTED, BaselineStatus.NEEDS_RETEST -> {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { contentDescription = "开始六题起点小测" }) {
                    Text("开始基线")
                }
            }
            BaselineStatus.IN_PROGRESS -> if (!state.isOpen) {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { contentDescription = "继续六题起点小测" }) {
                    Text("继续基线")
                }
            } else {
                question?.let { question ->
                    Text("${state.currentIndex + 1} / ${state.totalCount}", style = MaterialTheme.typography.labelLarge)
                    if (isPreviewing.value) {
                        Text("请记住下面的示例，${((previewRemainingMs.value + 999L) / 1000L).coerceAtLeast(1L)} 秒后开始选择。")
                        StimulusCard(question.stimulus, "记忆示例")
                        LinearProgressIndicator(
                            progress = {
                                1f - (previewRemainingMs.value.toFloat() / question.previewMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        Text(question.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                        if (!isMemoryQuestion && question.stimulus.isNotBlank()) StimulusCard(question.stimulus, "题目示例")
                        question.options.forEachIndexed { index, option ->
                            OutlinedButton(
                                onClick = { onAnswer(index) },
                                enabled = !state.isWorking,
                                modifier = Modifier.fillMaxWidth().heightIn(min = if (containsVisualMaterial(option)) 120.dp else 56.dp).semantics { contentDescription = "回答起点小测：$option" }
                            ) { Text(option, style = enlargedVisualStyle(MaterialTheme.typography.titleMedium, option)) }
                            Spacer(Modifier.height(8.dp))
                        }
                        TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "暂时离开六题起点小测" }) { Text("暂时离开基线") }
                    }
                }
            }
            BaselineStatus.COMPLETED -> {
                Text("已完成六题起点小测", style = MaterialTheme.typography.titleMedium)
                Text("记录的是过程表现，不是诊断或排名。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onRestart, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "重新开始六题起点小测" }) {
                    Text("重新开始")
                }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, supporting: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "$title 开关" }
        )
    }
}

@Composable
private fun ChoiceButton(label: String, optionIndex: Int, onChoice: (Int) -> Unit, modifier: Modifier, enabled: Boolean) {
    OutlinedButton(
        onClick = { onChoice(optionIndex) },
        modifier = modifier.heightIn(min = if (containsVisualMaterial(label)) 120.dp else 76.dp).semantics { contentDescription = "选择$label" },
        enabled = enabled
    ) {
        Text(label, style = enlargedVisualStyle(MaterialTheme.typography.titleLarge, label))
    }
}

// ===================== 交互模式聊天 =====================

@Composable
private fun ChatCard(
    state: ChildUiState,
    onSend: (String) -> Unit,
    onSelectProvider: (com.xingmou.core.llm.ChatLlmProvider) -> Unit,
    onOpenApiKey: () -> Unit
) {
    val input = remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val messages = state.chatMessages

    // 新消息自动滚到底
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    // 儿童端仅支持 DeepSeek：若历史选择是豆包/千问，自动切回
    LaunchedEffect(state.chatProvider) {
        if (state.chatProvider != com.xingmou.core.llm.ChatLlmProvider.DEEPSEEK) {
            onSelectProvider(com.xingmou.core.llm.ChatLlmProvider.DEEPSEEK)
        }
    }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 供应商选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("模型：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 儿童端交互仅保留 DeepSeek，豆包/千问不在此暴露
                com.xingmou.core.llm.ChatLlmProvider.entries
                    .filter { it == com.xingmou.core.llm.ChatLlmProvider.DEEPSEEK }
                    .forEach { provider ->
                    androidx.compose.material3.FilterChip(
                        selected = state.chatProvider == provider,
                        onClick = { onSelectProvider(provider) },
                        label = { Text(provider.label) }
                    )
                }
            }

            // 未配置提示：醒目引导用户去设置 API Key
            if (!state.chatProviderConfigured) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenApiKey() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "还未配置 ${state.chatProvider.label} 的 API Key，点这里去设置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 消息列表
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 420.dp)
                    .verticalScroll(scrollState)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    Text(state.chatHint, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                messages.forEach { msg ->
                    ChatBubble(msg)
                }
                if (state.chatLoading) {
                    Text("小星正在思考…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            // 快捷话题：点击即发送，免去儿童打字门槛
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.chatQuickTopics.forEach { topic ->
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.clickable(enabled = !state.chatLoading) { onSend(topic) }
                    ) {
                        Text(
                            topic,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 输入区
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = input.value,
                    onValueChange = { input.value = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("对小星说…") },
                    enabled = !state.chatLoading
                )
                Button(
                    onClick = {
                        val t = input.value
                        input.value = ""
                        onSend(t)
                    },
                    enabled = !state.chatLoading && input.value.isNotBlank()
                ) { Text("发送") }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: com.xingmou.ChatMessageUi) {
    val isUser = msg.role == "user"
    val avatar = if (isUser) "我" else "星"
    val avatarColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val avatarText = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val bubbleText = if (msg.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = avatarColor,
                modifier = Modifier.size(28.dp)
            ) {
                Text(avatar, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.titleSmall, color = avatarText, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.size(6.dp))
        }
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = bubbleText
            )
        }
        if (isUser) {
            Spacer(modifier = Modifier.size(6.dp))
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = avatarColor,
                modifier = Modifier.size(28.dp)
            ) {
                Text(avatar, modifier = Modifier.padding(2.dp), style = MaterialTheme.typography.titleSmall, color = avatarText, textAlign = TextAlign.Center)
            }
        }
    }
}
