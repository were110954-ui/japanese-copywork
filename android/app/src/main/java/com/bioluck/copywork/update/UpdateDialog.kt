package com.bioluck.copywork.update

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun InAppUpdateHost() {
    val activity = LocalContext.current as Activity
    val updater = remember(activity) { AppUpdater(activity) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(updater) { state = updater.check() }
    if (dismissed || state is UpdateState.Idle) return
    val manifest = when (val current = state) {
        is UpdateState.Available -> current.manifest
        is UpdateState.Downloading -> current.manifest
        is UpdateState.Ready -> current.manifest
        is UpdateState.Error -> current.manifest
        UpdateState.Idle -> null
    }

    AlertDialog(
        onDismissRequest = { if (state !is UpdateState.Downloading) dismissed = true },
        title = { Text(manifest?.let { "새로운 버전(v${it.versionName}) 업데이트 안내" } ?: "업데이트 확인 실패") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val current = state) {
                    is UpdateState.Available -> Text(current.manifest.changelog.ifBlank { "새 버전이 준비되었습니다." }, Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
                    is UpdateState.Downloading -> {
                        val determinate = current.total > 0
                        if (determinate) LinearProgressIndicator({ current.bytes.toFloat() / current.total }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("${formatBytes(current.bytes)}${if (determinate) " / ${formatBytes(current.total)}" else ""} 다운로드 중…")
                    }
                    is UpdateState.Ready -> Text("다운로드와 보안 검증이 완료되었습니다. 설치 화면을 여세요.")
                    is UpdateState.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
                    UpdateState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (val current = state) {
                is UpdateState.Available -> Button({ scope.launch {
                    state = UpdateState.Downloading(current.manifest, 0, -1)
                    state = updater.download(current.manifest) { progress -> activity.runOnUiThread { state = progress } }
                    (state as? UpdateState.Ready)?.let { updater.install(it.apk) }
                } }) { Text("지금 업데이트") }
                is UpdateState.Ready -> Button({ updater.install(current.apk) }) { Text("설치 화면 열기") }
                is UpdateState.Error -> current.manifest?.let { info -> Button({ state = UpdateState.Available(info) }) { Text("다시 시도") } }
                else -> Unit
            }
        },
        dismissButton = { if (state !is UpdateState.Downloading) TextButton({ dismissed = true }) { Text("다음에 하기") } },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
