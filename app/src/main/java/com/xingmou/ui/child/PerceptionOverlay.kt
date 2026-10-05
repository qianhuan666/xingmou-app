package com.xingmou.ui.child

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.xingmou.ChildUiState

/**
 * 感知状态标签 + 小窗口预览 + 开关。
 */
@Composable
fun PerceptionOverlay(
    state: ChildUiState,
    onTogglePerception: (Boolean) -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    onAttachPreviewView: (PreviewView) -> Unit,
    onDetachPreviewView: (PreviewView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onTogglePerception(true)
    }

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
                    onCheckedChange = { enabled ->
                        if (enabled && !hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            onTogglePerception(enabled)
                        }
                    }
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
                if (state.perceptionPreview) {
                    val previewRef = remember { arrayOfNulls<PreviewView>(1) }
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FIT_CENTER
                            }.also {
                                previewRef[0] = it
                                onAttachPreviewView(it)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    DisposableEffect(Unit) {
                        onDispose { previewRef[0]?.let(onDetachPreviewView) }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.perceptionEmotion.isNotBlank()) {
                        Text(
                            text = "情绪: ${emotionLabel(state.perceptionEmotion)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (state.perceptionFocus.isNotBlank()) {
                        Text(
                            text = "专注: ${focusLabel(state.perceptionFocus)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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

/**
 * 主页（训练页）顶部的实时监测小窗口。
 * 仅在感知开启且预览开启时显示；与设置页共用同一 PreviewView 绑定（两处不会同时存在）。
 */
@Composable
fun PerceptionHomePreview(
    state: ChildUiState,
    onAttachPreviewView: (PreviewView) -> Unit,
    onDetachPreviewView: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 180.dp
) {
    if (!state.perceptionPreview) return
    val detectActive = state.autoDetect.phase != com.xingmou.DetectPhase.IDLE
    if (!state.perceptionEnabled && !detectActive) return

    // 纯圆形窗口，无卡片背景、无文字
    val previewRef = remember { arrayOfNulls<PreviewView>(1) }
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }.also {
                previewRef[0] = it
                onAttachPreviewView(it)
            }
        },
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    )
    DisposableEffect(Unit) {
        onDispose { previewRef[0]?.let(onDetachPreviewView) }
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
