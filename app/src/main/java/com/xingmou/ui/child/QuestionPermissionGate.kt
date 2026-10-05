package com.xingmou.ui.child

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 运行时权限说明卡：点「好呀」才拉起系统弹窗；系统不再给弹窗时改为「去设置打开」。
 * onResult(true) 表示已授权可开始检测；onManual 表示孩子选择手动作答。
 */
@androidx.compose.runtime.Composable
fun QuestionPermissionGate(
    permission: String,
    onResult: (Boolean) -> Unit,
    onManual: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun granted() = ContextCompat.checkSelfPermission(context, permission) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    var grantedState by remember { mutableStateOf(granted()) }
    var deniedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            grantedState = true
            onResult(true)
        } else {
            deniedOnce = true
            onResult(false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && granted()) {
                grantedState = true
                onResult(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isCamera = permission == Manifest.permission.CAMERA
    val emoji = if (isCamera) "📷" else "🎤"
    val ask = if (isCamera) "小星想看看你的小手" else "小星想听听你的声音"
    // deniedOnce 且系统不再解释 → 只能去设置
    val mustOpenSettings = deniedOnce && !shouldShowRationale(context, permission)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(ask, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (grantedState) { onResult(true); return@Button }
                if (mustOpenSettings) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                } else {
                    launcher.launch(permission)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text(if (mustOpenSettings) "去设置打开" else "好呀") }
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("先点按钮做")
        }
    }
}

private fun shouldShowRationale(
    context: android.content.Context,
    permission: String
): Boolean {
    val activity = context as? android.app.Activity ?: return false
    return activity.shouldShowRequestPermissionRationale(permission)
}
