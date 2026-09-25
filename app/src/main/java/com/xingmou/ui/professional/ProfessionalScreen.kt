package com.xingmou.ui.professional

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xingmou.ProfessionalUiState
import com.xingmou.HomeFeedbackUi
import com.xingmou.data.catalog.AssessmentCatalog
import com.xingmou.core.domain.PlanStatus
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.theme.Success
import com.xingmou.ui.theme.Warning

@Composable
fun ProfessionalScreen(
    state: ProfessionalUiState,
    onReviewCommentChange: (String) -> Unit,
    onPlanTaskChange: (String) -> Unit,
    onPlanDifficultyChange: (String) -> Unit,
    onPlanSupportLevelChange: (String) -> Unit,
    onPlanFrequencyChange: (String) -> Unit,
    onPlanDurationChange: (String) -> Unit,
    onPlanStopConditionsChange: (String) -> Unit,
    onCreateRevision: () -> Unit,
    onAssessmentSelect: (String) -> Unit,
    onAssessmentDateChange: (String) -> Unit,
    onAssessmentSourceChange: (String) -> Unit,
    onAssessmentScoresChange: (String) -> Unit,
    onAssessmentNotesChange: (String) -> Unit,
    onSaveAssessment: () -> Unit,
    onCreateDraft: () -> Unit,
    onConfirm: () -> Unit,
    onActivate: () -> Unit,
    onReject: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("专业审核工作台", style = MaterialTheme.typography.headlineMedium)
        Text("Agent 负责整理与草拟，方案确认和生效始终由专业人员完成。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 920.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AnalysisPanel(state, onRefresh)
                        ReportPanel(state)
                        GroupReportPanel(state)
                        TrainingDetailsPanel(state)
                        AssessmentPanel(state, onAssessmentSelect, onAssessmentDateChange, onAssessmentSourceChange, onAssessmentScoresChange, onAssessmentNotesChange, onSaveAssessment)
                        HomeFeedbackPanel(state)
                        AgentPanel(state)
                    }
                    PlanPanel(state, onReviewCommentChange, onPlanTaskChange, onPlanDifficultyChange, onPlanSupportLevelChange, onPlanFrequencyChange, onPlanDurationChange, onPlanStopConditionsChange, onCreateRevision, onCreateDraft, onConfirm, onActivate, onReject, Modifier.weight(1.12f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AnalysisPanel(state, onRefresh)
                    ReportPanel(state)
                    GroupReportPanel(state)
                    TrainingDetailsPanel(state)
                    AssessmentPanel(state, onAssessmentSelect, onAssessmentDateChange, onAssessmentSourceChange, onAssessmentScoresChange, onAssessmentNotesChange, onSaveAssessment)
                    HomeFeedbackPanel(state)
                    PlanPanel(state, onReviewCommentChange, onPlanTaskChange, onPlanDifficultyChange, onPlanSupportLevelChange, onPlanFrequencyChange, onPlanDurationChange, onPlanStopConditionsChange, onCreateRevision, onCreateDraft, onConfirm, onActivate, onReject, Modifier.fillMaxWidth())
                    AgentPanel(state)
                }
            }
        }
    }
}

