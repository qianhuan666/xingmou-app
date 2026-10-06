package com.xingmou.ui.child

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingmou.AccessibilityUiState
import com.xingmou.AutoDetectState
import com.xingmou.ChildUiState
import com.xingmou.BaselineUiState
import com.xingmou.CurriculumLevelStatus
import com.xingmou.DetectMode
import com.xingmou.DetectPhase
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
    onResetCurriculum: () -> Unit,
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
    onDetectPermissionResolved: (Boolean) -> Unit,
    onDetectChooseManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speechController = remember(context) { ChildSpeechController(context) }
    val soundEffects = remember(context) { ChildSoundEffects() }
    // 儿童端默认进入交互模式：先由小星给出「聊天 / 做游戏」两个选择
    val selectedSection = remember { mutableStateOf(ChildSection.CHAT) }
    val greetingChoiceMade = remember { mutableStateOf(false) }
    // 开场选择放大转场：色块从按钮位置铺满全屏后切换，再淡出（与进入儿童端的头像特效同款）
    val choiceExpand = remember { mutableStateOf<ChoiceExpand?>(null) }
    val choiceScale = remember { Animatable(1f) }
    val choiceAlpha = remember { Animatable(1f) }
    val choiceScreenSize = remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    LaunchedEffect(choiceExpand.value) {
        val target = choiceExpand.value ?: return@LaunchedEffect
        val w = choiceScreenSize.value.width
        val h = choiceScreenSize.value.height
        val cover = if (w > 0 && h > 0 && target.sizePx > 0f) {
            (kotlin.math.sqrt((w * w + h * h).toFloat()) / target.sizePx) * 1.2f
        } else 14f
        choiceAlpha.snapTo(1f)
        choiceScale.snapTo(1f)
        choiceScale.animateTo(cover, tween(520, easing = FastOutSlowInEasing))
        target.onDone()
        choiceAlpha.animateTo(0f, tween(320))
        choiceExpand.value = null
    }
    val selectedCourseLevel = remember { mutableStateOf<Int?>(null) }
    // 消消乐式关卡地图是独立全屏界面：点侧栏「训练」或聊天页「做游戏」直接进入
    val mapImmersive = remember { mutableStateOf(false) }
    val enterMap = {
        selectedSection.value = ChildSection.TRAINING
        mapImmersive.value = true
    }
    val exitMap = {
        mapImmersive.value = false
        selectedSection.value = ChildSection.CHAT
        greetingChoiceMade.value = true
    }
    // 聊天页右上角低调的「更多」侧滑面板：画报 / 设置（儿童主界面不显示导航栏，低频功能藏这里）
    val morePanelOpen = remember { mutableStateOf(false) }
    DisposableEffect(speechController) {
        onDispose { speechController.shutdown() }
    }
    DisposableEffect(soundEffects) {
        onDispose { soundEffects.release() }
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
        val q = curriculumQuestion ?: return@LaunchedEffect
        if (!accessibility.speechEnabled) return@LaunchedEffect
        // 听力题读刺激文本，其他题型读题干
        val text = if (q.type == QuestionType.AUDIO) q.stimulus.ifBlank { q.prompt } else q.prompt
        if (text.isNotBlank()) speechController.speak(text)
    }
    // 基线题目同样朗读题干（听力题读刺激文本）
    val baselineQuestion = baseline.question
    LaunchedEffect(baselineQuestion?.id, baselineQuestion?.type, accessibility.speechEnabled) {
        val q = baselineQuestion ?: return@LaunchedEffect
        if (!accessibility.speechEnabled) return@LaunchedEffect
        val text = if (q.type == QuestionType.AUDIO) q.stimulus.ifBlank { q.prompt } else q.prompt
        if (text.isNotBlank()) speechController.speak(text)
    }
    // 答题音效：作答瞬间即播；记录已播时间戳，避免重新组合时重放上一题的结果
    val playedAnswerAt = remember { mutableStateOf(0L) }
    LaunchedEffect(state.lastAnswerAt) {
        if (state.lastAnswerAt == 0L || state.lastAnswerAt == playedAnswerAt.value) return@LaunchedEffect
        playedAnswerAt.value = state.lastAnswerAt
        when (state.lastAnswerCorrect) {
            true -> soundEffects.correct()
            false -> soundEffects.wrong()
            null -> Unit
        }
    }
    // 感知反馈温和语音提醒：排队播报，不打断正在朗读的题目
    LaunchedEffect(state.perceptionFeedback) {
        if (accessibility.speechEnabled && state.perceptionFeedback.isNotBlank()) {
            speechController.speakQueued(state.perceptionFeedback)
        }
    }
    // 沉浸空间判断：基线进行中 或 进入了课程关卡
    val baselineImmersive = baseline.isOpen && baseline.status == BaselineStatus.IN_PROGRESS
    val courseImmersive = selectedCourseLevel.value != null

    Box(modifier = modifier.fillMaxSize().onSizeChanged { choiceScreenSize.value = it }) {
      Row(modifier = Modifier.fillMaxSize()) {
        // 交互模式为独立全屏形态：不显示导航栏，由开场两个选项（聊天/做游戏）决定去向
        if (selectedSection.value != ChildSection.CHAT) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text("小星", modifier = Modifier.padding(vertical = 20.dp), style = MaterialTheme.typography.titleMedium)
            NavigationRailItem(
                selected = selectedSection.value == ChildSection.TRAINING,
                onClick = { enterMap() },
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
        }

        // 交互模式不滚动：消息列表占满剩余高度、输入区固定底部（聊天应用标准布局）
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .then(if (selectedSection.value == ChildSection.CHAT) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                XiaoXingMark(Modifier.size(52.dp))
                Column(Modifier.weight(1f)) {
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
                if (selectedSection.value == ChildSection.CHAT) {
                    androidx.compose.material3.Surface(
                        onClick = { morePanelOpen.value = true },
                        shape = CircleShape,
                        color = androidx.compose.ui.graphics.Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.xingmou.ui.theme.Rule)
                    ) {
                        Text(
                            "···",
                            style = MaterialTheme.typography.titleLarge,
                            color = com.xingmou.ui.theme.Ink.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                .semantics { contentDescription = "更多功能：画报和设置" }
                        )
                    }
                }
            }

            if (selectedSection.value == ChildSection.TRAINING && !mapImmersive.value) {
                // 全屏地图打开时训练主页内容不渲染（地图是独立界面）；预览也让给沉浸空间
                if (!baselineImmersive && !courseImmersive) {
                    PerceptionHomePreview(
                        state = state,
                        onAttachPreviewView = onAttachPerceptionPreviewView,
                        onDetachPreviewView = onDetachPerceptionPreviewView
                    )
                }
                if (!baselineImmersive) {
                    BaselineCard(
                        baseline,
                        onStartBaseline,
                        onResumeBaseline,
                        onLeaveBaseline,
                        onRestartBaseline,
                        onBaselineAnswer,
                        autoDetect = state.autoDetect,
                        onDetectPermissionResult = onDetectPermissionResolved,
                        onDetectManual = onDetectChooseManual
                    )
                }
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
                    // 关卡播放由全屏沉浸空间承载，此处不重复渲染
                    Text("小星正在准备这一关…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (selectedSection.value == ChildSection.CHAT) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ChatCard(
                        state = state,
                        greetingChoiceMade = greetingChoiceMade,
                        onPlayGame = { enterMap() },
                        onChoiceBegin = { color, cx, cy, sizePx, onDone ->
                            if (choiceExpand.value == null) {
                                choiceExpand.value = ChoiceExpand(color, cx, cy, sizePx, onDone)
                            }
                        },
                        onSend = onSendChatMessage,
                        onSelectProvider = onSelectChatProvider,
                        onOpenApiKey = onOpenApiKey
                    )
                }
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

      // 开场选择放大转场覆盖层：按钮色块从原位铺满全屏，到位后新界面已切换，覆盖层淡出
      choiceExpand.value?.let { target ->
          val base = with(androidx.compose.ui.platform.LocalDensity.current) { target.sizePx.toDp() }
          Box(modifier = Modifier.fillMaxSize()) {
              Box(
                  modifier = Modifier
                      .offset {
                          IntOffset(
                              (target.centerX - target.sizePx / 2f).toInt(),
                              (target.centerY - target.sizePx / 2f).toInt()
                          )
                      }
                      .size(base)
                      .scale(choiceScale.value)
                      .alpha(choiceAlpha.value)
                      .background(target.color)
              )
          }
      }

      // 「更多」侧滑面板：从聊天页右上角进入画报/设置（侧栏在全屏聊天中不显示）
      if (morePanelOpen.value) {
          val panelProgress = remember { Animatable(0f) }
          LaunchedEffect(Unit) { panelProgress.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
          val closePanel = {
              morePanelOpen.value = false
          }
          Box(
              Modifier.fillMaxSize()
                  .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f * panelProgress.value))
                  .clickable(
                      interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                      indication = null,
                      onClick = closePanel
                  )
          ) {
              androidx.compose.material3.Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                  shadowElevation = 12.dp,
                  modifier = Modifier
                      .align(Alignment.CenterEnd)
                      .fillMaxHeight()
                      .width(320.dp)
                      .offset {
                          IntOffset(
                              ((1f - panelProgress.value) * 320.dp.toPx()).toInt(),
                              0
                          )
                      }
                      .clickable(
                          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                          indication = null,
                          onClick = {}
                      )
              ) {
                  Column(
                      Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                      verticalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                      Row(
                          Modifier.fillMaxWidth().padding(bottom = 8.dp),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                      ) {
                          Text("更多", style = MaterialTheme.typography.headlineSmall)
                          TextButton(onClick = closePanel) { Text("关闭") }
                      }
                      androidx.compose.material3.Surface(
                          onClick = {
                              morePanelOpen.value = false
                              selectedSection.value = ChildSection.PROFILE
                          },
                          shape = RoundedCornerShape(20.dp),
                          color = MaterialTheme.colorScheme.secondaryContainer
                      ) {
                          Column(Modifier.fillMaxWidth().padding(20.dp)) {
                              Text("我的彩虹画像", style = MaterialTheme.typography.titleLarge)
                              Text(
                                  "小星的游戏足迹，不是考试分数。",
                                  style = MaterialTheme.typography.bodyMedium,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  modifier = Modifier.padding(top = 4.dp)
                              )
                          }
                      }
                      androidx.compose.material3.Surface(
                          onClick = {
                              morePanelOpen.value = false
                              selectedSection.value = ChildSection.SETTINGS
                          },
                          shape = RoundedCornerShape(20.dp),
                          color = MaterialTheme.colorScheme.secondaryContainer
                      ) {
                          Column(Modifier.fillMaxWidth().padding(20.dp)) {
                              Text("儿童端设置", style = MaterialTheme.typography.titleLarge)
                              Text(
                                  "朗读、语速、字号、感知守护等呈现方式。",
                                  style = MaterialTheme.typography.bodyMedium,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                                  modifier = Modifier.padding(top = 4.dp)
                              )
                          }
                      }
                  }
              }
          }
      }

      // ===== 儿童专属沉浸测试空间（全屏 Dialog，盖住顶栏和导航） =====
      when {
          baselineImmersive -> ImmersiveTestDialog(
              exitLabel = "暂时离开",
              onExit = onLeaveBaseline,
              state = state,
              onAttachPreviewView = onAttachPerceptionPreviewView,
              onDetachPreviewView = onDetachPerceptionPreviewView
          ) {
              BaselineCard(
                  baseline,
                  onStartBaseline,
                  onResumeBaseline,
                  onLeaveBaseline,
                  onRestartBaseline,
                  onBaselineAnswer,
                  autoDetect = state.autoDetect,
                  onDetectPermissionResult = onDetectPermissionResolved,
                  onDetectManual = onDetectChooseManual,
                  immersive = true
              )
          }
          courseImmersive -> ImmersiveTestDialog(
              exitLabel = "返回关卡地图",
              onExit = {
                  selectedCourseLevel.value = null
                  onLeaveCurriculumLevel()
              },
              state = state,
              onAttachPreviewView = onAttachPerceptionPreviewView,
              onDetachPreviewView = onDetachPerceptionPreviewView
          ) {
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
                  },
                  autoDetect = state.autoDetect,
                  onDetectPermissionResult = onDetectPermissionResolved,
                  onDetectManual = onDetectChooseManual
              )
          }
          // 全屏关卡地图：优先级最低，基线小测 / 关卡练习打开时自然盖在地图之上
          mapImmersive.value -> CurriculumMapDialog(
              map = state.curriculumMap,
              courseUnlocked = state.courseUnlocked,
              isWorking = state.isWorking,
              onBack = exitMap,
              onStartBaseline = onStartBaseline,
              onChooseInterest = onChooseCurriculumInterest,
              onReset = {
                  selectedCourseLevel.value = null
                  onResetCurriculum()
              },
              onOpenLevel = { level ->
                  selectedCourseLevel.value = level
                  onOpenCurriculumLevel(level)
              }
          )
      }
    }
}

