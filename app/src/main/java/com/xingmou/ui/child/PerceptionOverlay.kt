package com.xingmou.ui.child

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xingmou.ChildUiState

/**
 * 感知状态标签 + 开关。
 * 默认不显示摄像头预览，仅显示情绪/专注度文字标签。
 */
@Composable
fun PerceptionOverlay(
    state: ChildUiState,
    onTogglePerception: (Boolean) -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "感知守护",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = state.perceptionEnabled,
                    onCheckedChange = onTogglePerception
                )
            }
            if (state.perceptionEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("显示预览", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.perceptionPreview,
                        onCheckedChange = onTogglePreview
                    )
                }
                if (state.perceptionEmotion.isNotBlank()) {
                    Text(
                        text = "情绪: ${emotionLabel(state.perceptionEmotion)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (state.perceptionFocus.isNotBlank()) {
                    Text(
                        text = "专注: ${focusLabel(state.perceptionFocus)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (state.perceptionFeedback.isNotBlank()) {
                    Text(
                        text = state.perceptionFeedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun emotionLabel(name: String): String = when (name) {
    "HAPPY" -> "开心"
    "SAD" -> "难过"
    "ANGRY" -> "生气"
    "SURPRISED" -> "惊讶"
    "FEARFUL" -> "害怕"
    "DISGUSTED" -> "厌恶"
    "NEUTRAL" -> "平静"
    "CRYING" -> "哭泣"
    "LAUGHING" -> "大笑"
    else -> name
}

private fun focusLabel(name: String): String = when (name) {
    "FOCUSED" -> "专注"
    "DISTRACTED" -> "分心"
    "SLEEPING" -> "困倦"
    else -> name
}
