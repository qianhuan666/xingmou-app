package com.xingmou.ui.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xingmou.ParentDomainStatUi
import com.xingmou.ParentUiState
import com.xingmou.RainbowProfileUi
import com.xingmou.core.safety.SafeResponses
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
                label = { Text("陪练") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ParentSection.DATA,
                onClick = { selectedSection.value = ParentSection.DATA },
                icon = { Text("数", style = MaterialTheme.typography.titleLarge) },
                label = { Text("数据") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ParentSection.WORKBENCH,
                onClick = { selectedSection.value = ParentSection.WORKBENCH },
                icon = { Text("台", style = MaterialTheme.typography.titleLarge) },
                label = { Text("工作台") }
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                when (selectedSection.value) {
                    ParentSection.COMPANIONSHIP -> "家庭陪练"
                    ParentSection.DATA -> "儿童数据"
                    ParentSection.WORKBENCH -> "家长工作台"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                when (selectedSection.value) {
                    ParentSection.COMPANIONSHIP -> "按今天的节奏陪孩子完成一个小任务。"
                    ParentSection.DATA -> "查看能力画像和最近一周的家庭训练变化。"
                    ParentSection.WORKBENCH -> "记录观察，并从本地已审核知识中寻找可执行建议。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                    SectionSurface(title = "本周家庭回顾", supporting = "只汇总当前儿童最近 7 天的本地记录。") {
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
                                ObservationPanel(state, onQueryChange, onAsk, onSubmitObservation, Modifier.weight(1.08f))
                                ResultPanel(state, Modifier.weight(0.92f))
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                ObservationPanel(state, onQueryChange, onAsk, onSubmitObservation, Modifier.fillMaxWidth())
                                ResultPanel(state, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    SectionSurface(title = "边界说明", containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(SafeResponses.DISCLAIMER)
                        Text("涉及诊疗判断、持续加重或紧急风险时，请联系有资质的专业人员。", modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
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
    SectionSurface(title = "今日家庭任务", supporting = state.feedbackMessage, containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(state.homeTaskTitle, style = MaterialTheme.typography.titleLarge)
        Text(state.homeTaskDescription, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text("安排：${state.homeTaskFrequency} · ${state.homeTaskDurationMinutes} 分钟 · 支持 ${state.homeTaskSupportLevel}", modifier = Modifier.padding(top = 6.dp))
        Text("停止条件：${state.homeTaskStopConditions}", modifier = Modifier.padding(top = 4.dp), color = Warning)
        if (state.homeTaskSafetyStopped) {
            Text("当前有安全暂停标记，家庭任务已暂时禁用，请先联系专业人员确认。", modifier = Modifier.padding(top = 8.dp), color = Warning)
        }
        StatusLine("任务状态", homeStatusLabel(state.homeTaskStatus))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onComplete, enabled = state.homeTaskStatus == "pending" && !state.homeTaskSafetyStopped, modifier = Modifier.weight(1f)) { Text("完成") }
            OutlinedButton(onClick = onPause, enabled = state.homeTaskStatus == "pending" && !state.homeTaskSafetyStopped, modifier = Modifier.weight(1f)) { Text("暂停") }
            TextButton(onClick = onSkip, enabled = state.homeTaskStatus == "pending" && !state.homeTaskSafetyStopped, modifier = Modifier.weight(1f)) { Text("跳过") }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("5 分钟陪练示范", style = MaterialTheme.typography.titleMedium)
        Text("按儿童状态逐步完成，不必一次做完。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        DemoSteps(state.homeDemoStep, state.homeTaskSafetyStopped)
        OutlinedButton(
            onClick = onAdvanceDemo,
            enabled = !state.homeTaskSafetyStopped,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text(if (state.homeDemoStep >= 4) "重新开始示范" else "完成本步") }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("今天的状态（点一下即记录）", style = MaterialTheme.typography.titleMedium)
        MoodQuickRow(selected = state.feedbackMood, onQuickRecord = onQuickRecordMood)
        OutlinedTextField(
            value = state.feedbackNote,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 2,
            maxLines = 4,
            label = { Text("补充观察（可选）") }
        )
        Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("保存今天的观察") }
    }
}

@Composable
private fun DemoSteps(current: Int, disabled: Boolean) {
    val steps = listOf("准备", "示范", "邀请", "回应", "结束")
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

private data class MoodOption(val emoji: String, val label: String)

private val MOOD_OPTIONS = listOf(
    MoodOption("😊", "状态平稳"),
    MoodOption("😴", "睡眠不足"),
    MoodOption("😣", "情绪波动"),
    MoodOption("🤝", "配合度较高")
)

@Composable
private fun MoodQuickRow(selected: String, onQuickRecord: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        MOOD_OPTIONS.forEach { option ->
            val isSelected = option.label == selected
            Surface(
                onClick = { onQuickRecord(option.label) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(option.emoji, style = MaterialTheme.typography.titleLarge)
                    Text(option.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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

@Composable
private fun ObservationPanel(state: ParentUiState, onQueryChange: (String) -> Unit, onAsk: () -> Unit, onSubmitObservation: () -> Unit, modifier: Modifier) {
    SectionSurface(title = "写下观察", supporting = "建议包含发生场景、持续时间和孩子当时的状态。", modifier = modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 7,
            label = { Text("具体观察") },
            placeholder = { Text("例如：今天做图片配对时，连续两次选错后开始捂耳朵。") },
            supportingText = { Text("${state.query.length}/240") }
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAsk, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !state.isWorking) {
            Text(if (state.isWorking) "正在检索" else "检索支持建议")
        }
        OutlinedButton(onClick = onSubmitObservation, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !state.isWorking) {
            Text("提交家庭观察给专业人员")
        }
        if (state.isWorking) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        Spacer(Modifier.height(16.dp))
        StatusLine("本地训练记录", "${state.recordCount} 条")
        Spacer(Modifier.height(8.dp))
        StatusLine("风险路由", state.riskLabel, valueColor = if (state.riskLabel == "SAFETY_STOP") Warning else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        StatusLine("Agent", state.agentStatus)
    }
}

@Composable
private fun ResultPanel(state: ParentUiState, modifier: Modifier) {
    SectionSurface(title = "支持建议", supporting = state.message, modifier = modifier) {
        if (state.suggestions.isEmpty()) {
            Text("当前没有可展示的已审核建议。可补充更具体的观察后再次检索。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(suggestion, style = MaterialTheme.typography.bodyLarge)
                state.sources.getOrNull(index)?.let { source ->
                    Text(source, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        state.agentRunId?.let { runId ->
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("运行记录：$runId", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ParentProfileCard(profile: RainbowProfileUi) {
    if (!profile.present) {
        SectionSurface(title = "平台初始能力画像", supporting = "儿童完成六题起点小测后自动生成。") {
            Text("尚无画像记录。可先在儿童端完成起点小测。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    SectionSurface(title = "平台初始能力画像", supporting = "生成于 ${profile.createdLabel}") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            profile.domainBars.forEach { bar ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(bar.emoji, style = MaterialTheme.typography.headlineSmall)
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Text("🤖")
            Text(profile.narrative, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
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
            val label = "${domain.emoji}${domain.name}"
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
            Text("暂无六域训练记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DomainRadarChart(state.domainOverview)
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            state.domainOverview.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    row.forEach { domain ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(domain.emoji, style = MaterialTheme.typography.bodyLarge)
                            Column(Modifier.padding(start = 8.dp)) {
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
        Text("按训练记录时间分段，仅用于回看过程变化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.trendPoints.isEmpty()) {
            Text("暂无足够记录生成趋势。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else state.trendPoints.forEach { point ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("第${point.label}段", modifier = Modifier.weight(0.22f), style = MaterialTheme.typography.labelMedium)
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
            Text("${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(detail.timestamp))} · ${detail.domain}/${detail.task}", style = MaterialTheme.typography.titleSmall)
            Text("${detail.result} · ${detail.support} · 反应时 ${detail.reaction}", modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
