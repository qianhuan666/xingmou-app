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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xingmou.AccessibilityUiState
import com.xingmou.ChildUiState
import com.xingmou.ui.components.SectionSurface
import com.xingmou.ui.components.StatusLine
import com.xingmou.ui.components.XiaoXingMark
import com.xingmou.ui.theme.Error

@Composable
fun ChildScreen(
    state: ChildUiState,
    accessibility: AccessibilityUiState,
    onChoice: (Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSpeechEnabledChange: (Boolean) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onSpeechVolumeChange: (Float) -> Unit,
    onLargeTextChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speechController = remember(context) { ChildSpeechController(context) }
    DisposableEffect(speechController) {
        onDispose { speechController.shutdown() }
    }
    LaunchedEffect(accessibility.speechRate, accessibility.speechVolume) {
        speechController.setSpeechRate(accessibility.speechRate)
        speechController.setSpeechVolume(accessibility.speechVolume)
    }
    LaunchedEffect(state.message, accessibility.speechEnabled, state.isPaused, state.isSafetyStopped) {
        if (accessibility.speechEnabled && !state.isWorking) speechController.speak(state.message)
        if (!accessibility.speechEnabled) speechController.stop()
    }
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
            containerColor = if (state.isSafetyStopped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
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

        SectionSurface(title = "今天的支持", containerColor = MaterialTheme.colorScheme.secondaryContainer) {
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

        SectionSurface(title = "辅助设置", supporting = "设置只保存在本机，用来调整小星的呈现方式。") {
            SettingRow("小星朗读", "朗读儿童端短句", accessibility.speechEnabled) { onSpeechEnabledChange(it) }
            Spacer(Modifier.height(8.dp))
            Text("语速：${"%.2f".format(accessibility.speechRate)}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = accessibility.speechRate,
                onValueChange = onSpeechRateChange,
                onValueChangeFinished = {
                    if (accessibility.speechEnabled) speechController.speak("小星会用这个速度说话。")
                },
                valueRange = 0.75f..1.25f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Text("音量：${(accessibility.speechVolume * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = accessibility.speechVolume,
                onValueChange = onSpeechVolumeChange,
                onValueChangeFinished = {
                    if (accessibility.speechEnabled) speechController.speak("这是现在的朗读音量。")
                },
                valueRange = 0.5f..1.0f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )
            SettingRow("大字体", "增加界面文字大小", accessibility.largeText) { onLargeTextChange(it) }
            Spacer(Modifier.height(8.dp))
            SettingRow("高对比", "提高文字与表面的对比度", accessibility.highContrast) { onHighContrastChange(it) }
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
