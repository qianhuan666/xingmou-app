package com.xingmou.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xingmou.agentStateLabel
import com.xingmou.core.agent.AgentRunState

/** Agent 实时状态指示：运行中呼吸点 + 友好进度文案，结束态按结果着色。 */
@Composable
fun AgentStatusLine(status: String, working: Boolean, modifier: Modifier = Modifier) {
    val dotColor = when {
        working -> Color(0xFFF9A825)
        status == agentStateLabel(AgentRunState.COMPLETED) -> Color(0xFF2E7D32)
        status == agentStateLabel(AgentRunState.SAFETY_STOP) || status == agentStateLabel(AgentRunState.FAILED) -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.outline
    }
    val alpha = if (working) {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse)).value
    } else 1f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor.copy(alpha = alpha)))
        Text("Agent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(status, style = MaterialTheme.typography.labelMedium)
    }
}