/**
 * 全屏 Dialog 承载沉浸空间：无变暗、透明窗口、延伸到状态栏下，返回键等同于退出。
 */
@Composable
private fun ImmersiveTestDialog(
    exitLabel: String,
    onExit: () -> Unit,
    state: ChildUiState,
    onAttachPreviewView: (androidx.camera.view.PreviewView) -> Unit,
    onDetachPreviewView: (androidx.camera.view.PreviewView) -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onExit,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.DisposableEffect(Unit) {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            window?.let {
                it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.setDimAmount(0f)
                it.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            onDispose { }
        }
        ImmersiveTestSpace(
            exitLabel = exitLabel,
            onExit = onExit,
            state = state,
            onAttachPreviewView = onAttachPreviewView,
            onDetachPreviewView = onDetachPreviewView,
            content = content
        )
    }
}

/**
 * 全屏关卡地图（独立界面）：天空背景上的消消乐式蜿蜒地图。
 * 基线小测与关卡练习的沉浸 Dialog 打开时会盖在本层之上，退出后自然回到地图。
 */
@Composable
private fun CurriculumMapDialog(
    map: CurriculumMapUi,
    courseUnlocked: Boolean,
    isWorking: Boolean,
    onBack: () -> Unit,
    onStartBaseline: () -> Unit,
    onChooseInterest: (String) -> Unit,
    onReset: () -> Unit,
    onOpenLevel: (Int) -> Unit
) {
    val showResetConfirm = remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onBack,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            window?.let {
                it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                it.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                it.setDimAmount(0f)
                it.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            onDispose { }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            androidx.compose.ui.graphics.Color(0xFF9ED8FF),
                            androidx.compose.ui.graphics.Color(0xFFCDEFF0),
                            androidx.compose.ui.graphics.Color(0xFFFFF2C4)
                        )
                    )
                )
        ) {
            SkyDecorations()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp)
            ) {
                // 顶栏：返回 + 进度
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(28.dp),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.xingmou.ui.theme.Rule)
                    ) {
                        Text(
                            "← 返回",
                            style = MaterialTheme.typography.titleMedium,
                            color = com.xingmou.ui.theme.Ink,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("我的彩虹冒险", style = MaterialTheme.typography.titleLarge, color = com.xingmou.ui.theme.Ink)
                            Text(
                                "已点亮 ${map.completedLevels} / ${map.totalLevels} 关",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.xingmou.ui.theme.Ink.copy(alpha = 0.7f)
                            )
                        }
                        // 家长重置入口：低调的圆形小按钮，点击需二次确认
                        androidx.compose.material3.Surface(
                            onClick = { showResetConfirm.value = true },
                            shape = CircleShape,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, com.xingmou.ui.theme.Rule)
                        ) {
                            Text(
                                "↺",
                                style = MaterialTheme.typography.titleLarge,
                                color = com.xingmou.ui.theme.Ink.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    .semantics { contentDescription = "重新开始：清空小测和全部关卡进度" }
                            )
                        }
                    }
                }

                // 地图滚动区：第 1 关在底部，进入时直接停在最下面
                val scrollState = rememberScrollState()
                LaunchedEffect(map.totalLevels, courseUnlocked, map.interestChosen) {
                    delay(120)
                    scrollState.scrollTo(scrollState.maxValue)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CurriculumTrailMap(
                            levels = map.levels,
                            isWorking = isWorking,
                            onOpenLevel = onOpenLevel,
                            onLockedTap = { order -> if (order == 1 && !courseUnlocked) onStartBaseline() },
                            immersive = true
                        )
                    }
                    // 兴趣选择：白卡浮在地图中央，选好主题后消失（基线完成前后都可以选）
                    if (!map.interestChosen) {
                        Box(
                            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = androidx.compose.ui.graphics.Color.White,
                                shadowElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth(0.82f).padding(24.dp)
                            ) {
                                Column(Modifier.padding(28.dp)) {
                                    InterestGateway(map.interestOptions, onChooseInterest)
                                }
                            }
                        }
                    }
                }

                // 基线未完成：底部温和提示 + 开始小测按钮
                // 底部留出系统导航栏空间（全屏 Dialog 的 safeDrawing inset 在部分设备上为 0）
                if (!courseUnlocked) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = com.xingmou.ui.theme.CoralSoft,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 56.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "先完成六题起点小测，第 1 关就会打开。",
                                style = MaterialTheme.typography.titleMedium,
                                color = com.xingmou.ui.theme.CoralDark,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.Button(
                                onClick = onStartBaseline,
                                enabled = !isWorking,
                                shape = RoundedCornerShape(24.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = com.xingmou.ui.theme.Coral
                                )
                            ) { Text("开始小测", modifier = Modifier.padding(horizontal = 8.dp)) }
                        }
                    }
                }
            }

            // 重置二次确认（防误触）：清空后需重新做起点小测
            if (showResetConfirm.value) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetConfirm.value = false },
                    title = { Text("重新开始？") },
                    text = { Text("会清空起点小测结果、已点亮的关卡和星星，第 1 关会重新锁上，需要再做一次小测。这个操作不能撤销。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showResetConfirm.value = false
                                onReset()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = com.xingmou.ui.theme.CoralDark
                            )
                        ) { Text("全部清空，重新开始") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showResetConfirm.value = false }) {
                            Text("再想想")
                        }
                    }
                )
            }
        }
    }
}

