package com.xingmou.ui.professional

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xingmou.ProfessionalUiState
import com.xingmou.HomeFeedbackUi
import com.xingmou.data.catalog.AssessmentCatalog
import com.xingmou.data.catalog.RehabilitationMethods
import com.xingmou.data.catalog.DomainCatalog
import com.xingmou.data.catalog.TaskCatalog
import com.xingmou.supportWord
import com.xingmou.core.domain.PlanStatus
import com.xingmou.ui.components.AgentStatusLine
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.theme.Success
import com.xingmou.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ProfessionalSection { ANALYSIS, PLAN, ASSESSMENT, CARE, AGENT }

@Composable
fun ProfessionalScreen(
    state: ProfessionalUiState,
    onReviewCommentChange: (String) -> Unit,
    onPlanTaskChange: (String) -> Unit,
    onPlanGoalChange: (String) -> Unit,
    onPlanDifficultyChange: (String) -> Unit,
    onPlanSupportLevelChange: (String) -> Unit,
    onPlanFrequencyChange: (String) -> Unit,
    onPlanDurationChange: (String) -> Unit,
    onPlanStopConditionsChange: (String) -> Unit,
    onCreateRevision: () -> Unit,
    onAdvanceCareStage: () -> Unit,
    onCareNoteChange: (String) -> Unit,
    onCareClosureReasonChange: (String) -> Unit,
    onCareFollowUpPlanChange: (String) -> Unit,
    onCareFollowUpDateChange: (String) -> Unit,
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
    onRefreshAgentAudit: () -> Unit,
    onOpenAgentAudit: (String) -> Unit,
    onAnnotateAgentTrace: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSection = remember { mutableStateOf(ProfessionalSection.ANALYSIS) }
    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(modifier = Modifier.padding(top = 16.dp)) {
            NavigationRailItem(
                selected = selectedSection.value == ProfessionalSection.ANALYSIS,
                onClick = { selectedSection.value = ProfessionalSection.ANALYSIS },
                icon = { Text("析", style = MaterialTheme.typography.titleLarge) },
                label = { Text("分析") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ProfessionalSection.PLAN,
                onClick = { selectedSection.value = ProfessionalSection.PLAN },
                icon = { Text("案", style = MaterialTheme.typography.titleLarge) },
                label = { Text("方案") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ProfessionalSection.ASSESSMENT,
                onClick = { selectedSection.value = ProfessionalSection.ASSESSMENT },
                icon = { Text("估", style = MaterialTheme.typography.titleLarge) },
                label = { Text("评估") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ProfessionalSection.CARE,
                onClick = { selectedSection.value = ProfessionalSection.CARE },
                icon = { Text("个", style = MaterialTheme.typography.titleLarge) },
                label = { Text("个案") }
            )
            NavigationRailItem(
                selected = selectedSection.value == ProfessionalSection.AGENT,
                onClick = { selectedSection.value = ProfessionalSection.AGENT },
                icon = { Text("AI", style = MaterialTheme.typography.titleLarge) },
                label = { Text("Agent") }
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                when (selectedSection.value) {
                    ProfessionalSection.ANALYSIS -> "过程分析"
                    ProfessionalSection.PLAN -> "训练方案"
                    ProfessionalSection.ASSESSMENT -> "量表与评估"
                    ProfessionalSection.CARE -> "个案管理"
                    ProfessionalSection.AGENT -> "Agent 运行审计"
                },
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                when (selectedSection.value) {
                    ProfessionalSection.ANALYSIS -> "查看能力分析、个体与分组报告及训练明细。"
                    ProfessionalSection.PLAN -> "Agent 生成草案，专业人员确认并签署生效。"
                    ProfessionalSection.ASSESSMENT -> "转录专业量表，并参考本地方法库。"
                    ProfessionalSection.CARE -> "记录个案阶段与家庭反馈。"
                    ProfessionalSection.AGENT -> "查看当前儿童的 Agent 运行记录与决策轨迹。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (selectedSection.value) {
                ProfessionalSection.ANALYSIS -> {
                    AnalysisPanel(state, onRefresh)
                    ReportPanel(state)
                    GroupReportPanel(state)
                    AdaptiveOverviewPanel(state)
                    TrainingDetailsPanel(state)
                }
                ProfessionalSection.PLAN -> {
                    PlanPanel(state, onReviewCommentChange, onPlanTaskChange, onPlanGoalChange, onPlanDifficultyChange, onPlanSupportLevelChange, onPlanFrequencyChange, onPlanDurationChange, onPlanStopConditionsChange, onCreateRevision, onCreateDraft, onConfirm, onActivate, onReject, Modifier.fillMaxWidth())
                }
                ProfessionalSection.ASSESSMENT -> {
                    AssessmentPanel(state, onAssessmentSelect, onAssessmentDateChange, onAssessmentSourceChange, onAssessmentScoresChange, onAssessmentNotesChange, onSaveAssessment)
                    MethodLibraryPanel(onAssociateWithPlan = { taskText ->
                        onPlanTaskChange(taskText)
                        selectedSection.value = ProfessionalSection.PLAN
                    })
                }
                ProfessionalSection.CARE -> {
                    CareWorkflowPanel(state, onAdvanceCareStage, onCareNoteChange, onCareClosureReasonChange, onCareFollowUpPlanChange, onCareFollowUpDateChange)
                    HomeFeedbackPanel(state)
                }
                ProfessionalSection.AGENT -> {
                    AgentPanel(state, onRefreshAgentAudit, onOpenAgentAudit, onAnnotateAgentTrace)
                }
            }
        }
    }
}

@Composable
private fun AdaptiveOverviewPanel(state: ProfessionalUiState) {
    SectionSurface(title = "模块自适应回放", supporting = "来自 Room 的当前难度、支持等级、连续表现和版本关联。") {
        if (state.adaptiveOverview.isEmpty()) {
            Text("暂无模块自适应状态。完成起点小测或一次训练后刷新。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.adaptiveOverview.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.moduleName, style = MaterialTheme.typography.titleSmall)
                        Text("基线 V${item.baselineVersion} · 方案 ${item.planVersion?.let { "V$it" } ?: "无"} · 最近题目 ${item.lastQuestionId ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("难度 ${item.difficulty} · ${supportWord(item.supportLevel)}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MethodLibraryPanel(onAssociateWithPlan: (String) -> Unit) {
    val expandedMethod = remember { mutableStateOf<String?>(null) }
    SectionSurface(title = "12 方法库", supporting = "点击方法展开详情，可关联到训练方案。仅供专业人员参考。") {
        RehabilitationMethods.all.forEachIndexed { index, method ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val isExpanded = expandedMethod.value == method.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .then(if (isExpanded) Modifier else Modifier)
                    .clickable { expandedMethod.value = if (isExpanded) null else method.id }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isExpanded) "▼" else "▶",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(method.name, style = MaterialTheme.typography.titleSmall)
                    Text(method.summary, modifier = Modifier.padding(top = 3.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isExpanded) {
                Column(Modifier.padding(start = 20.dp, top = 6.dp, bottom = 6.dp)) {
                    Text("适用边界：${method.boundary}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("来源：${method.sourceRef} · ${method.reviewStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { onAssociateWithPlan("${method.name}：${method.summary}") },
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("关联到训练方案") }
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
                val parts = buildList {
                    if (feedback.mood.isNotBlank()) add("状态：${feedback.mood}")
                    if (feedback.fatigue.isNotBlank()) add("疲劳：${feedback.fatigue}")
                    if (feedback.note.isNotBlank()) add(feedback.note)
                }
                Text(parts.joinToString(" · "), modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun CareWorkflowPanel(
    state: ProfessionalUiState,
    onAdvance: () -> Unit,
    onNoteChange: (String) -> Unit,
    onClosureReasonChange: (String) -> Unit,
    onFollowUpPlanChange: (String) -> Unit,
    onFollowUpDateChange: (String) -> Unit
) {
    SectionSurface(title = "专业个案流程", supporting = "接案、目标、方案、复评、结案和随访均保留历史记录。") {
        StatusLine("当前阶段", state.careStage)
        StatusLine("阶段状态", state.careStageStatus)
        Text(state.careStageSummary, modifier = Modifier.padding(top = 8.dp))
        if (state.careStage == "复评") {
            OutlinedTextField(
                value = state.careClosureReason,
                onValueChange = onClosureReasonChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 2,
                maxLines = 4,
                label = { Text("结案依据") },
                placeholder = { Text("进入结案阶段前填写可观察的结案依据") }
            )
        }
        if (state.careStage == "结案" || state.careStage == "随访") {
            OutlinedTextField(
                value = state.careFollowUpPlan,
                onValueChange = onFollowUpPlanChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                minLines = 2,
                maxLines = 4,
                label = { Text("随访计划") },
                placeholder = { Text("填写后续观察、联系或支持安排") }
            )
            OutlinedTextField(
                value = state.careFollowUpDate,
                onValueChange = onFollowUpDateChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text("随访日期") },
                placeholder = { Text("例如：2026-10-15") }
            )
        }
        OutlinedTextField(
            value = state.careNote,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            minLines = 2,
            maxLines = 4,
            label = { Text("阶段备注") },
            placeholder = { Text("结案和随访阶段必须填写专业备注") }
        )
        OutlinedButton(onClick = onAdvance, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Text(if (state.careStage == "随访") "签署并记录随访" else "签署并推进到下一阶段")
        }
        if (state.carePermissionMessage.isNotBlank()) {
            Text(state.carePermissionMessage, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        }
        if (state.careTimeline.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("流程历史", style = MaterialTheme.typography.titleMedium)
            state.careTimeline.forEach { item ->
                Text("${item.stageLabel} · ${item.status} · ${if (item.signed) "已签署" else "未签署"}", modifier = Modifier.padding(top = 6.dp))
                Text(item.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.note.isNotBlank()) Text("备注：${item.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.closureReason?.takeIf { it.isNotBlank() }?.let { Text("结案依据：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item.followUpPlan?.takeIf { it.isNotBlank() }?.let { Text("随访计划：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item.followUpDate?.takeIf { it.isNotBlank() }?.let { Text("随访日期：$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("分段正确率趋势", style = MaterialTheme.typography.titleMedium)
        Text("按训练时间从早到晚分成最多 5 段，用于回看正确率的变化。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.reportTrend.isEmpty()) {
            Text("暂无足够记录生成趋势。", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else state.reportTrend.forEach { point ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(point.label, modifier = Modifier.weight(0.22f), style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { point.accuracy }, modifier = Modifier.weight(0.55f).padding(top = 3.dp))
                Text("${(point.accuracy * 100).toInt()}% · ${point.sampleCount}条", modifier = Modifier.weight(0.23f), style = MaterialTheme.typography.labelSmall)
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
                val domainName = DomainCatalog.find(group.domain)?.name ?: group.domain
                val taskName = TaskCatalog.find(group.task)?.name ?: group.task
                Text("$domainName · $taskName", style = MaterialTheme.typography.titleSmall)
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
                val domainName = DomainCatalog.find(detail.domain)?.name ?: detail.domain
                val taskName = TaskCatalog.find(detail.task)?.name ?: detail.task
                Text("${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(detail.timestamp))} · $domainName · $taskName", style = MaterialTheme.typography.titleSmall)
                Text("${detail.result} · ${supportWord(detail.support)} · 反应时 ${detail.reaction}", modifier = Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (state.assessmentChanges.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text("复评前后变化", style = MaterialTheme.typography.titleMedium)
            state.assessmentChanges.forEach { change ->
                Text("${change.assessmentName} V${change.fromVersion} → V${change.toVersion}", modifier = Modifier.padding(top = 6.dp))
                if (change.changes.isEmpty()) {
                    Text("分数摘要未检测到变化", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    change.changes.forEach { item -> Text(item, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (state.profileEvidenceDetails.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text("能力画像证据回放", style = MaterialTheme.typography.titleMedium)
            state.profileEvidenceDetails.forEach { evidence ->
                Text("${evidence.assessmentName} V${evidence.version} · ${evidence.recordType} · ${evidence.date}", modifier = Modifier.padding(top = 6.dp))
                Text(evidence.source, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onPlanGoalChange: (String) -> Unit,
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
        PlanStatusStepper(state.planStatus)
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
            OutlinedTextField(state.planGoal, onPlanGoalChange, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, maxLines = 3, label = { Text("可观察目标") }, placeholder = { Text("例如：在 L1 支持下完成 4/5 次") })
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

/** 训练方案生命周期步骤条：草案 → 已确认 → 已生效，退回态单独标红。 */
@Composable
private fun PlanStatusStepper(status: PlanStatus?, modifier: Modifier = Modifier) {
    val labels = if (status == PlanStatus.REJECTED) listOf("已退回", "已确认", "已生效") else listOf("草案", "已确认", "已生效")
    val currentIndex = when (status) {
        null -> -1
        PlanStatus.DRAFT -> 0
        PlanStatus.CONFIRMED -> 1
        PlanStatus.ACTIVE, PlanStatus.SUPERSEDED, PlanStatus.ARCHIVED -> 2
        PlanStatus.REJECTED -> 0
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { index, label ->
            val stepState = when {
                status == PlanStatus.REJECTED && index == 0 -> PlanStepState.REJECTED
                index < currentIndex -> PlanStepState.DONE
                index == currentIndex -> PlanStepState.CURRENT
                else -> PlanStepState.TODO
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    PlanStepConnector(filled = index > 0 && index <= currentIndex, modifier = Modifier.weight(1f))
                    PlanStepCircle(stepState, index + 1)
                    PlanStepConnector(filled = index < currentIndex, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (stepState) {
                        PlanStepState.DONE -> Success
                        PlanStepState.CURRENT -> MaterialTheme.colorScheme.primary
                        PlanStepState.REJECTED -> Warning
                        PlanStepState.TODO -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private enum class PlanStepState { DONE, CURRENT, REJECTED, TODO }

@Composable
private fun PlanStepConnector(filled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(2.dp)
            .background(if (filled) Success else MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun PlanStepCircle(state: PlanStepState, number: Int) {
    val (bg, content, contentColor) = when (state) {
        PlanStepState.DONE -> Triple(Success, "✓", Color.White)
        PlanStepState.CURRENT -> Triple(MaterialTheme.colorScheme.primary, "$number", MaterialTheme.colorScheme.onPrimary)
        PlanStepState.REJECTED -> Triple(Warning, "!", Color.White)
        PlanStepState.TODO -> Triple(MaterialTheme.colorScheme.surfaceVariant, "$number", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box(
        modifier = Modifier.size(26.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(content, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

@Composable
private fun AgentPanel(state: ProfessionalUiState, onRefreshAudit: () -> Unit, onOpenAudit: (String) -> Unit, onAnnotateTrace: (String, String, String) -> Unit) {
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    SectionSurface(title = "Agent 运行", supporting = "本地可审计信息，不展示原始敏感数据。", containerColor = MaterialTheme.colorScheme.primaryContainer) {
        AgentStatusLine(status = state.agentStatus, working = state.isWorking)
        Spacer(Modifier.height(8.dp))
        StatusLine("最近事件", state.recentEvent)
        state.agentRunId?.let {
            Spacer(Modifier.height(8.dp))
            StatusLine("运行编号", it)
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("规则与工具", style = MaterialTheme.typography.titleMedium)
        Text(state.evidence.joinToString(" · "), modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Text("审计回放", style = MaterialTheme.typography.titleMedium)
        Text(state.auditMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.auditRuns.isEmpty()) {
            Text("智能体自动生成方案时会在此留下决策轨迹，专业人员可回放和标注。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Text(state.auditMetricsSummary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        OutlinedButton(onClick = onRefreshAudit, modifier = Modifier.padding(top = 8.dp)) { Text("刷新当前儿童运行记录") }
        state.auditRuns.forEach { run ->
            OutlinedButton(onClick = { onOpenAudit(run.runId) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("${timeFormat.format(Date(run.startedAt))} · ${run.taskType} · ${run.port} · ${run.status} · ${run.runId.takeLast(8)}")
            }
        }
        if (state.auditSelectedRunId != null) {
            Text("运行 ${state.auditSelectedRunId.takeLast(8)}", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp))
            state.auditReplay.forEach { line ->
                Text("${timeFormat.format(Date(line.timestamp))} · ${line.category} · ${line.description}", modifier = Modifier.padding(top = 5.dp))
                if (line.traceId != null) {
                    val traceId = line.traceId
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { onAnnotateTrace(state.auditSelectedRunId!!, traceId, "supported") }) { Text("依据充分") }
                        OutlinedButton(onClick = { onAnnotateTrace(state.auditSelectedRunId!!, traceId, "unsupported") }) { Text("无依据") }
                        OutlinedButton(onClick = { onAnnotateTrace(state.auditSelectedRunId!!, traceId, "uncertain") }) { Text("不确定") }
                    }
                }
            }
        }
    }
}
