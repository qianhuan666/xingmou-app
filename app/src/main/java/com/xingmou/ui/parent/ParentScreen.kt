package com.xingmou.ui.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xingmou.ParentUiState
import com.xingmou.core.safety.SafeResponses
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.theme.Warning

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
    onFatigueChange: (String) -> Unit,
    onFeedbackNoteChange: (String) -> Unit,
    onSubmitFeedback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("家庭观察与支持", style = MaterialTheme.typography.headlineMedium)
        Text("先记录事实，再从本地已审核知识中寻找可执行建议。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        HomeTaskPanel(state, onCompleteTask, onSkipTask, onPauseTask, onAdvanceDemo, onMoodChange, onFatigueChange, onFeedbackNoteChange, onSubmitFeedback)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 860.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ObservationPanel(state, onQueryChange, onAsk, Modifier.weight(1.08f))
                    ResultPanel(state, Modifier.weight(0.92f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ObservationPanel(state, onQueryChange, onAsk, Modifier.fillMaxWidth())
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

@Composable
private fun HomeTaskPanel(
    state: ParentUiState,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onAdvanceDemo: () -> Unit,
    onMoodChange: (String) -> Unit,
    onFatigueChange: (String) -> Unit,
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
        Text("今天的状态", style = MaterialTheme.typography.titleMedium)
        ChoiceRow("心情", listOf("平稳", "兴奋", "抗拒"), state.feedbackMood, onMoodChange)
        ChoiceRow("疲劳", listOf("不确定", "较少", "明显"), state.feedbackFatigue, onFatigueChange)
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

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, modifier = Modifier.weight(0.28f))
        options.forEach { option ->
            TextButton(onClick = { onChange(option) }, modifier = Modifier.weight(0.24f)) {
                Text(if (option == selected) "✓ $option" else option)
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
private fun ObservationPanel(state: ParentUiState, onQueryChange: (String) -> Unit, onAsk: () -> Unit, modifier: Modifier) {
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