/**
 * 儿童测试沉浸空间：柔和天空渐变 + 太阳云朵，题目卡片浮在中间。
 */
@Composable
private fun ImmersiveTestSpace(
    exitLabel: String,
    onExit: () -> Unit,
    state: ChildUiState,
    onAttachPreviewView: (androidx.camera.view.PreviewView) -> Unit,
    onDetachPreviewView: (androidx.camera.view.PreviewView) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFF9ED8FF),
                        androidx.compose.ui.graphics.Color(0xFFCDEFF0),
                        androidx.compose.ui.graphics.Color(0xFFFFF2C4)
                    )
                )
            )
    ) {
        SkyDecorations()
        // 左上角固定的小窗口预览，与主页圆形预览同款；感知关闭或预览关闭时不显示
        PerceptionHomePreview(
            state = state,
            onAttachPreviewView = onAttachPreviewView,
            onDetachPreviewView = onDetachPreviewView,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 16.dp, top = 4.dp),
            size = 140.dp
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.heightIn(min = 52.dp).semantics { contentDescription = exitLabel }
                ) { Text("← $exitLabel", style = MaterialTheme.typography.titleMedium) }
            }
            val glassShape = RoundedCornerShape(36.dp)
            Box(modifier = Modifier.fillMaxWidth(0.86f)) {
                // 假投影：卡片下方三层偏移填充块（被卡片本体遮挡，仅下缘可见），兼容所有渲染器
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(y = 10.dp)
                        .background(androidx.compose.ui.graphics.Color(0x1233506B), glassShape)
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(y = 20.dp)
                        .background(androidx.compose.ui.graphics.Color(0x0D33506B), glassShape)
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .offset(y = 30.dp)
                        .background(androidx.compose.ui.graphics.Color(0x0833506B), glassShape)
                )
                // 玻璃卡片本体
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(glassShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.80f),
                                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.60f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                            glassShape
                        )
                        .padding(26.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/** 太阳和白云装饰，纯 Canvas 绘制，安静不抢眼。 */
@Composable
private fun SkyDecorations() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        // 太阳光晕 + 太阳（右上）
        drawCircle(androidx.compose.ui.graphics.Color(0xFFFFF3C4), radius = 150f, center = androidx.compose.ui.geometry.Offset(w - 170f, 170f))
        drawCircle(androidx.compose.ui.graphics.Color(0xFFFFE082), radius = 85f, center = androidx.compose.ui.geometry.Offset(w - 170f, 170f))
        cloud(w * 0.16f, 220f, 1.1f)
        cloud(w * 0.52f, 130f, 0.8f)
        cloud(w * 0.8f, 420f, 0.9f)
    }
}