@Composable
private fun HomeFeedbackPanel(state: ProfessionalUiState) {
    SectionSurface(title = "家庭反馈", supporting = "仅显示当前儿童已保存的状态观察。") {
        if (state.recentHomeFeedback.isEmpty()) {
            Text("暂无家庭反馈。家长完成任务后可保存今天的观察。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.recentHomeFeedback.forEach { feedback ->
                Text("${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(feedback.createdAt))} · ${feedback.taskTitle}", style = MaterialTheme.typography.labelMedium)
                Text("心情：${feedback.mood} · 疲劳：${feedback.fatigue}${feedback.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun ReportPanel(state: ProfessionalUiState) {
    SectionSurface(title = "训练报表", supporting = "指标来自当前儿童的本地训练记录，不等同于能力评估或医学诊断。") {
        state.reportMetrics.forEachIndexed { index, metric ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(metric.label, style = MaterialTheme.typography.titleSmall)
                    Text(metric.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(metric.value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun GroupReportPanel(state: ProfessionalUiState) {
    SectionSurface(title = "六域 / 模块聚合", supporting = "点击回溯前的只读摘要；按当前儿童记录聚合。") {
        if (state.reportGroups.isEmpty()) {
            Text("暂无足够记录可聚合。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.reportGroups.forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("${group.domain} 域 · ${group.task}", style = MaterialTheme.typography.titleSmall)
                Text("${group.sampleCount} 条 · 正确率 ${group.accuracy} · 独立完成 ${group.independentRate} · 反应时 ${group.averageReaction}", modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrainingDetailsPanel(state: ProfessionalUiState) {
    SectionSurface(title = "最近训练记录", supporting = "只读明细，可用于回到原始训练过程核对。") {
        if (state.recentTrainingDetails.isEmpty()) {
            Text("暂无训练记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.recentTrainingDetails.forEachIndexed { index, detail ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(detail.timestamp))} · ${detail.domain}/${detail.task}", style = MaterialTheme.typography.titleSmall)
                Text("${detail.result} · ${detail.support} · 反应时 ${detail.reaction}", modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AssessmentPanel(
    state: ProfessionalUiState,
    onSelect: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onSourceChange: (String) -> Unit,
    onScoresChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    SectionSurface(title = "量表转录与复评", supporting = state.assessmentMessage) {
        Text("量表记录由专业人员录入，保留版本与来源。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text("${state.assessmentName} · ${state.assessmentId}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AssessmentCatalog.all.forEach { definition ->
                    DropdownMenuItem(
                        text = { Text("${definition.name} · ${definition.id}") },
                        onClick = { expanded = false; onSelect(definition.id) }
                    )
                }
            }
        }
        OutlinedTextField(value = state.assessmentDate, onValueChange = onDateChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("评估日期") }, placeholder = { Text("例如 2026-09-25") })
        OutlinedTextField(value = state.assessmentSource, onValueChange = onSourceChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("来源/工具版本") })
        OutlinedTextField(value = state.assessmentScores, onValueChange = onScoresChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, maxLines = 4, label = { Text("分数摘要") }, placeholder = { Text("例如：A=3，B=2；或粘贴结构化摘要") })
        OutlinedTextField(value = state.assessmentNotes, onValueChange = onNotesChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, maxLines = 4, label = { Text("专业备注（可选）") })
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("保存量表记录") }
        if (state.recentAssessments.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text("历史版本", style = MaterialTheme.typography.titleMedium)
            state.recentAssessments.forEach { item ->
                Text("${item.assessmentName} V${item.version} · ${item.recordType} · ${item.assessmentDate}", modifier = Modifier.padding(top = 6.dp))
                Text("${item.source} · ${item.scores}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AnalysisPanel(state: ProfessionalUiState, onRefresh: () -> Unit) {
    SectionSurface(title = "过程分析", supporting = "只描述训练过程表现，不作医学诊断。") {
        StatusLine("有效记录", "${state.recordCount} 条")
        Spacer(Modifier.height(8.dp))
        StatusLine("数据充分性", if (state.dataSufficient) "可生成过程分析" else "不足 3 条", valueColor = if (state.dataSufficient) Success else Warning)
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        state.analysisSummary.forEach { Text("• $it", modifier = Modifier.padding(bottom = 6.dp)) }
        Text(state.profileVersionSummary, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(state.profileEvidenceSummary, modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.warningSignals.forEach { Text("注意：$it", modifier = Modifier.padding(top = 6.dp), color = Warning) }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("刷新本地记录") }
    }
}

@Composable
private fun PlanPanel(
    state: ProfessionalUiState,
    onReviewCommentChange: (String) -> Unit,
    onPlanTaskChange: (String) -> Unit,
    onPlanDifficultyChange: (String) -> Unit,
    onPlanSupportLevelChange: (String) -> Unit,
    onPlanFrequencyChange: (String) -> Unit,
    onPlanDurationChange: (String) -> Unit,
    onPlanStopConditionsChange: (String) -> Unit,
    onCreateRevision: () -> Unit,
    onCreateDraft: () -> Unit,
    onConfirm: () -> Unit,
    onActivate: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier
) {
    SectionSurface(
        title = "训练方案",
        supporting = state.reviewMessage,
        modifier = modifier,
        containerColor = if (state.planStatus == PlanStatus.ACTIVE) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    ) {
        StatusLine("当前状态", state.planStatus?.name ?: "尚未创建")
        Spacer(Modifier.height(12.dp))
        Text(state.planSummary, style = MaterialTheme.typography.bodyLarge)
        if (state.planDiffs.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text("与上一版本的差异", style = MaterialTheme.typography.titleMedium)
            state.planDiffs.forEach { diff ->
                Text("${diff.field}：${diff.previous} → ${diff.current}", modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (state.planStatus == PlanStatus.ACTIVE) {
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text("编辑并创建新版本", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(state.planTask, onPlanTaskChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("训练任务") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(state.planDifficulty, onPlanDifficultyChange, modifier = Modifier.weight(1f), label = { Text("难度 1–5") })
                OutlinedTextField(state.planSupportLevel, onPlanSupportLevelChange, modifier = Modifier.weight(1f), label = { Text("支持等级") })
            }
            OutlinedTextField(state.planFrequency, onPlanFrequencyChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("频率") })
            OutlinedTextField(state.planDuration, onPlanDurationChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("单次时长") })
            OutlinedTextField(state.planStopConditions, onPlanStopConditionsChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, maxLines = 4, label = { Text("停止条件") })
            OutlinedButton(onClick = onCreateRevision, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !state.isWorking) { Text("创建新版本草案") }
        }
        if (state.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.reviewComment,
            onValueChange = onReviewCommentChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            label = { Text("审核意见") },
            placeholder = { Text("退回修改时必须填写理由；确认时建议记录核对要点。") }
        )
        Spacer(Modifier.height(14.dp))
        when (state.planStatus) {
            null, PlanStatus.REJECTED -> Button(
                onClick = onCreateDraft,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.dataSufficient && !state.isWorking
            ) { Text(if (state.dataSufficient) "生成方案草案" else "记录不足，暂不能生成") }
            PlanStatus.DRAFT -> {
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !state.isWorking) { Text("确认草案") }
                OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !state.isWorking) { Text("退回修改") }
            }
            PlanStatus.CONFIRMED -> {
                Button(onClick = onActivate, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !state.isWorking) { Text("签署生效") }
                OutlinedButton(onClick = onReject, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = !state.isWorking) { Text("退回修改") }
            }
            PlanStatus.ACTIVE -> Text("方案已由专业人员签署生效。后续变更应创建新版本。", color = Success)
            else -> Text("当前状态不可在本页面继续变更。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AgentPanel(state: ProfessionalUiState) {
    SectionSurface(title = "Agent 运行", supporting = "本地可审计信息，不展示原始敏感数据。", containerColor = MaterialTheme.colorScheme.primaryContainer) {
        StatusLine("运行状态", state.agentStatus)
        Spacer(Modifier.height(8.dp))
        StatusLine("最近事件", state.recentEvent)
        state.agentRunId?.let {
            Spacer(Modifier.height(8.dp))
            StatusLine("runId", it)
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("规则与工具", style = MaterialTheme.typography.titleMedium)
        Text(state.evidence.joinToString(" · "), modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
