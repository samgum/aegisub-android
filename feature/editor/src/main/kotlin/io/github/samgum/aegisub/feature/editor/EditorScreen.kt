package io.github.samgum.aegisub.feature.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.feature.editor.compact.EventEditSheet
import io.github.samgum.aegisub.feature.editor.compact.EventListScreen
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import io.github.samgum.aegisub.feature.editor.expanded.EditorTwoPane

/**
 * 编辑器入口屏：按 [EditorUiState] 分发，再按窗口宽度选 compact（列表+底栏）
 * 或 expanded（双栏列表|详情）布局。撤销/重做与编辑统一回写 [EditorViewModel]。
 * 右下角工具箱 FAB 汇聚查找替换/时间偏移/删除空行/样式批量替换。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onOpenStyles: (Long) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
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
    var showToolbox by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showShiftTimes by remember { mutableStateOf(false) }
    var showDeleteEmpty by remember { mutableStateOf(false) }
    var showStyleReplace by remember { mutableStateOf(false) }

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
            // 布局：用户设置优先（COMPACT/EXPANDED 强制），AUTO 时按 Material 宽度断点
            // < 600dp 为 Compact（手机竖屏），否则 Medium/Expanded（平板/横屏）
            val isCompact = when (layoutMode) {
                LayoutMode.COMPACT -> true
                LayoutMode.EXPANDED -> false
                LayoutMode.AUTO -> LocalConfiguration.current.screenWidthDp < 600
            }
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
                    onClick = { showToolbox = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) { Icon(Icons.Filled.Build, contentDescription = "批量工具") }
            }
        }
    }

    if (showToolbox) {
        ToolboxSheet(
            onDismiss = { showToolbox = false },
            onFindReplace = { showToolbox = false; showFindReplace = true },
            onShiftTimes = { showToolbox = false; showShiftTimes = true },
            onDeleteEmpty = { showToolbox = false; showDeleteEmpty = true },
            onStyleReplace = { showToolbox = false; showStyleReplace = true },
            onOpenStyleManager = { showToolbox = false; onOpenStyles(viewModel.projectId) },
        )
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

    if (showShiftTimes) {
        ShiftTimesDialog(
            onDismiss = { showShiftTimes = false },
            onApply = { deltaMs, target, onlyAfterSelected ->
                val fromStart = if (onlyAfterSelected) currentSelectedStart(state, editingId) else null
                viewModel.shiftTimes(deltaMs, target, fromStart)
                showShiftTimes = false
            },
        )
    }

    if (showDeleteEmpty) {
        AlertDialog(
            onDismissRequest = { showDeleteEmpty = false },
            title = { Text("删除空行") },
            text = { Text("删除所有「有效内容为空」的行（纯空、仅空白、仅覆盖标签）。绘图行保留。此操作可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEmptyLines()
                    showDeleteEmpty = false
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteEmpty = false }) { Text("取消") } },
        )
    }

    if (showStyleReplace) {
        val styles = (state as? EditorUiState.Loaded)?.script?.styles?.map { it.name } ?: emptyList()
        StyleReplaceDialog(
            styles = styles,
            onDismiss = { showStyleReplace = false },
            onApply = { from, to ->
                viewModel.replaceStyles(from, to)
                showStyleReplace = false
            },
        )
    }
}

/** 取当前选中行的起始毫秒（用于"仅平移选中及之后"）。无选中返回 null。 */
private fun currentSelectedStart(state: EditorUiState, editingId: Long?): Long? {
    val loaded = state as? EditorUiState.Loaded ?: return null
    val ev = loaded.script.events.firstOrNull { it.id == editingId } ?: return null
    return ev.start.millis
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

/**
 * 工具箱底部弹层：列出批量操作入口。每项点击后关闭本层并打开对应弹层。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolboxSheet(
    onDismiss: () -> Unit,
    onFindReplace: () -> Unit,
    onShiftTimes: () -> Unit,
    onDeleteEmpty: () -> Unit,
    onStyleReplace: () -> Unit,
    onOpenStyleManager: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            "批量工具",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                ToolEntry(Icons.Filled.Search, "查找替换", "正则 / 忽略大小写，全部替换（一次撤销）") { onFindReplace() }
            }
            item {
                ToolEntry(Icons.AutoMirrored.Filled.ArrowForward, "时间偏移", "整体前移/后移，可仅作用于选中行之后") { onShiftTimes() }
            }
            item {
                ToolEntry(Icons.Filled.Delete, "删除空行", "移除纯空 / 仅标签的行，保留绘图行") { onDeleteEmpty() }
            }
            item {
                ToolEntry(Icons.Filled.Edit, "样式批量替换", "把指定样式名的事件改为另一样式") { onStyleReplace() }
            }
            item {
                ToolEntry(Icons.Filled.Build, "样式管理器", "编辑颜色 / 字体 / 描边 / 对齐 / 边距 / 编码") { onOpenStyleManager() }
            }
            item { HorizontalDivider() }
        }
    }
}

@Composable
private fun ToolEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

/**
 * 时间偏移对话框：输入毫秒偏移 + 作用对象（两者/起始/结束）+ 仅选中行之后。
 *
 * @author 伤感咩吖
 */
@Composable
private fun ShiftTimesDialog(
    onDismiss: () -> Unit,
    onApply: (deltaMs: Long, target: ShiftTarget, onlyAfterSelected: Boolean) -> Unit,
) {
    var deltaText by remember { mutableStateOf("0") }
    var target by remember { mutableStateOf(ShiftTarget.BOTH) }
    var onlyAfter by remember { mutableStateOf(false) }
    val delta = deltaText.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时间偏移") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text("偏移（毫秒，负=前移）") },
                    singleLine = true,
                )
                Text("作用对象", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = target == ShiftTarget.BOTH, onClick = { target = ShiftTarget.BOTH }, label = { Text("起止") })
                    FilterChip(selected = target == ShiftTarget.START, onClick = { target = ShiftTarget.START }, label = { Text("仅起始") })
                    FilterChip(selected = target == ShiftTarget.END, onClick = { target = ShiftTarget.END }, label = { Text("仅结束") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyAfter, onCheckedChange = { onlyAfter = it })
                    Text("仅作用于当前选中行及之后")
                }
                Text(
                    if (delta >= 0) "整体后移 ${delta}ms" else "整体前移 ${-delta}ms（越界自动钳零）",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(delta, target, onlyAfter) }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 样式批量替换对话框：从已有样式名选源/目标，替换（一次撤销点）。
 *
 * @author 伤感咩吖
 */
@Composable
private fun StyleReplaceDialog(
    styles: List<String>,
    onDismiss: () -> Unit,
    onApply: (fromStyle: String, toStyle: String) -> Unit,
) {
    val distinct = styles.distinct()
    var from by remember { mutableStateOf(distinct.firstOrNull() ?: "") }
    var to by remember { mutableStateOf(distinct.firstOrNull() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("样式批量替换") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("把所有「${if (from.isEmpty()) "（空）" else from}」样式的事件改为另一样式。", style = MaterialTheme.typography.bodySmall)
                StyleDropdown("原样式", distinct, from) { from = it }
                StyleDropdown("新样式", distinct, to) { to = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(from, to) }, enabled = from.isNotEmpty()) { Text("替换") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) { Text("▾") }
            },
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { name ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(name); expanded = false },
                )
            }
        }
    }
}
