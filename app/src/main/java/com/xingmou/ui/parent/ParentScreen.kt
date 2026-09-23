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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xingmou.ParentUiState
import com.xingmou.core.safety.SafeResponses
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.theme.BlueSoft
import com.xingmou.ui.theme.Warning

@Composable
fun ParentScreen(
    state: ParentUiState,
    onQueryChange: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("家庭观察与支持", style = MaterialTheme.typography.headlineMedium)
        Text("先记录事实，再从本地已审核知识中寻找可执行建议。", color = MaterialTheme.colorScheme.onSurfaceVariant)

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

        SectionSurface(title = "边界说明", containerColor = BlueSoft) {
            Text(SafeResponses.DISCLAIMER)
            Text("涉及诊疗判断、持续加重或紧急风险时，请联系有资质的专业人员。", modifier = Modifier.padding(top = 8.dp))
        }
    }
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
