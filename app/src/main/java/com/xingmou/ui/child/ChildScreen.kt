package com.xingmou.ui.child

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xingmou.ChildUiState
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.components.XiaoXingMark
import com.xingmou.ui.theme.BlueSoft
import com.xingmou.ui.theme.CoralSoft
import com.xingmou.ui.theme.Error

@Composable
fun ChildScreen(
    state: ChildUiState,
    onChoice: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            XiaoXingMark(Modifier.size(52.dp))
            Column {
                Text("和小星一起练习", style = MaterialTheme.typography.headlineMedium)
                Text("一次只做一步，随时可以休息。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionSurface(
            title = if (state.isSafetyStopped) "先找身边的大人" else if (state.isPaused) "休息时间" else state.instruction,
            supporting = state.message,
            containerColor = if (state.isSafetyStopped) CoralSoft else MaterialTheme.colorScheme.surface
        ) {
            if (state.isSafetyStopped) {
                Text("训练已经停止。请不要继续操作。", color = Error, style = MaterialTheme.typography.titleMedium)
            } else if (state.isPaused) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    enabled = !state.isWorking
                ) { Text(if (state.isWorking) "请稍等" else "准备好了，继续") }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val horizontal = maxWidth >= 520.dp
                    if (horizontal) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ChoiceButton(state.options.getOrElse(0) { "圆形" }, true, onChoice, Modifier.weight(1f), !state.isWorking)
                            ChoiceButton(state.options.getOrElse(1) { "三角形" }, false, onChoice, Modifier.weight(1f), !state.isWorking)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ChoiceButton(state.options.getOrElse(0) { "圆形" }, true, onChoice, Modifier.fillMaxWidth(), !state.isWorking)
                            ChoiceButton(state.options.getOrElse(1) { "三角形" }, false, onChoice, Modifier.fillMaxWidth(), !state.isWorking)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPause,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    enabled = !state.isWorking
                ) { Text("先休息") }
            }
        }

        SectionSurface(title = "今天的支持", containerColor = BlueSoft) {
            StatusLine("难度", "第 ${state.difficulty} 级")
            Spacer(Modifier.height(8.dp))
            StatusLine("支持", state.supportLevel.name)
            Spacer(Modifier.height(8.dp))
            StatusLine("最近状态", state.lastEvent)
            Text(
                "这里不展示分数和排名，只记录下一步需要多少支持。",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChoiceButton(label: String, correct: Boolean, onChoice: (Boolean) -> Unit, modifier: Modifier, enabled: Boolean) {
    OutlinedButton(
        onClick = { onChoice(correct) },
        modifier = modifier.heightIn(min = 76.dp),
        enabled = enabled
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}
