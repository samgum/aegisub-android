package io.github.samgum.aegisub.feature.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.toPersistentList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.feature.editor.compact.EventEditSheet
import io.github.samgum.aegisub.feature.editor.compact.EventListScreen
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import io.github.samgum.aegisub.feature.editor.expanded.EditorTwoPane

/**
 * 编辑器入口屏：按 [EditorUiState] 分发，再按窗口宽度选 compact（列表+底栏）
 * 或 expanded（双栏列表|详情）布局。撤销/重做与编辑统一回写 [EditorViewModel]。
 *
 * @author 伤感咩吖
 */
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch { writeExportFile(context, uri, viewModel.exportContent()) }
        }
    }
    val onExport = { exportLauncher.launch("字幕工程.ass") }
    var showFindReplace by remember { mutableStateOf(false) }

    when (val s = state) {
        EditorUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is EditorUiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "加载失败：${s.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onBack) { Text("返回") }
                }
            }

        is EditorUiState.Loaded -> {
            // Material 宽度断点：< 600dp 为 Compact（手机竖屏），否则 Medium/Expanded（平板/横屏）
            val isCompact = LocalConfiguration.current.screenWidthDp < 600
            Box(Modifier.fillMaxSize()) {
                if (isCompact) {
                    CompactEditor(
                        script = s.script,
                        editingId = editingId,
                        onEventClick = { editingId = it.id },
                        onDismissEdit = { editingId = null },
                        onBack = onBack,
                        onOpenPreview = { onOpenPreview(viewModel.projectId) },
                        onExport = onExport,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        viewModel = viewModel,
                    )
                } else {
                    EditorTwoPane(
                        script = s.script,
                        editingId = editingId,
                        onEventClick = { editingId = it.id },
                        onBack = onBack,
                        onOpenPreview = { onOpenPreview(viewModel.projectId) },
                        onExport = onExport,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onTextChanged = viewModel::updateEventText,
                        onTimesChanged = viewModel::updateEventTimes,
                        onStyleChanged = viewModel::updateEventStyle,
                        onLayerChanged = viewModel::setEventLayer,
                    )
                }
                FloatingActionButton(
                    onClick = { showFindReplace = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Icon(Icons.Filled.Search, contentDescription = "查找替换") }
            }
        }
    }

    if (showFindReplace) {
        FindReplaceDialog(
            onDismiss = { showFindReplace = false },
            onReplace = { q, r, regex, ic ->
                viewModel.replaceAll(q, r, regex, ic)
                showFindReplace = false
            },
        )
    }
}

@Composable
private fun CompactEditor(
    script: AssScript,
    editingId: Long?,
    onEventClick: (AssEvent) -> Unit,
    onDismissEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    onExport: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    viewModel: EditorViewModel,
) {
    EventListScreen(
        events = script.events,
        onEventClick = onEventClick,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenPreview) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "预览")
            }
            TextButton(onClick = onExport) { Text("导出") }
            EditorActions(
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
            )
        },
    )
    script.events.firstOrNull { it.id == editingId }?.let { ev ->
        EventEditSheet(
            event = ev,
            styles = script.styles.map { it.name }.toPersistentList(),
            onDismiss = onDismissEdit,
            onTextChanged = { viewModel.updateEventText(ev.id, it) },
            onTimesChanged = { start, end -> viewModel.updateEventTimes(ev.id, start, end) },
            onStyleChanged = { viewModel.updateEventStyle(ev.id, it) },
            onLayerChanged = { viewModel.setEventLayer(ev.id, it) },
        )
    }
}

/** 把导出内容写入 SAF 返回的 URI（IO 线程）。 */
private suspend fun writeExportFile(context: Context, uri: Uri, content: String) =
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
    }

/**
 * 查找替换对话框：查找/替换文本 + 正则/忽略大小写选项，全部替换（一次撤销点）。
 *
 * @author 伤感咩吖
 */
@Composable
private fun FindReplaceDialog(
    onDismiss: () -> Unit,
    onReplace: (query: String, replacement: String, useRegex: Boolean, ignoreCase: Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var ignoreCase by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查找替换") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("查找") }, singleLine = true)
                OutlinedTextField(value = replacement, onValueChange = { replacement = it }, label = { Text("替换为") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useRegex, onCheckedChange = { useRegex = it })
                    Text("正则")
                    Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                    Text("忽略大小写")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onReplace(query, replacement, useRegex, ignoreCase) }) { Text("全部替换") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