/** 云朵（白色半透明，圆叠加成云形），供天空装饰与闯关地图复用。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.cloud(cx: Float, cy: Float, s: Float) {
    val cloudColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f)
    drawCircle(cloudColor, radius = 46f * s, center = androidx.compose.ui.geometry.Offset(cx - 42f * s, cy + 10f * s))
    drawCircle(cloudColor, radius = 62f * s, center = androidx.compose.ui.geometry.Offset(cx, cy - 12f * s))
    drawCircle(cloudColor, radius = 44f * s, center = androidx.compose.ui.geometry.Offset(cx + 46f * s, cy + 12f * s))
    drawCircle(cloudColor, radius = 52f * s, center = androidx.compose.ui.geometry.Offset(cx + 8f * s, cy + 18f * s))
}

@Composable
private fun CurriculumPlayerCard(
    player: CurriculumPlayerUi,
    isPaused: Boolean,
    isSafetyStopped: Boolean,
    onAnswer: (Int) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onLeave: () -> Unit,
    autoDetect: AutoDetectState,
    onDetectPermissionResult: (Boolean) -> Unit,
    onDetectManual: () -> Unit
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
        containerColor = if (isSafetyStopped) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        immersive = true
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
                StimulusCard(question.stimulus, "记忆示例", immersive = true)
                LinearProgressIndicator(
                    progress = {
                        1f - (previewRemainingMs.value.toFloat() / question.previewMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
            } else {
                if (isMemoryQuestion != true && question.stimulus.isNotBlank()) StimulusCard(question.stimulus, "题目示例", immersive = true)
                Text(question.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                ObservedAnswerArea(
                    question = question,
                    detect = autoDetect,
                    isWorking = player.isWorking,
                    onAnswer = onAnswer,
                    onPermissionResult = onDetectPermissionResult,
                    onManual = onDetectManual
                ) {
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
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPause,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { contentDescription = "让儿童休息" },
                enabled = !player.isWorking
            ) { Text("先休息") }
        }
    }
}

@Composable
private fun StimulusCard(stimulus: String, label: String, immersive: Boolean = false) {
    SectionSurface(title = label, containerColor = MaterialTheme.colorScheme.secondaryContainer, immersive = immersive) {
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
            else -> CurriculumTrailMap(map.levels, isWorking, onOpenLevel)
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

/** 已通关节点的薄荷青底色。 */
private val TrailDone = androidx.compose.ui.graphics.Color(0xFF4FB8A8)

