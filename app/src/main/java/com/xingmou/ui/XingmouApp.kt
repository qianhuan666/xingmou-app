package com.xingmou.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.xingmou.XingmouViewModel
import com.xingmou.core.model.Port
import com.xingmou.ui.child.ChildScreen
import com.xingmou.ui.parent.ParentScreen
import com.xingmou.ui.professional.ProfessionalScreen
import com.xingmou.ui.theme.XingmouTheme

@Composable
fun XingmouApp(viewModel: XingmouViewModel) {
    val state by viewModel.uiState.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        destination -> destination?.let { viewModel.exportAuthorizedData(it, "json") }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        destination -> destination?.let { viewModel.exportAuthorizedData(it, "csv") }
    }
    val density = LocalDensity.current
    val fontScale = if (state.accessibility.largeText) 1.15f else 1.0f
    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
        XingmouTheme(highContrast = state.accessibility.highContrast) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    ChildContextBar(
                        state = state,
                        onSelectChild = viewModel::selectChild,
                        onCreateChild = viewModel::createLocalChild,
                        onUpdateChild = viewModel::updateActiveChild,
                        onArchiveChild = viewModel::archiveActiveChild,
                        onRemoteAiConsentChange = viewModel::setRemoteAiConsent,
                        onExportConsentChange = viewModel::setExportConsent,
                        onExportAuthorizedData = {
                            exportLauncher.launch("xingmou-${state.activeChildId}-${System.currentTimeMillis()}.json")
                        },
                        onExportAuthorizedCsv = {
                            csvExportLauncher.launch("xingmou-${state.activeChildId}-${System.currentTimeMillis()}.csv")
                        },
                        onDeleteChild = viewModel::deleteActiveChild,
                        onSaveApiKey = viewModel::saveInstitutionApiKey,
                        onClearApiKey = viewModel::clearInstitutionApiKey
                    )
                },
                bottomBar = { AppStatusBand(aiConfigured = state.aiConfigured, remoteAiConsent = state.remoteAiConsent) }
            ) { padding ->
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                    if (maxWidth >= 840.dp) {
                        Row(Modifier.fillMaxSize()) {
                            PortRail(state.selectedPort, viewModel::selectPort)
                            PortContent(viewModel, state.selectedPort, Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            CompactPortSelector(state.selectedPort, viewModel::selectPort)
                            PortContent(viewModel, state.selectedPort, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChildContextBar(
    state: com.xingmou.XingmouUiState,
    onSelectChild: (String) -> Unit,
    onCreateChild: (String, String) -> Unit,
    onUpdateChild: (String, String) -> Unit,
    onArchiveChild: () -> Unit,
    onRemoteAiConsentChange: (Boolean) -> Unit,
    onExportConsentChange: (Boolean) -> Unit,
    onExportAuthorizedData: () -> Unit,
    onExportAuthorizedCsv: () -> Unit,
    onDeleteChild: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit
) {
    val expandedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val dialogMode = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val aliasState = androidx.compose.runtime.remember(state.activeChildAlias) { androidx.compose.runtime.mutableStateOf(state.activeChildAlias) }
    val ageBandState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("学龄期") }
    val consentOpen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val deleteConfirm = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val apiKeyOpen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val apiKeyInput = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("当前儿童：${state.activeChildAlias}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { expandedState.value = true }, enabled = state.availableChildren.isNotEmpty()) { Text("切换档案") }
            TextButton(onClick = { aliasState.value = ""; ageBandState.value = "学龄期"; dialogMode.value = "create" }) { Text("新建") }
            TextButton(onClick = { dialogMode.value = "edit" }) { Text("编辑") }
            TextButton(onClick = onArchiveChild, enabled = state.availableChildren.size > 1) { Text("归档") }
            TextButton(onClick = { consentOpen.value = true }) { Text("授权") }
            if (state.selectedPort == Port.PROFESSIONAL) {
                TextButton(onClick = { apiKeyInput.value = ""; apiKeyOpen.value = true }) { Text("机构 API Key") }
            }
            DropdownMenu(expanded = expandedState.value, onDismissRequest = { expandedState.value = false }) {
                state.availableChildren.forEach { child ->
                    DropdownMenuItem(
                        text = { Text("${child.alias} · ${child.ageBand}") },
                        onClick = { expandedState.value = false; onSelectChild(child.childId) }
                    )
                }
            }
        }
    }
    if (apiKeyOpen.value) {
        AlertDialog(
            onDismissRequest = { apiKeyInput.value = ""; apiKeyOpen.value = false },
            title = { Text("API Key 设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("由机构使用者自行填写 DeepSeek Key，保存在当前设备。设备持有 Key 可能被提取并产生费用；远程调用仍需当前儿童的明确授权。", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = apiKeyInput.value,
                        onValueChange = { apiKeyInput.value = it.take(256) },
                        label = { Text("DeepSeek API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Text(state.apiKeyMessage, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        onClearApiKey()
                        apiKeyInput.value = ""
                    }, enabled = state.aiConfigured) { Text("清除已保存 Key") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSaveApiKey(apiKeyInput.value)
                    apiKeyInput.value = ""
                }, enabled = apiKeyInput.value.isNotBlank()) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { apiKeyInput.value = ""; apiKeyOpen.value = false }) { Text("关闭") }
            }
        )
    }
    dialogMode.value?.let { mode ->
        AlertDialog(
            onDismissRequest = { dialogMode.value = null },
            title = { Text(if (mode == "create") "新建儿童档案" else "编辑儿童档案") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = aliasState.value, onValueChange = { aliasState.value = it }, label = { Text("化名") })
                    OutlinedTextField(value = ageBandState.value, onValueChange = { ageBandState.value = it }, label = { Text("年龄段") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (mode == "create") onCreateChild(aliasState.value, ageBandState.value)
                    else onUpdateChild(aliasState.value, ageBandState.value)
                    dialogMode.value = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { dialogMode.value = null }) { Text("取消") } }
        )
    }
    if (consentOpen.value) {
        AlertDialog(
            onDismissRequest = { consentOpen.value = false },
            title = { Text("数据授权") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("远程 AI", style = MaterialTheme.typography.titleMedium)
                            Text("仅在明确同意后允许发送脱敏请求。", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.remoteAiConsent, onCheckedChange = onRemoteAiConsentChange)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("数据导出", style = MaterialTheme.typography.titleMedium)
                            Text("仅导出当前儿童授权范围内的数据；保存后的文件需自行保管，删档不会删除该副本。", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = state.exportConsent, onCheckedChange = onExportConsentChange)
                    }
                    Text(
                        state.dataRightsMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onExportAuthorizedData,
                        enabled = state.exportConsent && !state.dataRightsWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.dataRightsWorking) "处理中…" else "导出当前儿童 JSON")
                    }
                    OutlinedButton(
                        onClick = onExportAuthorizedCsv,
                        enabled = state.exportConsent && !state.dataRightsWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("导出当前儿童 CSV") }
                    OutlinedButton(
                        onClick = { deleteConfirm.value = true },
                        enabled = state.availableChildren.size > 1 && !state.dataRightsWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("删除当前儿童档案")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { consentOpen.value = false }) { Text("完成") } }
        )
    }
    if (deleteConfirm.value) {
        AlertDialog(
            onDismissRequest = { deleteConfirm.value = false },
            title = { Text("确认删除儿童档案？") },
            text = { Text("将删除当前儿童的训练、家庭、评估、方案和本地 Agent 数据。删除结果记录会保留，操作不可撤销。") },
            confirmButton = {
                Button(onClick = {
                    deleteConfirm.value = false
                    consentOpen.value = false
                    onDeleteChild()
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm.value = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun PortContent(viewModel: XingmouViewModel, port: Port, modifier: Modifier) {
    val state by viewModel.uiState.collectAsState()
    when (port) {
        Port.CHILD -> ChildScreen(
            state = state.child,
            baseline = state.baseline,
            accessibility = state.accessibility,
            onChoice = viewModel::completeChildTask,
            onStartBaseline = viewModel::startBaseline,
            onResumeBaseline = viewModel::resumeBaseline,
            onLeaveBaseline = viewModel::leaveBaseline,
            onRestartBaseline = viewModel::restartBaseline,
            onBaselineAnswer = viewModel::answerBaseline,
            onStartCourse = viewModel::startCourse,
            onLeaveCourse = viewModel::leaveCourse,
            onResumeCourse = viewModel::resumeCourse,
            onPause = viewModel::pauseChildTraining,
            onResume = viewModel::resumeChildTraining,
            onSpeechEnabledChange = viewModel::setSpeechEnabled,
            onSpeechRateChange = viewModel::setSpeechRate,
            onSpeechVolumeChange = viewModel::setSpeechVolume,
            onLargeTextChange = viewModel::setLargeText,
            onHighContrastChange = viewModel::setHighContrast,
            onSlowMotionChange = viewModel::setSlowMotion,
            onInterestChange = viewModel::selectInterest,
            modifier = modifier
        )
        Port.PARENT -> ParentScreen(
            state = state.parent,
            onQueryChange = viewModel::updateParentQuery,
            onAsk = viewModel::askParentQuestion,
            onCompleteTask = viewModel::completeHomeTask,
            onSkipTask = viewModel::skipHomeTask,
            onPauseTask = viewModel::pauseHomeTask,
            onAdvanceDemo = viewModel::advanceHomeDemo,
            onMoodChange = viewModel::updateFeedbackMood,
            onFatigueChange = viewModel::updateFeedbackFatigue,
            onFeedbackNoteChange = viewModel::updateFeedbackNote,
            onSubmitFeedback = viewModel::submitHomeFeedback,
            modifier = modifier
        )
        Port.PROFESSIONAL -> ProfessionalScreen(
            state = state.professional,
            onReviewCommentChange = viewModel::updateReviewComment,
            onPlanTaskChange = viewModel::updatePlanTask,
            onPlanGoalChange = viewModel::updatePlanGoal,
            onPlanDifficultyChange = viewModel::updatePlanDifficulty,
            onPlanSupportLevelChange = viewModel::updatePlanSupportLevel,
            onPlanFrequencyChange = viewModel::updatePlanFrequency,
            onPlanDurationChange = viewModel::updatePlanDuration,
            onPlanStopConditionsChange = viewModel::updatePlanStopConditions,
            onCreateRevision = viewModel::createPlanRevision,
            onAdvanceCareStage = viewModel::advanceCareStage,
            onCareNoteChange = viewModel::updateCareNote,
            onCareClosureReasonChange = viewModel::updateCareClosureReason,
            onCareFollowUpPlanChange = viewModel::updateCareFollowUpPlan,
            onCareFollowUpDateChange = viewModel::updateCareFollowUpDate,
            onAssessmentSelect = viewModel::selectAssessment,
            onAssessmentDateChange = viewModel::updateAssessmentDate,
            onAssessmentSourceChange = viewModel::updateAssessmentSource,
            onAssessmentScoresChange = viewModel::updateAssessmentScores,
            onAssessmentNotesChange = viewModel::updateAssessmentNotes,
            onSaveAssessment = viewModel::saveAssessmentRecord,
            onCreateDraft = viewModel::createPlanDraft,
            onConfirm = viewModel::confirmPlanDraft,
            onActivate = viewModel::activatePlan,
            onReject = viewModel::rejectPlan,
            onRefresh = viewModel::refreshProfessionalAnalysis,
            modifier = modifier
        )
    }
}

@Composable
private fun PortRail(selected: Port, onSelect: (Port) -> Unit) {
    NavigationRail(
        modifier = Modifier.width(128.dp).fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Text("星眸", modifier = Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.titleLarge)
        Port.entries.forEach { port ->
            NavigationRailItem(
                selected = selected == port,
                onClick = { onSelect(port) },
                icon = { Text(portGlyph(port), style = MaterialTheme.typography.titleLarge) },
                label = { Text(portLabel(port), maxLines = 1) },
                alwaysShowLabel = true
            )
        }
    }
}

@Composable
private fun CompactPortSelector(selected: Port, onSelect: (Port) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Port.entries.forEach { port ->
            Surface(
                onClick = { onSelect(port) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = if (selected == port) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (selected == port) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    portLabel(port),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AppStatusBand(aiConfigured: Boolean, remoteAiConsent: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "本地数据已连接 · AI：${when {
                    !aiConfigured -> "本地安全模式"
                    !remoteAiConsent -> "Key 已设置，待当前儿童授权"
                    else -> "设备直连已就绪"
                }} · 不构成医学诊断",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun portLabel(port: Port): String = when (port) {
    Port.CHILD -> "儿童端"
    Port.PARENT -> "家长端"
    Port.PROFESSIONAL -> "专业端"
}

private fun portGlyph(port: Port): String = when (port) {
    Port.CHILD -> "童"
    Port.PARENT -> "家"
    Port.PROFESSIONAL -> "专"
}
