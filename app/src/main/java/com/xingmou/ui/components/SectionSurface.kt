package com.xingmou.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SectionSurface(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    containerColor: Color? = null,
    immersive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val baseColor = containerColor ?: MaterialTheme.colorScheme.surface
    // 注意：Color.Transparent 的 RGB 为 0，直接 copy(alpha) 会变成半透明黑色，
    // 所以透明色必须原样保留，只有真实颜色才降透明度。
    val resolvedColor = when {
        !immersive -> baseColor
        baseColor.alpha == 0f -> Color.Transparent
        else -> baseColor.copy(alpha = 0.5f)
    }
    Surface(
        modifier = modifier,
        shape = if (immersive) androidx.compose.foundation.shape.RoundedCornerShape(28.dp) else MaterialTheme.shapes.large,
        color = resolvedColor,
        border = if (immersive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (!supporting.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun StatusLine(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    androidx.compose.foundation.layout.Row(modifier = modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
