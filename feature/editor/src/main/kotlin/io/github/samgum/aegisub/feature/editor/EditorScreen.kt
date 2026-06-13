package io.github.samgum.aegisub.feature.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.feature.editor.R
import io.github.samgum.aegisub.domain.edit.FramerateConverter
import io.github.samgum.aegisub.domain.edit.KaraokeMode
import io.github.samgum.aegisub.domain.edit.ScriptInfoOps
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.SrtFormat
import io.github.samgum.aegisub.domain.format.SubtitleFormat
import io.github.samgum.aegisub.domain.format.VttFormat
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.feature.editor.compact.EventEditSheet
import io.github.samgum.aegisub.feature.editor.compact.EventListScreen
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import io.github.samgum.aegisub.feature.editor.components.SelectionActionBar
import io.github.samgum.aegisub.feature.editor.components.StylingAssistantSheet
import io.github.samgum.aegisub.feature.editor.components.TranslationAssistantSheet
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
    val snapshots by viewModel.snapshotList.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    fun exitSelection() { selectionMode = false; selectedIds = emptySet() }
    fun enterSelection(id: Long) { selectionMode = true; selectedIds = setOf(id) }
    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportFormat by remember { mutableStateOf<SubtitleFormat?>(null) }
    var showExportFormat by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val fmt = pendingExportFormat
        pendingExportFormat = null
        if (uri != null && fmt != null) {
            scope.launch { writeExportFile(context, uri, viewModel.exportAs(fmt)) }
        }
    }
    val onExport = { showExportFormat = true }
    var showToolbox by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showShiftTimes by remember { mutableStateOf(false) }
    var showDeleteEmpty by remember { mutableStateOf(false) }
    var showStyleReplace by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showFramerate by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }
    var showStyling by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var showKaraoke by remember { mutableStateOf(false) }
    var showTimingPP by remember { mutableStateOf(false) }
    var showResample by remember { mutableStateOf(false) }

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
                        onBack = { if (selectionMode) exitSelection() else onBack() },
                        onOpenPreview = { onOpenPreview(viewModel.projectId) },
                        onExport = onExport,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        viewModel = viewModel,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelect = ::toggleSelect,
                        onEnterSelection = ::enterSelection,
                    )
                } else {
                    EditorTwoPane(
                        script = s.script,
                        editingId = editingId,
                        onEventClick = { editingId = it.id },
                        onBack = { if (selectionMode) exitSelection() else onBack() },
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
                        onLineAction = viewModel::applyLineAction,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelect = ::toggleSelect,
                        onEnterSelection = ::enterSelection,
                    )
                }
                if (selectionMode) {
                    SelectionActionBar(
                        count = selectedIds.size,
                        total = s.script.events.size,
                        onMoveUp = {
                            viewModel.moveSelectedUp(selectedIds)
                            // 移动后 id 集合不变（事件身份随 id 走）
                        },
                        onMoveDown = { viewModel.moveSelectedDown(selectedIds) },
                        onDuplicate = {
                            viewModel.duplicateSelected(selectedIds)
                            exitSelection()
                        },
                        onDelete = {
                            viewModel.deleteSelected(selectedIds)
                            exitSelection()
                        },
                        onSelectAll = { selectedIds = s.script.events.map { it.id }.toSet() },
                        onCancel = ::exitSelection,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (!selectionMode) {
                    FloatingActionButton(
                        onClick = { showToolbox = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) { Icon(Icons.Filled.Build, contentDescription = "批量工具") }
                }
            }
        }
    }

    if (showToolbox) {
        ToolboxSheet(
            onDismiss = { showToolbox = false },
            onFindReplace = { showToolbox = false; showFindReplace = true },
            onShiftTimes = { showToolbox = false; showShiftTimes = true },
            onSort = { showToolbox = false; showSort = true },
            onFramerate = { showToolbox = false; showFramerate = true },
            onProperties = { showToolbox = false; showProperties = true },
            onStyling = { showToolbox = false; showStyling = true },
            onTranslation = { showToolbox = false; showTranslation = true },
            onKaraoke = { showToolbox = false; showKaraoke = true },
            onTimingPP = { showToolbox = false; showTimingPP = true },
            onResample = { showToolbox = false; showResample = true },
            onDeleteEmpty = { showToolbox = false; showDeleteEmpty = true },
            onStyleReplace = { showToolbox = false; showStyleReplace = true },
            onOpenStyleManager = { showToolbox = false; onOpenStyles(viewModel.projectId) },
            onOpenHistory = { showToolbox = false; showHistory = true },
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

    if (showSort) {
        SortDialog(
            onDismiss = { showSort = false },
            onApply = { key, order ->
                viewModel.sortLines(key, order)
                showSort = false
            },
        )
    }

    if (showFramerate) {
        FramerateDialog(
            onDismiss = { showFramerate = false },
            onApply = { from, to ->
                viewModel.convertFramerate(from, to)
                showFramerate = false
            },
        )
    }

    if (showProperties) {
        val info = (state as? EditorUiState.Loaded)?.script?.info
        if (info != null) {
            PropertiesSheet(
                info = info,
                onDismiss = { showProperties = false },
                onApply = { changes ->
                    viewModel.applyScriptInfo(changes)
                    showProperties = false
                },
            )
        }
    }

    if (showStyling) {
        val loaded = state as? EditorUiState.Loaded
        val events = loaded?.script?.events
        if (loaded != null && events != null && events.isNotEmpty()) {
            val currentId = editingId ?: events.first().id
            val pos = events.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
            val ev = events[pos]
            StylingAssistantSheet(
                event = ev,
                position = pos,
                total = events.size,
                styles = loaded.script.styles,
                onAssign = { style ->
                    viewModel.updateEventStyle(ev.id, style)
                    // 应用后自动前进到下一行
                    if (pos + 1 < events.size) editingId = events[pos + 1].id
                },
                onPrev = { if (pos > 0) editingId = events[pos - 1].id },
                onNext = { if (pos + 1 < events.size) editingId = events[pos + 1].id },
                onDismiss = { showStyling = false },
            )
        }
    }

    if (showTranslation) {
        val loaded = state as? EditorUiState.Loaded
        val events = loaded?.script?.events
        if (events != null && events.isNotEmpty()) {
            val currentId = editingId ?: events.first().id
            val pos = events.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
            val ev = events[pos]
            TranslationAssistantSheet(
                event = ev,
                position = pos,
                total = events.size,
                onSave = { original, translation ->
                    viewModel.setTranslation(ev.id, original, translation)
                    if (pos + 1 < events.size) editingId = events[pos + 1].id
                },
                onPrev = { if (pos > 0) editingId = events[pos - 1].id },
                onNext = { if (pos + 1 < events.size) editingId = events[pos + 1].id },
                onDismiss = { showTranslation = false },
            )
        }
    }

    if (showKaraoke) {
        val loaded = state as? EditorUiState.Loaded
        val events = loaded?.script?.events
        val hasSelection = events != null && events.any { it.id == editingId }
        if (loaded != null && hasSelection && editingId != null) {
            KaraokeDialog(
                onDismiss = { showKaraoke = false },
                onApply = { mode, useKf ->
                    viewModel.makeKaraoke(editingId!!, mode, useKf)
                    showKaraoke = false
                },
            )
        }
    }

    if (showTimingPP) {
        TimingPostProcessDialog(
            onDismiss = { showTimingPP = false },
            onApply = { leadIn, leadOut, gap ->
                viewModel.applyTimingPostProcess(leadIn, leadOut, gap)
                showTimingPP = false
            },
        )
    }

    if (showResample) {
        val (fromW, fromH) = viewModel.playRes()
        ResolutionResampleDialog(
            fromW = fromW,
            fromH = fromH,
            onDismiss = { showResample = false },
            onApply = { toW, toH, scalePos, scaleBorders ->
                viewModel.resampleResolution(fromW, fromH, toW, toH, scalePos, scaleBorders)
                showResample = false
            },
        )
    }

    if (showExportFormat) {
        ExportFormatDialog(
            onDismiss = { showExportFormat = false },
            onPick = { fmt ->
                showExportFormat = false
                pendingExportFormat = fmt
                exportLauncher.launch("字幕工程${fmt.extensions.first()}")
            },
        )
    }

    if (showHistory) {
        HistorySheet(
            snapshots = snapshots,
            onDismiss = { showHistory = false },
            onSaveSnapshot = { label ->
                viewModel.takeSnapshot(label)
            },
            onRestore = { id ->
                viewModel.restoreSnapshot(id)
                showHistory = false
            },
            onDelete = { id -> viewModel.deleteSnapshot(id) },
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
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onEnterSelection: (Long) -> Unit = {},
) {
    EventListScreen(
        events = script.events,
        onEventClick = onEventClick,
        onBack = onBack,
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onToggleSelect = onToggleSelect,
        onEnterSelection = onEnterSelection,
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
            onLineAction = { viewModel.applyLineAction(ev.id, it) },
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
    onSort: () -> Unit,
    onFramerate: () -> Unit,
    onProperties: () -> Unit,
    onStyling: () -> Unit,
    onTranslation: () -> Unit,
    onKaraoke: () -> Unit,
    onTimingPP: () -> Unit,
    onResample: () -> Unit,
    onDeleteEmpty: () -> Unit,
    onStyleReplace: () -> Unit,
    onOpenStyleManager: () -> Unit,
    onOpenHistory: () -> Unit,
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
                ToolEntry(Icons.Filled.Search, stringResource(R.string.tool_find_replace), stringResource(R.string.tool_find_replace_desc)) { onFindReplace() }
            }
            item {
                ToolEntry(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.tool_shift_times), stringResource(R.string.tool_shift_times_desc)) { onShiftTimes() }
            }
            item {
                ToolEntry(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.tool_sort), stringResource(R.string.tool_sort_desc)) { onSort() }
            }
            item {
                ToolEntry(Icons.Filled.Movie, stringResource(R.string.tool_framerate), stringResource(R.string.tool_framerate_desc)) { onFramerate() }
            }
            item {
                ToolEntry(Icons.Filled.Settings, stringResource(R.string.tool_properties), stringResource(R.string.tool_properties_desc)) { onProperties() }
            }
            item {
                ToolEntry(Icons.Filled.Delete, stringResource(R.string.tool_delete_empty), stringResource(R.string.tool_delete_empty_desc)) { onDeleteEmpty() }
            }
            item {
                ToolEntry(Icons.Filled.Edit, stringResource(R.string.tool_style_replace), stringResource(R.string.tool_style_replace_desc)) { onStyleReplace() }
            }
            item {
                ToolEntry(Icons.Filled.Build, stringResource(R.string.tool_style_manager), stringResource(R.string.tool_style_manager_desc)) { onOpenStyleManager() }
            }
            item {
                ToolEntry(Icons.Filled.Palette, stringResource(R.string.tool_styling), stringResource(R.string.tool_styling_desc)) { onStyling() }
            }
            item {
                ToolEntry(Icons.Filled.Translate, stringResource(R.string.tool_translation), stringResource(R.string.tool_translation_desc)) { onTranslation() }
            }
            item {
                ToolEntry(Icons.Filled.MusicNote, stringResource(R.string.tool_karaoke), stringResource(R.string.tool_karaoke_desc)) { onKaraoke() }
            }
            item {
                ToolEntry(Icons.Filled.Timer, stringResource(R.string.tool_timing_pp), stringResource(R.string.tool_timing_pp_desc)) { onTimingPP() }
            }
            item {
                ToolEntry(Icons.Filled.AspectRatio, stringResource(R.string.tool_resample), stringResource(R.string.tool_resample_desc)) { onResample() }
            }
            item {
                ToolEntry(Icons.Filled.PlayArrow, stringResource(R.string.tool_history), stringResource(R.string.tool_history_desc)) { onOpenHistory() }
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

/**
 * 排序对话框：选择排序键与方向，应用到全部事件（一次撤销点）。
 *
 * @author 伤感咩吖
 */
@Composable
private fun SortDialog(
    onDismiss: () -> Unit,
    onApply: (SortKey, SortOrder) -> Unit,
) {
    var key by remember { mutableStateOf(SortKey.START) }
    var descending by remember { mutableStateOf(false) }
    val keys = listOf(
        SortKey.START to "起始时间",
        SortKey.END to "结束时间",
        SortKey.STYLE to "样式名",
        SortKey.ACTOR to "演员名",
        SortKey.EFFECT to "效果",
        SortKey.TEXT to "文本",
        SortKey.LAYER to "层",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("排序依据", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    keys.forEach { (k, label) ->
                        FilterChip(selected = key == k, onClick = { key = k }, label = { Text(label) })
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = !descending,
                        onClick = { descending = false },
                        label = { Text("升序") },
                    )
                    FilterChip(
                        selected = descending,
                        onClick = { descending = true },
                        label = { Text("降序") },
                    )
                }
                Text(
                    "相等项保持原序（稳定排序）。可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(key, if (descending) SortOrder.DESCENDING else SortOrder.ASCENDING)
            }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 帧率转换对话框：选源/目标帧率，按比例等比缩放全部时间（一次撤销点）。
 *
 * @author 伤感咩吖
 */
@Composable
private fun FramerateDialog(
    onDismiss: () -> Unit,
    onApply: (fromFps: Double, toFps: Double) -> Unit,
) {
    val presets = FramerateConverter.PRESETS
    var from by remember { mutableStateOf(presets.first().second) } // 23.976
    var to by remember { mutableStateOf(presets[2].second) }       // 25.0
    val ratio = to / from
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("帧率转换") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("源帧率", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (label, value) ->
                        FilterChip(selected = from == value, onClick = { from = value }, label = { Text(label) })
                    }
                }
                Text("目标帧率", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (label, value) ->
                        FilterChip(selected = to == value, onClick = { to = value }, label = { Text(label) })
                    }
                }
                Text(
                    if (ratio >= 1) "整体拉长 %.4f 倍（每 1s → %.3fs）".format(ratio, ratio)
                    else "整体压缩 %.4f 倍（每 1s → %.3fs）".format(ratio, ratio),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(from, to) }, enabled = from > 0 && to > 0) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 脚本属性面板（复刻桌面 Aegisub Properties）：
 * 编辑 [Script Info] 的分辨率 / 换行样式 / 碰撞 / 缩放描边 / 计时速度等。
 * 一次性提交全部改动（单撤销点）。空文本字段不写入（避免插入空键值行）。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertiesSheet(
    info: ImmutableList<AssInfo>,
    onDismiss: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(ScriptInfoOps.get(info, "Title") ?: "") }
    var resX by remember { mutableStateOf(ScriptInfoOps.get(info, "PlayResX") ?: "") }
    var resY by remember { mutableStateOf(ScriptInfoOps.get(info, "PlayResY") ?: "") }
    var wrap by remember { mutableStateOf(ScriptInfoOps.get(info, "WrapStyle") ?: "0") }
    var sbs by remember { mutableStateOf(ScriptInfoOps.get(info, "ScaledBorderAndShadow") ?: "yes") }
    var collisions by remember { mutableStateOf(ScriptInfoOps.get(info, "Collisions") ?: "Normal") }
    var timer by remember { mutableStateOf(ScriptInfoOps.get(info, "Timer") ?: "100") }
    val wrapOptions = listOf(
        "0" to "智能换行（顶宽）",
        "1" to "行尾换行",
        "2" to "不换行",
        "3" to "智能换行（底宽）",
    )
    // 作者与元信息字段（key → 标签），桌面 Aegisub Properties 同名键
    val authorFields = listOf(
        "Script" to "原始脚本（Script）",
        "Translation" to "翻译（Translation）",
        "Editing" to "编辑（Editing）",
        "Timing" to "打轴（Timing）",
        "Synch Point" to "同步点（Synch Point）",
        "Updated By" to "更新者（Updated By）",
        "YCbCr Matrix" to "YCbCr Matrix",
    )
    val authorValues = remember(info) {
        authorFields.map { (k, _) -> mutableStateOf(ScriptInfoOps.get(info, k) ?: "") }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("脚本属性", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题（Title）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = resX,
                    onValueChange = { resX = it.filter { ch -> ch.isDigit() } },
                    label = { Text("PlayResX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = resY,
                    onValueChange = { resY = it.filter { ch -> ch.isDigit() } },
                    label = { Text("PlayResY") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text("换行样式（WrapStyle）", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                wrapOptions.forEach { (v, label) ->
                    FilterChip(selected = wrap == v, onClick = { wrap = v }, label = { Text(label) })
                }
            }
            Text("缩放描边阴影（ScaledBorderAndShadow）", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sbs == "yes", onClick = { sbs = "yes" }, label = { Text("是（yes）") })
                FilterChip(selected = sbs == "no", onClick = { sbs = "no" }, label = { Text("否（no）") })
            }
            Text("碰撞（Collisions）", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = collisions == "Normal", onClick = { collisions = "Normal" }, label = { Text("Normal（向上堆叠）") })
                FilterChip(selected = collisions == "Reverse", onClick = { collisions = "Reverse" }, label = { Text("Reverse（向下堆叠）") })
            }
            OutlinedTextField(
                value = timer,
                onValueChange = { timer = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("计时速度（Timer，100 = 正常）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            Text("作者与元信息", style = MaterialTheme.typography.labelLarge)
            authorFields.forEachIndexed { i, (_, label) ->
                OutlinedTextField(
                    value = authorValues[i].value,
                    onValueChange = { authorValues[i].value = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = {
                    val changes = buildMap {
                        if (title.isNotBlank()) put("Title", title)
                        if (resX.isNotBlank()) put("PlayResX", resX)
                        if (resY.isNotBlank()) put("PlayResY", resY)
                        put("WrapStyle", wrap)
                        put("ScaledBorderAndShadow", sbs)
                        put("Collisions", collisions)
                        if (timer.isNotBlank()) put("Timer", timer)
                        authorFields.forEachIndexed { i, (key, _) ->
                            val v = authorValues[i].value
                            if (v.isNotBlank()) put(key, v)
                        }
                    }
                    onApply(changes)
                }) { Text("应用") }
            }
        }
    }
}

/**
 * 导出格式选择器：ASS / SRT / WebVTT。选定后触发 SAF CreateDocument。
 *
 * @author 伤感咩吖
 */
@Composable
private fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onPick: (SubtitleFormat) -> Unit,
) {
    val formats = listOf(
        Triple(AssFormat, "ASS", "完整样式/标签，ASS 厘秒或毫秒精度"),
        Triple(SrtFormat, "SRT", "剥离标签的纯文本字幕"),
        Triple(VttFormat, "WebVTT (VTT)", "剥离标签，Web 视频 HTML5 字幕"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出格式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                formats.forEach { (fmt, label, desc) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.clickable { onPick(fmt) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 分辨率重采样对话框：源分辨率预填当前 PlayResX/Y，输入目标分辨率 + 缩放选项。
 *
 * @author 伤感咩吖
 */
@Composable
private fun ResolutionResampleDialog(
    fromW: Int,
    fromH: Int,
    onDismiss: () -> Unit,
    onApply: (toW: Int, toH: Int, scalePositions: Boolean, scaleBorders: Boolean) -> Unit,
) {
    val presets = listOf(
        "384×288" to (384 to 288),
        "640×480" to (640 to 480),
        "1280×720" to (1280 to 720),
        "1920×1080" to (1920 to 1080),
        "3840×2160" to (3840 to 2160),
    )
    var toW by remember { mutableStateOf((fromW * 2).toString()) }
    var toH by remember { mutableStateOf((fromH * 2).toString()) }
    var scalePos by remember { mutableStateOf(true) }
    var scaleBorders by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分辨率重采样") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("源分辨率：$fromW × $fromH", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = toW,
                        onValueChange = { toW = it.filter { ch -> ch.isDigit() } },
                        label = { Text("目标宽") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = toH,
                        onValueChange = { toH = it.filter { ch -> ch.isDigit() } },
                        label = { Text("目标高") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (label, res) ->
                        FilterChip(
                            selected = toW == res.first.toString() && toH == res.second.toString(),
                            onClick = { toW = res.first.toString(); toH = res.second.toString() },
                            label = { Text(label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = scalePos, onCheckedChange = { scalePos = it })
                    Text("缩放 \\pos/\\move")
                    Checkbox(checked = scaleBorders, onCheckedChange = { scaleBorders = it })
                    Text("缩放描边/阴影")
                }
                Text("字号与边距始终按比例缩放；PlayResX/Y 更新为目标值。可撤销。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            val w = toW.toIntOrNull() ?: 0
            val h = toH.toIntOrNull() ?: 0
            TextButton(onClick = { onApply(w, h, scalePos, scaleBorders) }, enabled = w > 0 && h > 0) {
                Text("应用")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 时间后处理对话框：lead-in/out 毫秒 + 最小间隙毫秒，应用到全部事件（单撤销点）。
 *
 * @author 伤感咩吖
 */
@Composable
private fun TimingPostProcessDialog(
    onDismiss: () -> Unit,
    onApply: (leadInMs: Long, leadOutMs: Long, gapMs: Long) -> Unit,
) {
    var leadIn by remember { mutableStateOf("100") }
    var leadOut by remember { mutableStateOf("100") }
    var gap by remember { mutableStateOf("200") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时间后处理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = leadIn,
                    onValueChange = { leadIn = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Lead-in 起始提前（ms）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = leadOut,
                    onValueChange = { leadOut = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Lead-out 结束延后（ms）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gap,
                    onValueChange = { gap = it.filter { ch -> ch.isDigit() } },
                    label = { Text("最小间隙（ms，0=不去重叠）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "按字幕顺序：先提前起始/延后结束，再强制相邻行最小间隙（去重叠）。可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(leadIn.toLongOrNull() ?: 0L, leadOut.toLongOrNull() ?: 0L, gap.toLongOrNull() ?: 0L)
            }) { Text("应用") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 卡拉OK生成对话框：选切分模式（按词/按字）+ 填充类型（{\k}/{\kf}），
 * 应用到当前选中行（单撤销点）。
 *
 * @author 伤感咩吖
 */
@Composable
private fun KaraokeDialog(
    onDismiss: () -> Unit,
    onApply: (KaraokeMode, useKf: Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(KaraokeMode.BY_WORD) }
    var useKf by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("卡拉OK生成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("切分模式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == KaraokeMode.BY_WORD,
                        onClick = { mode = KaraokeMode.BY_WORD },
                        label = { Text("按词（空格）") },
                    )
                    FilterChip(
                        selected = mode == KaraokeMode.BY_CHAR,
                        onClick = { mode = KaraokeMode.BY_CHAR },
                        label = { Text("按字（每字符）") },
                    )
                }
                Text("填充类型", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !useKf, onClick = { useKf = false }, label = { Text("{\\k} 逐字填充") })
                    FilterChip(selected = useKf, onClick = { useKf = true }, label = { Text("{\\kf} 平滑填充") })
                }
                Text(
                    "把选中行文本切成音节，时长均匀分配（余数前置），生成 karaoke 标签。可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(mode, useKf) }) { Text("应用到选中行") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 历史版本弹层：保存当前为快照 + 列出过往快照（恢复/删除）。
 * 恢复会把快照内容作为新撤销点载入，可撤销回恢复前。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    snapshots: List<io.github.samgum.aegisub.data.repository.Snapshot>,
    onDismiss: () -> Unit,
    onSaveSnapshot: (label: String) -> Unit,
    onRestore: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("手动快照") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("历史版本", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("快照备注") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSaveSnapshot(label.ifBlank { "手动快照" }) }) { Text("保存当前") }
            }
            HorizontalDivider()
            if (snapshots.isEmpty()) {
                Text("暂无快照。保存当前脚本为快照后，可随时恢复到该版本。", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(snapshots.size) { i ->
                        val s = snapshots[i]
                        ListItem(
                            headlineContent = { Text(s.label.ifBlank { "（无备注）" }) },
                            supportingContent = { Text(formatTimestamp(s.createdAt), style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                Row {
                                    TextButton(onClick = { onRestore(s.id) }) { Text("恢复") }
                                    TextButton(onClick = { onDelete(s.id) }) { Text("删除") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** epoch 毫秒 → "yyyy-MM-dd HH:mm"。 */
private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "—"
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%04d-%02d-%02d %02d:%02d".format(
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH) + 1,
        cal.get(java.util.Calendar.DAY_OF_MONTH),
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
    )
}
