package com.xingmou.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xingmou.ui.theme.Coral
import com.xingmou.ui.theme.CoralSoft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun XiaoXingMark(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.semantics { contentDescription = "小星标记" }) {
        drawCircle(color = CoralSoft, radius = size.minDimension / 2f)
        drawStar(color = Coral, radius = size.minDimension * 0.29f)
    }
}

private fun DrawScope.drawStar(color: Color, radius: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val path = Path()
    repeat(10) { index ->
        val angle = -PI / 2 + index * PI / 5
        val pointRadius = if (index % 2 == 0) radius else radius * 0.45f
        val point = Offset(
            center.x + cos(angle).toFloat() * pointRadius,
            center.y + sin(angle).toFloat() * pointRadius
        )
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, color)
}
