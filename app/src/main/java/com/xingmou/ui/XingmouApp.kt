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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xingmou.XingmouViewModel
import com.xingmou.core.model.Port
import com.xingmou.ui.child.ChildScreen
import com.xingmou.ui.parent.ParentScreen
import com.xingmou.ui.professional.ProfessionalScreen
import com.xingmou.ui.theme.XingmouTheme

@Composable
fun XingmouApp(viewModel: XingmouViewModel) {
    val state by viewModel.uiState.collectAsState()
    val density = LocalDensity.current
    val fontScale = if (state.accessibility.largeText) 1.15f else 1.0f
    CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(density.density, fontScale)) {
        XingmouTheme(highContrast = state.accessibility.highContrast) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { ChildContextBar(state, viewModel::selectChild) },
                bottomBar = { AppStatusBand(aiConfigured = state.aiConfigured) }
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
private fun ChildContextBar(state: com.xingmou.XingmouUiState, onSelectChild: (String) -> Unit) {
    val expandedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("当前儿童：${state.activeChildAlias}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { expandedState.value = true }, enabled = state.availableChildren.isNotEmpty()) { Text("切换档案") }
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
}

@Composable
private fun PortContent(viewModel: XingmouViewModel, port: Port, modifier: Modifier) {
    val state by viewModel.uiState.collectAsState()
    when (port) {
        Port.CHILD -> ChildScreen(
            state = state.child,
            accessibility = state.accessibility,
            onChoice = viewModel::completeChildTask,
            onPause = viewModel::pauseChildTraining,
            onResume = viewModel::resumeChildTraining,
            onSpeechEnabledChange = viewModel::setSpeechEnabled,
            onSpeechRateChange = viewModel::setSpeechRate,
            onSpeechVolumeChange = viewModel::setSpeechVolume,
            onLargeTextChange = viewModel::setLargeText,
            onHighContrastChange = viewModel::setHighContrast,
            modifier = modifier
        )
        Port.PARENT -> ParentScreen(state.parent, viewModel::updateParentQuery, viewModel::askParentQuestion, modifier)
        Port.PROFESSIONAL -> ProfessionalScreen(
            state = state.professional,
            onReviewCommentChange = viewModel::updateReviewComment,
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
private fun AppStatusBand(aiConfigured: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "本地数据已连接 · AI：${if (aiConfigured) "已配置" else "本地安全模式"} · 不构成医学诊断",
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