/** 消消乐式蜿蜒闯关地图：天空渐变背景上，糖果珠链串起 S 形路径的圆形关卡节点，第 1 关在底部向上闯关。 */
@Composable
private fun CurriculumTrailMap(
    levels: List<CurriculumLevelUi>,
    isWorking: Boolean,
    onOpenLevel: (Int) -> Unit,
    onLockedTap: ((Int) -> Unit)? = null,
    immersive: Boolean = false
) {
    val gap = 150.dp
    val swing = listOf(0.5f, 0.72f, 0.88f, 0.72f, 0.5f, 0.28f, 0.12f, 0.28f)
    val pulse = rememberInfiniteTransition(label = "trailPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "trailPulse"
    ).value
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(gap * (levels.size + 1))
            .then(
                if (immersive) Modifier
                else Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color(0xFF9ED8FF),
                                androidx.compose.ui.graphics.Color(0xFFCDEFF0),
                                androidx.compose.ui.graphics.Color(0xFFFFF2C4)
                            )
                        )
                    )
            )
    ) {
        if (!immersive) SkyDecorations()
        val w = maxWidth
        val xOf = { i: Int -> (w.value * swing[i % swing.size]).dp }
        val yOf = { i: Int -> gap * (levels.size - i) }

        // 沿途零散云朵，让长地图不空
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height
            cloud(size.width * 0.2f, h * 0.22f, 1.0f)
            cloud(size.width * 0.75f, h * 0.38f, 0.75f)
            cloud(size.width * 0.3f, h * 0.55f, 0.9f)
            cloud(size.width * 0.68f, h * 0.72f, 0.8f)
            cloud(size.width * 0.25f, h * 0.9f, 1.05f)
        }

        // 节点之间的糖果珠链：两端都通关为珊瑚珠，其余为云灰珠
        Canvas(Modifier.fillMaxSize()) {
            levels.forEachIndexed { i, level ->
                if (i == levels.lastIndex) return@forEachIndexed
                val a = androidx.compose.ui.geometry.Offset(size.width * swing[i % swing.size], yOf(i).toPx())
                val b = androidx.compose.ui.geometry.Offset(size.width * swing[(i + 1) % swing.size], yOf(i + 1).toPx())
                val mid = androidx.compose.ui.geometry.Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                val solid = level.status == CurriculumLevelStatus.COMPLETED &&
                    levels[i + 1].status == CurriculumLevelStatus.COMPLETED
                val coreColor = if (solid) com.xingmou.ui.theme.Coral else androidx.compose.ui.graphics.Color(0xFFB8C4D4)
                val steps = 18
                for (t in 0..steps) {
                    if (t % 2 == 0) continue
                    val tt = t / steps.toFloat()
                    val omt = 1f - tt
                    val x = omt * omt * a.x + 2f * omt * tt * mid.x + tt * tt * b.x
                    val y = omt * omt * a.y + 2f * omt * tt * mid.y + tt * tt * b.y
                    drawCircle(
                        androidx.compose.ui.graphics.Color.White,
                        radius = 10f,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                    drawCircle(
                        coreColor,
                        radius = 6f,
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }
        }

        // 顶点装饰：彩虹城堡（第 20 关上方）
        Text(
            "🏰",
            fontSize = 40.sp,
            modifier = Modifier.offset(x = (w.value * 0.5f).dp - 24.dp, y = gap * 0.32f)
        )

        levels.forEachIndexed { index, level ->
            val canOpen = (level.status == CurriculumLevelStatus.AVAILABLE ||
                level.status == CurriculumLevelStatus.COMPLETED) && !isWorking
            // 第 1 关未解锁时允许点击（承接基线小测入口）
            val lockedTapEnabled = level.status == CurriculumLevelStatus.LOCKED &&
                level.order == 1 && onLockedTap != null && !isWorking
            val canTap = canOpen || lockedTapEnabled
            val nodeSize = when (level.status) {
                CurriculumLevelStatus.AVAILABLE -> 96.dp
                CurriculumLevelStatus.COMPLETED -> 76.dp
                CurriculumLevelStatus.LOCKED -> if (lockedTapEnabled) 84.dp else 64.dp
            }
            val circleColor = when (level.status) {
                CurriculumLevelStatus.COMPLETED -> TrailDone
                CurriculumLevelStatus.AVAILABLE -> com.xingmou.ui.theme.Coral
                CurriculumLevelStatus.LOCKED -> androidx.compose.ui.graphics.Color(0xFFE3E7EE)
            }
            Column(
                modifier = Modifier
                    .offset(x = xOf(index) - nodeSize / 2, y = yOf(index) - nodeSize / 2)
                    .width(nodeSize)
                    .then(if (canTap) Modifier.clickable {
                        if (lockedTapEnabled) onLockedTap?.invoke(level.order) else onOpenLevel(level.order)
                    } else Modifier)
                    .semantics {
                        contentDescription = when (level.status) {
                            CurriculumLevelStatus.COMPLETED -> "第 ${level.order} 关 ${level.title}，已完成"
                            CurriculumLevelStatus.AVAILABLE -> "第 ${level.order} 关 ${level.title}，可以开始"
                            CurriculumLevelStatus.LOCKED ->
                                if (lockedTapEnabled) "第 1 关还没解锁，点这里先做起点小测"
                                else "第 ${level.order} 关，还没解锁"
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (level.status == CurriculumLevelStatus.AVAILABLE) {
                        // 当前关呼吸光圈
                        Box(
                            Modifier
                                .size(nodeSize + 22.dp)
                                .scale(1f + pulse * 0.04f)
                                .clip(CircleShape)
                                .background(com.xingmou.ui.theme.Coral.copy(alpha = 0.20f + 0.16f * pulse))
                        )
                    }
                    if (lockedTapEnabled) {
                        // 待解锁的第 1 关：淡珊瑚光圈，提示可点
                        Box(
                            Modifier
                                .size(nodeSize + 20.dp)
                                .scale(1f + pulse * 0.05f)
                                .clip(CircleShape)
                                .background(com.xingmou.ui.theme.Coral.copy(alpha = 0.14f + 0.14f * pulse))
                        )
                    }
                    androidx.compose.material3.Surface(
                        shape = CircleShape,
                        color = circleColor,
                        border = androidx.compose.foundation.BorderStroke(3.dp, androidx.compose.ui.graphics.Color.White),
                        shadowElevation = 4.dp
                    ) {
                        Box(Modifier.size(nodeSize), contentAlignment = Alignment.Center) {
                            Text(
                                if (level.status == CurriculumLevelStatus.LOCKED) "🔒" else level.icon,
                                fontSize = when (level.status) {
                                    CurriculumLevelStatus.AVAILABLE -> 38.sp
                                    CurriculumLevelStatus.COMPLETED -> 30.sp
                                    CurriculumLevelStatus.LOCKED -> 26.sp
                                }
                            )
                        }
                    }
                    // 小星站在当前关顶上（对应消消乐停在当前关的小船）
                    if (level.status == CurriculumLevelStatus.AVAILABLE) {
                        Text(
                            "⭐",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = -(nodeSize / 2 + 6.dp) - 4.dp * pulse)
                        )
                    }
                }
                when (level.status) {
                    CurriculumLevelStatus.COMPLETED -> Text(
                        "★".repeat(level.stars) + "☆".repeat((3 - level.stars).coerceAtLeast(0)),
                        color = androidx.compose.ui.graphics.Color(0xFFFFB300),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    CurriculumLevelStatus.AVAILABLE -> Text(
                        "第 ${level.order} 关",
                        color = com.xingmou.ui.theme.Ink,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    CurriculumLevelStatus.LOCKED -> if (lockedTapEnabled) {
                        Text(
                            "点我做小测",
                            color = com.xingmou.ui.theme.Coral,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else {}
                }
            }
        }
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
    onAnswer: (Int) -> Unit,
    autoDetect: AutoDetectState,
    onDetectPermissionResult: (Boolean) -> Unit,
    onDetectManual: () -> Unit,
    immersive: Boolean = false
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
        containerColor = if (immersive) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.tertiaryContainer,
        immersive = immersive
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
                        StimulusCard(question.stimulus, "记忆示例", immersive)
                        LinearProgressIndicator(
                            progress = {
                                1f - (previewRemainingMs.value.toFloat() / question.previewMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    } else {
                        Text(question.prompt, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                        if (!isMemoryQuestion && question.stimulus.isNotBlank()) StimulusCard(question.stimulus, "题目示例", immersive)
                        ObservedAnswerArea(
                            question = question,
                            detect = autoDetect,
                            isWorking = state.isWorking,
                            onAnswer = onAnswer,
                            onPermissionResult = onDetectPermissionResult,
                            onManual = onDetectManual
                        ) {
                            question.options.forEachIndexed { index, option ->
                                OutlinedButton(
                                    onClick = { onAnswer(index) },
                                    enabled = !state.isWorking,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = if (containsVisualMaterial(option)) 120.dp else 56.dp).semantics { contentDescription = "回答起点小测：$option" }
                                ) { Text(option, style = enlargedVisualStyle(MaterialTheme.typography.titleMedium, option)) }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        if (!immersive) {
                            TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "暂时离开六题起点小测" }) { Text("暂时离开基线") }
                        }
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

// ===================== 观察题自动检测 =====================

/**
 * 观察题作答区：非检测题/无会话时渲染普通选项（normalContent，保持各卡片原布局）；
 * 检测会话激活时按相位渲染权限门、检测面板或「提示 + 手动选项」兜底。
 */
@Composable
private fun ObservedAnswerArea(
    question: com.xingmou.data.catalog.QuestionDefinition,
    detect: AutoDetectState,
    isWorking: Boolean,
    onAnswer: (Int) -> Unit,
    onPermissionResult: (Boolean) -> Unit,
    onManual: () -> Unit,
    normalContent: @Composable () -> Unit
) {
    val detectable = question.expectedAction != null || question.expectedSpeech != null
    val active = detectable && detect.questionId == question.id
    if (!active || detect.phase == DetectPhase.IDLE) {
        normalContent()
        return
    }
    when (detect.phase) {
        DetectPhase.AWAIT_PERMISSION -> QuestionPermissionGate(
            permission = detect.permission,
            onResult = onPermissionResult,
            onManual = onManual
        )
        DetectPhase.DETECTING, DetectPhase.RETRYING, DetectPhase.HIT -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            DetectPanel(detect)
            if (detect.phase != DetectPhase.HIT) {
                TextButton(onClick = onManual) { Text("我想用手点") }
            }
        }
        DetectPhase.MANUAL_FALLBACK, DetectPhase.IDLE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detect.hint, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            normalContent()
        }
    }
}

/** 检测中面板：呼吸光圈 + 模式 emoji + 提示语 + 倒计时进度条。 */
@Composable
private fun DetectPanel(detect: AutoDetectState) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "breath")
    val scale by infinite.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "scale"
    )
    val ringColor = when (detect.phase) {
        DetectPhase.HIT -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        DetectPhase.RETRYING -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.primary
    }
    val emoji = when {
        detect.phase == DetectPhase.HIT -> "✅"
        detect.mode == DetectMode.SPEECH -> "🎤"
        else -> "👏"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(132.dp)) {
                drawCircle(
                    color = ringColor.copy(alpha = 0.25f),
                    radius = size.minDimension / 2f * if (detect.phase == DetectPhase.HIT) 1f else scale
                )
            }
            Text(emoji, fontSize = 56.sp)
        }
        Text(detect.hint, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (detect.phase == DetectPhase.DETECTING || detect.phase == DetectPhase.RETRYING) {
            val progress = if (detect.timeoutMs > 0)
                detect.remainingMs.toFloat() / detect.timeoutMs else 0f
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ===================== 交互模式聊天 =====================

@Composable
private fun ChatCard(
    state: ChildUiState,
    greetingChoiceMade: androidx.compose.runtime.MutableState<Boolean>,
    onPlayGame: () -> Unit,
    onChoiceBegin: (androidx.compose.ui.graphics.Color, Float, Float, Float, () -> Unit) -> Unit,
    onSend: (String) -> Unit,
    onSelectProvider: (com.xingmou.core.llm.ChatLlmProvider) -> Unit,
    onOpenApiKey: () -> Unit
) {
    val input = remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val messages = state.chatMessages
    val context = LocalContext.current

    // ---- 语音输入：系统 RecognitionService，不可用时打字兜底 ----
    val micGranted = remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val micHint = remember { mutableStateOf<String?>(null) }
    val micListening = remember { mutableStateOf(false) }
    val micAvailable = remember { android.speech.SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember {
        if (micAvailable) android.speech.SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }
    val startListening: () -> Unit = {
        val rec = recognizer
        if (rec == null) {
            micHint.value = "这台设备暂时听不到，请打字告诉我"
        } else if (!micListening.value) {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            rec.setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    micListening.value = true
                    micHint.value = "正在听，请说话…"
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { micHint.value = "听听看…" }
                override fun onError(error: Int) {
                    micListening.value = false
                    micHint.value = if (error == android.speech.SpeechRecognizer.ERROR_NO_MATCH) {
                        "没听清，再按一次麦克风试试"
                    } else {
                        "没听到，也可以直接打字"
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    micListening.value = false
                    val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()
                    if (!text.isNullOrBlank()) {
                        micHint.value = null
                        onSend(text)
                    } else {
                        micHint.value = "没听清，再按一次麦克风试试"
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { input.value = it }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            rec.startListening(intent)
            micHint.value = "正在听，请说话…"
        }
    }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted.value = granted
        if (granted) startListening() else micHint.value = "需要麦克风权限才能语音说话，也可以直接打字"
    }

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

    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
            // 儿童端模型已锁定 DeepSeek，不向孩子暴露供应商等工程信息

            // 未配置提示：柔和暖色引导家长去设置 API Key
            if (!state.chatProviderConfigured) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = com.xingmou.ui.theme.CoralSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenApiKey() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🧠", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "小星还没连上大脑，请爸爸妈妈点这里设置一下",
                            style = MaterialTheme.typography.bodySmall,
                            color = com.xingmou.ui.theme.CoralDark
                        )
                    }
                }
            }

            // 新消息到达时自动滚动到底部
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
            }

            // 消息列表：占满剩余高度、消息不足一屏时贴底（贴近输入区），输入区始终固定底部
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 4.dp),
                // 问候状态贴顶展示，进入真实对话后消息贴底靠近输入区
                verticalArrangement = if (messages.isEmpty() && !greetingChoiceMade.value) {
                    Arrangement.spacedBy(10.dp)
                } else {
                    Arrangement.spacedBy(10.dp, alignment = Alignment.Bottom)
                }
            ) {
                if (messages.isEmpty() && !greetingChoiceMade.value) {
                    // 开场问候：小星主动给出「聊天 / 做游戏」两个大按钮选择
                    ChatBubble(
                        com.xingmou.ChatMessageUi(
                            id = "greeting",
                            role = "assistant",
                            content = "你好呀，我是小星！今天想和我聊天，还是一起做游戏呀？"
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val chatContainer = MaterialTheme.colorScheme.secondaryContainer
                        GreetingChoice("💬", "和小星聊天", Modifier.weight(1f)) { cx, cy, h ->
                            onChoiceBegin(chatContainer, cx, cy, h) {
                                greetingChoiceMade.value = true
                            }
                        }
                        GreetingChoice(
                            "🎮", "做游戏", Modifier.weight(1f),
                            container = com.xingmou.ui.theme.Coral,
                            content = androidx.compose.ui.graphics.Color.White
                        ) { cx, cy, h ->
                            onChoiceBegin(com.xingmou.ui.theme.Coral, cx, cy, h) {
                                greetingChoiceMade.value = true
                                onPlayGame()
                            }
                        }
                    }
                } else if (messages.isEmpty()) {
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
                        shape = RoundedCornerShape(50),
                        color = androidx.compose.ui.graphics.Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, com.xingmou.ui.theme.Rule),
                        modifier = Modifier.clickable(enabled = !state.chatLoading) { onSend(topic) }
                    ) {
                        Text(
                            topic,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = com.xingmou.ui.theme.Ink,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 语音输入提示行
            val hint = micHint.value
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // 输入区（交互模式为全屏无导航栏形态：常驻「做游戏」按钮提供去训练界面的路径）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val gameBtnRect = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
                androidx.compose.material3.Surface(
                    onClick = {
                        val r = gameBtnRect.value
                        onChoiceBegin(
                            com.xingmou.ui.theme.Coral,
                            r.left + r.width / 2f,
                            r.top + r.height / 2f,
                            r.height
                        ) { onPlayGame() }
                    },
                    enabled = !state.chatLoading,
                    shape = RoundedCornerShape(50),
                    color = com.xingmou.ui.theme.Coral,
                    modifier = Modifier.onGloballyPositioned { coords ->
                        val topLeft = coords.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
                        gameBtnRect.value = androidx.compose.ui.geometry.Rect(
                            topLeft,
                            androidx.compose.ui.geometry.Size(coords.size.width.toFloat(), coords.size.height.toFloat())
                        )
                    }
                ) {
                    Text(
                        "🎮 做游戏",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                androidx.compose.material3.Surface(
                    onClick = {
                        micHint.value = null
                        if (micGranted.value) startListening() else micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    enabled = !state.chatLoading && !micListening.value,
                    shape = RoundedCornerShape(50),
                    color = if (micListening.value) com.xingmou.ui.theme.Coral else androidx.compose.ui.graphics.Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.xingmou.ui.theme.Rule)
                ) {
                    Text(
                        if (micListening.value) "🎧 听着呢" else "🎤 语音",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        color = if (micListening.value) androidx.compose.ui.graphics.Color.White else com.xingmou.ui.theme.Ink,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = input.value,
                    onValueChange = { input.value = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("对小星说…") },
                    enabled = !state.chatLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.xingmou.ui.theme.Coral,
                        unfocusedBorderColor = com.xingmou.ui.theme.Rule,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.White
                    )
                )
                Button(
                    onClick = {
                        val t = input.value
                        input.value = ""
                        onSend(t)
                    },
                    enabled = !state.chatLoading && input.value.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.xingmou.ui.theme.Coral,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) { Text("发送") }
            }
    }
}

/** 开场两个大选择按钮：儿童点击无需打字。「做游戏」用暖色实底与聊天形成明显区分。点击后色块从按钮位置放大铺满全屏。 */
@Composable
private fun GreetingChoice(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer,
    content: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onStart: (Float, Float, Float) -> Unit
) {
    val rectState = remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    androidx.compose.material3.Surface(
        onClick = {
            val r = rectState.value
            onStart(r.left + r.width / 2f, r.top + r.height / 2f, r.height)
        },
        shape = RoundedCornerShape(24.dp),
        color = container,
        modifier = modifier.heightIn(min = 112.dp).onGloballyPositioned { coords ->
            val topLeft = coords.localToRoot(androidx.compose.ui.geometry.Offset.Zero)
            rectState.value = androidx.compose.ui.geometry.Rect(
                topLeft,
                androidx.compose.ui.geometry.Size(coords.size.width.toFloat(), coords.size.height.toFloat())
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(emoji, style = MaterialTheme.typography.headlineLarge)
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                color = content
            )
        }
    }
}

/** 开场选择放大转场的参数：色块颜色、按钮中心与高度、切换完成回调。 */
private data class ChoiceExpand(
    val color: androidx.compose.ui.graphics.Color,
    val centerX: Float,
    val centerY: Float,
    val sizePx: Float,
    val onDone: () -> Unit
)

@Composable
private fun ChatBubble(msg: com.xingmou.ChatMessageUi) {
    val isUser = msg.role == "user"
    val bubbleColor = if (isUser) com.xingmou.ui.theme.Coral else androidx.compose.ui.graphics.Color.White
    val bubbleBorder = if (isUser) {
        androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Transparent)
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, com.xingmou.ui.theme.Rule)
    }
    val bubbleText = when {
        msg.isError -> com.xingmou.ui.theme.CoralDark
        isUser -> androidx.compose.ui.graphics.Color.White
        else -> com.xingmou.ui.theme.Ink
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = com.xingmou.ui.theme.Coral,
                modifier = Modifier.size(34.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text("星", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp),
            color = bubbleColor,
            border = bubbleBorder,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = msg.content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = bubbleText,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (isUser) {
            Spacer(modifier = Modifier.size(8.dp))
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = com.xingmou.ui.theme.ExistingBlue,
                modifier = Modifier.size(34.dp)
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Text("我", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}
