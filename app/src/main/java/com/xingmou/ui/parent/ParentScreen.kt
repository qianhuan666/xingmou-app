package com.xingmou.ui.parent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingmou.ParentDomainStatUi
import com.xingmou.ParentUiState
import com.xingmou.RainbowProfileUi
import com.xingmou.parentRiskLabel
import com.xingmou.parentRouteLabel
import com.xingmou.scoreLevelWord
import com.xingmou.supportWord

import com.xingmou.ui.components.AgentStatusLine
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.components.domainBarColor
import com.xingmou.ui.theme.Warning

private enum class ParentSection { COMPANIONSHIP, DATA, WORKBENCH }

@Composable
fun ParentScreen(
    state: ParentUiState,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    onAskOnline: () -> Unit,
    onCompleteTask: () -> Unit,
    onSkipTask: () -> Unit,
    onPauseTask: () -> Unit,
    onAdvanceDemo: () -> Unit,
    onMoodChange: (String) -> Unit,
    onQuickRecordMood: (String) -> Unit,
    onFeedbackNoteChange: (String) -> Unit,
    onSubmitFeedback: () -> Unit,
    onSubmitObservation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSection = remember { mutableStateOf(ParentSection.COMPANIONSHIP) }
    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(modifier = Modifier.padding(top = 16.dp)) {
            NavigationRailItem(
                selected = selectedSection.value == ParentSection.COMPANIONSHIP,
                onClick = { selectedSection.value = ParentSection.COMPANIONSHIP },
                icon = { Text("伴", style = MaterialTheme.typography.titleLarge) },
                label = { Text("今日任务") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ParentSection.DATA,
                onClick = { selectedSection.value = ParentSection.DATA },
                icon = { Text("数", style = MaterialTheme.typography.titleLarge) },
                label = { Text("成长记录") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ParentSection.WORKBENCH,
                onClick = { selectedSection.value = ParentSection.WORKBENCH },
                icon = { Text("台", style = MaterialTheme.typography.titleLarge) },
                label = { Text("求助建议") }
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                when (selectedSection.value) {
                    ParentSection.COMPANIONSHIP -> "今日任务"
                    ParentSection.DATA -> "成长记录"
                    ParentSection.WORKBENCH -> "求助建议"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                when (selectedSection.value) {
                    ParentSection.COMPANIONSHIP -> "陪孩子完成今天的练习任务，记录孩子的状态。"
                    ParentSection.DATA -> "查看孩子的能力画像、训练进度和近期变化。"
                    ParentSection.WORKBENCH -> "记录孩子的表现，获取专业建议，必要时发给老师。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 底部免责声明（仅在工作台显示）
            if (selectedSection.value == ParentSection.WORKBENCH) {
                Text(
                    "本应用不构成医学诊断或治疗建议。涉及诊疗判断时，请联系有资质的专业人员。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
            when (selectedSection.value) {
                ParentSection.COMPANIONSHIP -> {
                    HomeTaskPanel(
                        state, onCompleteTask, onSkipTask, onPauseTask, onAdvanceDemo,
                        onMoodChange, onQuickRecordMood, onFeedbackNoteChange, onSubmitFeedback
                    )
                    ReminderPanel()
                }
                ParentSection.DATA -> {
                    ParentProfileCard(state.profile)
                    ParentTrainingStatsCard(state)
                    AdaptiveOverviewPanel(state)
                    SectionSurface(title = "本周家庭回顾", supporting = "过去一周的练习和状态记录。") {
                        StatusLine("任务完成率", state.weekCompletionRate)
                        Spacer(Modifier.height(8.dp))
                        StatusLine("状态变化", state.weekStatusSummary)
                        Text(state.weekSuggestion, modifier = Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ParentSection.WORKBENCH -> {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val wide = maxWidth >= 860.dp
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                ObservationPanel(state, onQueryChange, onAsk, onSubmitObservation, onAskOnline, Modifier.weight(1.08f))
                                ResultPanel(state, Modifier.weight(0.92f))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ObservationPanel(state, onQueryChange, onAsk, onSubmitObservation, onAskOnline, Modifier.fillMaxWidth())
                                ResultPanel(state, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveOverviewPanel(state: ParentUiState) {
    SectionSurface(title = "训练模块进度", supporting = "看看孩子在各个练习上的表现。") {
        if (state.adaptiveOverview.isEmpty()) {
            Text("完成起点小测后，这里会显示训练模块状态。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.adaptiveOverview.take(8).forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.moduleName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            streakWord(item.correctStreak, item.errorStreak),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "难度 ${item.difficulty} · ${supportWord(item.supportLevel)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            if (state.adaptiveOverview.size > 8) {
                Text("还有 ${state.adaptiveOverview.size - 8} 个模块", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun streakWord(correct: Int, error: Int): String = when {
    correct > 0 -> "连续完成 ${correct} 次"
    error > 0 -> "连续未完成 ${error} 次"
    else -> "还没有练习记录"
}

@Composable
private fun HomeTaskPanel(
    state: ParentUiState,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onAdvanceDemo: () -> Unit,
    onMoodChange: (String) -> Unit,
    onQuickRecordMood: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    // 卡片 1：今天要做什么
    SectionSurface(title = "今天做什么") {
        Text(state.homeTaskTitle, style = MaterialTheme.typography.titleLarge)
        Text(state.homeTaskDescription, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        if (state.homeTaskSafetyStopped) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("当前有安全暂停标记，请先联系专业人员确认后再继续。", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (state.homeTaskStatus == "completed") {
            Text("今日任务已完成", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
            Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("重新做一次") }
        } else {
            Button(
                onClick = onComplete,
                enabled = state.homeTaskStatus == "pending" && !state.homeTaskSafetyStopped,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("完成任务") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                OutlinedButton(onClick = onPause, enabled = state.homeTaskStatus == "pending" && !state.homeTaskSafetyStopped, modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(12.dp)) { Text("暂停") }
                TextButton(onClick = onSkip, enabled = state.homeTaskStatus == "pending" && !state.homeTaskSafetyStopped, modifier = Modifier.weight(1f)) { Text("跳过") }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    // 卡片 2：怎么做
    SectionSurface(title = "5 分钟陪练示范", supporting = "按步骤进行，不必一次做完。${state.homeTaskDurationMinutes} 分钟 · ${supportWord(state.homeTaskSupportLevel)}。出现疲劳、拒绝或风险时暂停。") {
        DemoSteps(state.homeDemoStep, state.homeTaskSafetyStopped)
        Button(
            onClick = onAdvanceDemo,
            enabled = !state.homeTaskSafetyStopped,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
        ) { Text(if (state.homeDemoStep >= 4) "重新开始示范" else "完成本步") }
    }
    Spacer(Modifier.height(16.dp))
    // 卡片 3：记录状态
    SectionSurface(title = "记录今天的状态") {
        MoodQuickRow(selected = state.feedbackMood, onQuickRecord = onQuickRecordMood)
        if (state.feedbackMood.isNotEmpty()) {
            Text("已记录：${state.feedbackMood}", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedTextField(
            value = state.feedbackNote,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            minLines = 2,
            maxLines = 4,
            label = { Text("补充观察（可选）") },
            shape = RoundedCornerShape(12.dp)
        )
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("保存观察") }
    }
}

@Composable
private fun DemoSteps(current: Int, disabled: Boolean) {
    val steps = listOf("准备卡片", "家长示范", "孩子尝试", "及时肯定", "整理结束")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        steps.forEachIndexed { index, step ->
            Text(
                text = if (index <= current) "✓ $step" else "○ $step",
                modifier = Modifier.weight(1f),
                color = when {
                    disabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    index == current -> MaterialTheme.colorScheme.primary
                    index < current -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private val MOOD_OPTIONS = listOf("状态平稳", "睡眠不足", "情绪波动", "配合度较高")

@Composable
private fun MoodQuickRow(selected: String, onQuickRecord: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        MOOD_OPTIONS.forEach { label ->
            val isSelected = label == selected
            Surface(
                onClick = { onQuickRecord(label) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun homeStatusLabel(status: String): String = when (status) {
    "completed" -> "已完成"
    "skipped" -> "已跳过"
    "paused" -> "已暂停"
    else -> "待完成"
}

private data class QuickObservation(val label: String, val text: String)

private val QUICK_OBSERVATIONS = listOf(
    QuickObservation("配合不错", "孩子今天配合度不错，完成了任务"),
    QuickObservation("容易分心", "孩子今天状态不太好，容易分心"),
    QuickObservation("遇到困难", "孩子在某个环节遇到了困难"),
    QuickObservation("情绪波动", "孩子情绪有波动，需要安抚")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ObservationPanel(state: ParentUiState, onQueryChange: (String) -> Unit, onAsk: () -> Unit, onSubmitObservation: () -> Unit, onAskOnline: () -> Unit, modifier: Modifier) {
    SectionSurface(title = "写下观察", supporting = "点一个模板快速开始，或直接用简单的话描述孩子今天的表现。", modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            QUICK_OBSERVATIONS.forEach { option ->
                Surface(
                    onClick = { onQueryChange(option.text) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            placeholder = { Text("例如：做图片配对时选错两次，但很快调整好了。") },
            supportingText = { Text("${state.query.length}/200") },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(14.dp))
        val canAct = !state.isWorking
        Button(
            onClick = onAskOnline,
            enabled = canAct,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            val buttonBrush = if (canAct) {
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimaryContainer))
            } else {
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
            }
            Box(
                modifier = Modifier.fillMaxSize().background(buttonBrush, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (state.isWorking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("正在结合孩子数据分析…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("获取智能建议", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        OutlinedButton(
            onClick = onAsk,
            enabled = canAct,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("本地快速建议（离线知识库）") }
        TextButton(
            onClick = onSubmitObservation,
            enabled = canAct,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) { Text("发给老师/康复师", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(8.dp))
        CollapsibleSection("诊断信息") {
            StatusLine("本地训练记录", "${state.recordCount} 条")
            Spacer(Modifier.height(8.dp))
            StatusLine("安全状态", if (state.riskLabel == "SAFETY_STOP") "需要关注" else "正常", valueColor = if (state.riskLabel == "SAFETY_STOP") Warning else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            AgentStatusLine(status = state.agentStatus, working = state.isWorking)
        }
    }
}

@Composable
private fun ResultPanel(state: ParentUiState, modifier: Modifier) {
    SectionSurface(title = "支持建议", supporting = state.message, modifier = modifier) {
        if (state.suggestions.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("写下一条观察，点击「获取智能建议」", style = MaterialTheme.typography.titleMedium)
                Text("这里会生成结合孩子最近训练数据的个性化建议。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            val aiAdvice = state.sources.any { it.contains("AI") }
            if (aiAdvice) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "AI 个性化 · 基于孩子训练数据",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            state.suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(suggestion, style = MaterialTheme.typography.bodyLarge)
                if (!aiAdvice) {
                    state.sources.getOrNull(index)?.let { source ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        if (state.suggestions.isNotEmpty() || state.agentRunId != null) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            CollapsibleSection("依据与运行记录") {
                StatusLine("来源约束", "已审核资料 · 同一领域最多 2 条 · 总数最多 3 条")
                Spacer(Modifier.height(6.dp))
                StatusLine("风险筛查", parentRiskLabel(state.riskLabel), valueColor = if (state.riskLabel == "SAFETY_STOP") Warning else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                StatusLine("决策路由", parentRouteLabel(state.route))
                state.agentRunId?.let { runId ->
                    Spacer(Modifier.height(6.dp))
                    StatusLine("运行记录", runId)
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSection(label: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { content() }
        }
    }
}

@Composable
private fun ParentProfileCard(profile: RainbowProfileUi) {
    if (!profile.present) {
        SectionSurface(title = "平台初始能力画像", supporting = "儿童完成基线测试后自动生成。") {
            Text("孩子还没有完成基线测试。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            Text("基线测试有 26 道简单题目，大约 5-8 分钟完成。完成后这里会显示孩子的能力画像和个性化训练建议。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Text("点击下方「今日任务」，陪孩子开始第一次练习。", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }
        return
    }
    SectionSurface(title = "平台初始能力画像", supporting = "生成于 ${profile.createdLabel}") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            if (profile.parentSummary.isNotBlank()) {
                Text(profile.parentSummary, style = MaterialTheme.typography.bodyLarge)
            }
            var showDetails = remember { mutableStateOf(false) }
            TextButton(onClick = { showDetails.value = !showDetails.value }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showDetails.value) "收起六域详情" else "展开六域详情")
            }
            if (showDetails.value) {
                profile.domainBars.forEach { bar ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(bar.name, style = MaterialTheme.typography.titleMedium)
                            if (bar.description.isNotBlank()) {
                                Text(bar.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                            }
                            LinearProgressIndicator(
                                progress = { bar.score / 100f },
                                color = domainBarColor(bar.colorKey),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                        Text("训练起点 ${bar.score} · ${scoreLevelWord(bar.score)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.narrative, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text("平台原创训练起点画像，不等同于标准化量表或医学诊断。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DomainRadarChart(domains: List<ParentDomainStatUi>) {
    val textMeasurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier = Modifier.fillMaxWidth().height(300.dp).padding(vertical = 8.dp)) {
        val n = domains.size
        if (n < 3) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = size.minDimension / 2f - 44.dp.toPx()
        val angleStep = (2f * Math.PI / n).toFloat()
        val startAngle = -Math.PI.toFloat() / 2f
        fun pointAt(index: Int, radius: Float): Offset {
            val angle = startAngle + angleStep * index
            return Offset(cx + kotlin.math.cos(angle) * radius, cy + kotlin.math.sin(angle) * radius)
        }
        listOf(0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            val radius = maxRadius * fraction
            val grid = Path()
            repeat(n) { i ->
                val p = pointAt(i, radius)
                if (i == 0) grid.moveTo(p.x, p.y) else grid.lineTo(p.x, p.y)
            }
            grid.close()
            drawPath(grid, color = gridColor, style = Stroke(width = 1.dp.toPx()))
        }
        repeat(n) { i ->
            drawLine(gridColor, pointAt(i, 0f), pointAt(i, maxRadius), strokeWidth = 1.dp.toPx())
        }
        val dataPath = Path()
        domains.forEachIndexed { i, domain ->
            val radius = maxRadius * (domain.accuracy / 100f).coerceIn(0f, 1f)
            val p = pointAt(i, radius)
            if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, color = primary.copy(alpha = 0.18f))
        drawPath(dataPath, color = primary, style = Stroke(width = 2.dp.toPx()))
        val labelStyle = TextStyle(fontSize = 12.sp, color = onSurface)
        domains.forEachIndexed { i, domain ->
            val radius = maxRadius * (domain.accuracy / 100f).coerceIn(0f, 1f)
            val p = pointAt(i, radius)
            drawCircle(domainBarColor(domain.colorKey), radius = 5.dp.toPx(), center = p)
            val labelPoint = pointAt(i, maxRadius + 20.dp.toPx())
            val label = domain.name
            val measured = textMeasurer.measure(label, style = labelStyle)
            drawText(
                textMeasurer, label,
                topLeft = Offset(labelPoint.x - measured.size.width / 2f, labelPoint.y - measured.size.height / 2f),
                style = labelStyle
            )
        }
    }
}

@Composable
private fun ParentTrainingStatsCard(state: ParentUiState) {
    SectionSurface(title = "六域训练概览", supporting = "来自当前儿童的本地训练记录，展示练习量与正确率。") {
        if (state.domainOverview.isEmpty()) {
            Text("孩子还没有开始训练。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            Text("完成基线测试后，会解锁适合孩子的训练课程。训练记录会在这里以图表形式展示，帮助您了解孩子在各个能力域的进步。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Text("先在儿童端完成基线测试，解锁专属训练计划。", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        } else {
            DomainRadarChart(state.domainOverview)
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            state.domainOverview.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEach { domain ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Column {
                                Text(domain.name, style = MaterialTheme.typography.labelMedium)
                                Text(if (domain.count == 0) "暂无记录" else "正确率 ${domain.accuracy}% · ${domain.count} 次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("正确率走势", style = MaterialTheme.typography.titleMedium)
        Text("按训练时间从早到晚分成最多 5 段，用于回看正确率的变化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.trendPoints.isEmpty()) {
            Text("暂无足够记录生成趋势。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else state.trendPoints.forEach { point ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(point.dateRange.ifBlank { "第${point.label}段" }, modifier = Modifier.weight(0.22f), style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { point.accuracy }, modifier = Modifier.weight(0.55f).padding(top = 3.dp))
                Text("${(point.accuracy * 100).toInt()}% · ${point.sampleCount}条", modifier = Modifier.weight(0.23f), style = MaterialTheme.typography.labelSmall)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("最近训练记录", style = MaterialTheme.typography.titleMedium)
        if (state.recentTrainingDetails.isEmpty()) {
            Text("暂无训练记录。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else state.recentTrainingDetails.forEachIndexed { index, detail ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("${detail.domain} · ${detail.task}", style = MaterialTheme.typography.titleSmall)
            Text(
                "${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(detail.timestamp))} · ${detail.result} · ${detail.support} · 反应时 ${detail.reaction}",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ReminderPanel() {
    SectionSurface(title = "训练提醒", supporting = "帮助建立短时、可停止的练习节奏。") {
        Text("建议短时高频：每次 5–10 分钟，在孩子状态稳定时进行。", modifier = Modifier.padding(bottom = 6.dp))
        Text("出现疲劳、拒绝或情绪波动时暂停，不必追求一次完成。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("当前原型不发送系统推送，提醒仅作为安排参考。", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
