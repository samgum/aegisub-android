package io.github.samgum.aegisub.ui.workspace.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.data.repository.Bookmark
import io.github.samgum.aegisub.data.repository.Snapshot
import io.github.samgum.aegisub.data.settings.UserSettings
import io.github.samgum.aegisub.domain.edit.FramerateConverter
import io.github.samgum.aegisub.domain.edit.KaraokeMode
import io.github.samgum.aegisub.domain.edit.SelectLines
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.editor.components.StylingAssistantSheet
import io.github.samgum.aegisub.feature.editor.components.TranslationAssistantSheet
import io.github.samgum.aegisub.feature.preview.PlaybackState
import io.github.samgum.aegisub.ui.workspace.WorkspaceViewModel
import io.github.samgum.aegisub.ui.workspace.components.WorkspaceDialogType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一工作台全量对话框调度中心（34 类桌面 Aegisub 标准功能 + SGMY 扩展弹窗一键路由）。
 *
 * @author 伤感咩吖
 */
@Composable
fun WorkspaceDialogHost(
    dialogType: WorkspaceDialogType?,
    script: AssScript,
    activeEvent: AssEvent?,
    selectedIds: Set<Long>,
    hasMedia: Boolean,
    playback: PlaybackState,
    keyframesMs: List<Long>,
    timecodesMs: List<Long>,
    settings: UserSettings,
    snapshots: List<Snapshot>,
    bookmarks: List<Bookmark>,
    viewModel: WorkspaceViewModel,
    onSwitchDialog: (WorkspaceDialogType?) -> Unit,
    onImportKeyframes: () -> Unit,
    onExportKeyframes: () -> Unit,
    onImportTimecodes: () -> Unit,
    onExportTimecodes: () -> Unit,
    onExportConfigured: (ExportDialogSelection) -> Unit,
) {
    if (dialogType == null) return
    val dismiss = { onSwitchDialog(null) }

    when (dialogType) {
        // -------- SGMY 9 大扩展工具 --------
        WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS -> SgmyFixCommonErrorsDialog(
            selectedIds = selectedIds,
            onApply = viewModel::applyFixCommonErrors,
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SGMY_LYRIC_SCROLL -> SgmyLyricScrollDialog(
            selectedIds = selectedIds,
            onGenerate = viewModel::generateLyricScroll,
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SGMY_CHINESE_CONVERT,
        WorkspaceDialogType.SGMY_TEXT_CLEANUP,
        WorkspaceDialogType.SGMY_PAIR_CHECK,
        WorkspaceDialogType.SGMY_FURIGANA,
        -> SgmyCjkToolsDialog(
            initialTab = dialogType,
            script = script,
            selectedIds = selectedIds,
            onSwitchDialog = { onSwitchDialog(it) },
            onChineseConvert = viewModel::applyChineseConversion,
            onTextCleanup = viewModel::applyTextCleanup,
            onCheckPairs = viewModel::checkPairedPunctuation,
            onApplyFurigana = viewModel::applyFurigana,
            onJumpToEvent = { id -> viewModel.selectEvent(id) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SGMY_STITCH_TIMINGS,
        WorkspaceDialogType.SGMY_STYLE_OVERLAP,
        -> SgmyTimingToolsDialog(
            initialTab = dialogType,
            selectedIds = selectedIds,
            onSwitchDialog = { onSwitchDialog(it) },
            onApplyStitch = viewModel::applyStitchTimings,
            onAnalyzeOverlaps = viewModel::analyzeStyleOverlaps,
            onJumpToEvent = { id -> viewModel.selectEvent(id) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SGMY_AI_ANALYSIS -> SgmyAiAnalysisSheet(
            activeEvent = activeEvent,
            allEvents = script.events,
            settings = settings,
            viewModel = viewModel,
            onDismiss = dismiss,
        )

        // -------- 桌面 Aegisub 25 类完整专业对话框 --------
        WorkspaceDialogType.EXPORT -> ExportPipelineDialog(
            onConfirm = { cfg ->
                dismiss()
                onExportConfigured(cfg)
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.PROPERTIES -> ScriptPropertiesDialog(
            script = script,
            onApply = { changes ->
                viewModel.applyScriptInfo(changes)
                dismiss()
            },
            onOpenResample = { onSwitchDialog(WorkspaceDialogType.RESAMPLE_RESOLUTION) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.FONTS_COLLECTOR -> FontsCollectorDialog(
            usages = remember(script) { viewModel.analyzeFonts() },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.ATTACHMENTS -> AttachmentsDialog(
            attachments = script.attachments,
            onAddAttachment = viewModel::addAttachment,
            onRemoveAttachment = viewModel::removeAttachment,
            onDismiss = dismiss,
        )

        WorkspaceDialogType.TEXT_IMPORT_OPTIONS -> TextImportOptionsDialog(
            onApply = { opts ->
                viewModel.applyTextImportActorParse(opts)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.HISTORY_SNAPSHOTS -> HistorySnapshotsDialog(
            snapshots = snapshots,
            onSaveNew = { label -> viewModel.takeSnapshot(label) },
            onRestore = { id -> viewModel.restoreSnapshot(id); dismiss() },
            onDelete = { id -> viewModel.deleteSnapshot(id) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.BOOKMARKS -> BookmarksDialog(
            bookmarks = bookmarks,
            onAddAtCurrentPos = { label -> viewModel.addBookmark(label) },
            onJump = { ms -> viewModel.seekTo(ms); dismiss() },
            onDelete = { id -> viewModel.deleteBookmark(id) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.FIND_REPLACE -> FullFindReplaceDialog(
            events = script.events,
            selectedIds = selectedIds,
            onFindNext = { q, fld, reg, ic -> viewModel.findNextMatch(q, fld, reg, ic) },
            onReplaceNext = { q, r, fld, reg, ic -> viewModel.replaceCurrentAndFindNext(q, r, fld, reg, ic) },
            onReplaceAll = { q, r, fld, reg, ic, selOnly ->
                viewModel.replaceAll(q, r, reg, ic, selOnly, fld)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.PASTE_OVER -> FullPasteOverDialog(
            targetCount = if (selectedIds.isNotEmpty()) selectedIds.size else script.events.size,
            onApply = { raw, fields ->
                viewModel.pasteOver(raw, fields)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SELECT_LINES -> SelectLinesDialog(
            onApply = { criteria ->
                viewModel.applySelectLinesCriteria(criteria)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.STYLE_REPLACE -> StyleReplaceDialog(
            styleNames = script.styles.map { it.name },
            hasSelection = selectedIds.isNotEmpty(),
            onApply = { from, to, selOnly ->
                viewModel.replaceStyles(from, to, selOnly)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SORT_LINES -> FullSortLinesDialog(
            selectedCount = selectedIds.size,
            onSort = { key, order, selOnly ->
                viewModel.sortLines(key, order, selOnly)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SPELL_CHECKER -> SpellCheckerDialog(
            events = script.events,
            onJumpToEvent = { id -> viewModel.selectEvent(id) },
            onFixEventText = { id, newText -> viewModel.updateEventText(id, newText) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.SHIFT_TIMES -> ShiftTimesDialog(
            hasSelection = selectedIds.isNotEmpty(),
            onApply = { delta, target, afterActive, selOnly ->
                viewModel.shiftTimes(delta, target, afterActive, selOnly)
                dismiss()
            },
            onOpenPostProcess = { onSwitchDialog(WorkspaceDialogType.TIMING_POST_PROCESS) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.TIMING_POST_PROCESS -> FullTimingProcessorDialog(
            styleNames = script.styles.map { it.name },
            selectedIds = selectedIds,
            keyframeCount = keyframesMs.size,
            defaultLeadIn = settings.defaultLeadInMs,
            defaultLeadOut = settings.defaultLeadOutMs,
            onApply = { cfg ->
                viewModel.applyFullTimingProcessor(cfg)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.KANJI_TIMER -> KanjiTimerDialog(
            styleNames = script.styles.map { it.name },
            onApply = { src, dst ->
                viewModel.applyKanjiTimer(src, dst)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.FRAMERATE_CONVERT -> FramerateConvertDialog(
            onApply = { fromFps, toFps ->
                viewModel.convertFramerate(fromFps, toFps)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.RESAMPLE_RESOLUTION -> ResampleResolutionDialog(
            currentW = script.getScriptInfo("PlayResX") ?: "1920",
            currentH = script.getScriptInfo("PlayResY") ?: "1080",
            onApply = { w, h, pos, bord ->
                viewModel.resampleResolution(w, h, pos, bord)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.DUMMY_VIDEO -> DummyVideoDialog(
            playback = playback,
            onApply = { w, h, fps, durMs, colorArgb, checker ->
                viewModel.openDummyVideo(w, h, fps, durMs, colorArgb, checker)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.JUMP_TO -> JumpToDialog(
            playback = playback,
            timecodesMs = timecodesMs,
            onSeek = viewModel::seekTo,
            onDismiss = dismiss,
        )

        WorkspaceDialogType.VIDEO_DETAILS -> VideoDetailsDialog(
            script = script,
            hasMedia = hasMedia,
            playback = playback,
            keyframeCount = keyframesMs.size,
            timecodeCount = timecodesMs.size,
            onImportKeyframes = onImportKeyframes,
            onExportKeyframes = onExportKeyframes,
            onImportTimecodes = onImportTimecodes,
            onExportTimecodes = onExportTimecodes,
            onOpenDummyVideo = { onSwitchDialog(WorkspaceDialogType.DUMMY_VIDEO) },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.AUTOMATION_MANAGER -> AutomationManagerDialog(
            onRunMacro = viewModel::runAutomationMacro,
            onInsertTemplateStarter = viewModel::insertKaraokeTemplateStarter,
            onDismiss = dismiss,
        )

        WorkspaceDialogType.KARAOKE_GENERATOR -> KaraokeGeneratorDialog(
            onApply = { mode, useKf ->
                viewModel.makeKaraokeOnActive(mode, useKf)
                dismiss()
            },
            onDismiss = dismiss,
        )

        WorkspaceDialogType.STYLING_ASSISTANT -> {
            val ev = activeEvent ?: script.events.firstOrNull()
            if (ev == null) dismiss() else {
                val pos = script.events.indexOfFirst { it.id == ev.id }.coerceAtLeast(0)
                StylingAssistantSheet(
                    event = ev,
                    position = pos,
                    total = script.events.size,
                    styles = script.styles,
                    onAssign = { styleName ->
                        viewModel.updateEventStyle(ev.id, styleName)
                        viewModel.selectNextEvent()
                    },
                    onPrev = viewModel::selectPrevEvent,
                    onNext = viewModel::selectNextEvent,
                    onDismiss = dismiss,
                )
            }
        }

        WorkspaceDialogType.TRANSLATION_ASSISTANT -> {
            val ev = activeEvent ?: script.events.firstOrNull()
            if (ev == null) dismiss() else {
                val pos = script.events.indexOfFirst { it.id == ev.id }.coerceAtLeast(0)
                TranslationAssistantSheet(
                    event = ev,
                    position = pos,
                    total = script.events.size,
                    onSave = { orig, trans ->
                        viewModel.setTranslation(ev.id, orig, trans)
                        viewModel.selectNextEvent()
                    },
                    onPrev = viewModel::selectPrevEvent,
                    onNext = viewModel::selectNextEvent,
                    onDismiss = dismiss,
                )
            }
        }
    }
}

@Composable
private fun ScriptPropertiesDialog(
    script: AssScript,
    onApply: (Map<String, String>) -> Unit,
    onOpenResample: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(script.getScriptInfo("Title") ?: "") }
    var resX by remember { mutableStateOf(script.getScriptInfo("PlayResX") ?: "1920") }
    var resY by remember { mutableStateOf(script.getScriptInfo("PlayResY") ?: "1080") }
    var wrapStyle by remember { mutableStateOf(script.getScriptInfo("WrapStyle") ?: "0") }
    var scaledBorder by remember { mutableStateOf((script.getScriptInfo("ScaledBorderAndShadow") ?: "yes").equals("yes", true)) }
    var ycbcr by remember { mutableStateOf(script.getScriptInfo("YCbCr Matrix") ?: "TV.709") }
    var author by remember { mutableStateOf(script.getScriptInfo("Original Script") ?: "") }
    var translator by remember { mutableStateOf(script.getScriptInfo("Original Translation") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("脚本属性 [Script Info]") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题 Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = resX, onValueChange = { resX = it }, label = { Text("PlayResX") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = resY, onValueChange = { resY = it }, label = { Text("PlayResY") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("1920" to "1080", "3840" to "2160", "1280" to "720", "1080" to "1920").forEach { (w, h) ->
                        FilterChip(selected = resX == w && resY == h, onClick = { resX = w; resY = h }, label = { Text("${w}×$h") })
                    }
                }
                OutlinedTextField(value = wrapStyle, onValueChange = { wrapStyle = it }, label = { Text("换行模式 WrapStyle (0智能 / 1行尾 / 2不换行 / 3下宽)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ycbcr, onValueChange = { ycbcr = it }, label = { Text("色彩矩阵 YCbCr Matrix (TV.709 / TV.2020 / PC.709)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("脚本作者 Original Script") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = translator, onValueChange = { translator = it }, label = { Text("译者 Original Translation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SgmyCheckRow("按分辨率缩放描边与阴影 (ScaledBorderAndShadow: yes)", scaledBorder) { scaledBorder = it }
                TextButton(onClick = onOpenResample) { Text("打开「分辨率重采样器」同步缩放坐标与字号 →") }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(
                    mapOf(
                        "Title" to title,
                        "PlayResX" to (resX.toIntOrNull()?.coerceAtLeast(1) ?: 1920).toString(),
                        "PlayResY" to (resY.toIntOrNull()?.coerceAtLeast(1) ?: 1080).toString(),
                        "WrapStyle" to wrapStyle.trim().ifEmpty { "0" },
                        "ScaledBorderAndShadow" to if (scaledBorder) "yes" else "no",
                        "YCbCr Matrix" to ycbcr.trim().ifEmpty { "TV.709" },
                        "Original Script" to author,
                        "Original Translation" to translator,
                    ),
                )
            }) { Text("保存属性") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FontsCollectorDialog(
    usages: List<io.github.samgum.aegisub.domain.edit.FontsCollectorAnalyzer.FontUsage>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("字体检查与清单统计 (${usages.size} 种字体)") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(usages, key = { it.fontName }) { u ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(u.fontName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "对白使用 ${u.activeDialogueCount} 行 · 行内 \\fn 覆盖 ${u.inlineOverrideCount} 次",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        if (u.usedInStyles.isNotEmpty()) {
                            Text("引用样式：${u.usedInStyles.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (u.sampleRows.isNotEmpty()) {
                            Text("示例行号：#${u.sampleRows.joinToString(", #")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        HorizontalDivider(Modifier.padding(top = 4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun HistorySnapshotsDialog(
    snapshots: List<Snapshot>,
    onSaveNew: (String) -> Unit,
    onRestore: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("手动快照 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("工程历史版本快照") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("快照备注") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { onSaveNew(label.ifBlank { "快照" }) }) { Text("新建") }
                }
                if (snapshots.isEmpty()) {
                    Text("暂无手动快照。保存快照后可随时无损回滚。", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(snapshots, key = { it.id }) { snap ->
                            val ts = remember(snap.createdAt) { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(snap.createdAt)) }
                            ListItem(
                                headlineContent = { Text(snap.label) },
                                supportingContent = { Text(ts) },
                                trailingContent = {
                                    Row {
                                        TextButton(onClick = { onRestore(snap.id) }) { Text("恢复") }
                                        IconButton(onClick = { onDelete(snap.id) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun BookmarksDialog(
    bookmarks: List<Bookmark>,
    onAddAtCurrentPos: (String) -> Unit,
    onJump: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("关键节点") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时间轴书签管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("书签说明") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(onClick = { onAddAtCurrentPos(label.ifBlank { "书签" }) }) { Text("添加") }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    items(bookmarks, key = { it.id }) { bm ->
                        ListItem(
                            headlineContent = { Text(bm.label) },
                            supportingContent = { Text(SubTime.ofMillis(bm.timeMs).toAssString(false), fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.clickable { onJump(bm.timeMs) },
                            trailingContent = {
                                IconButton(onClick = { onDelete(bm.id) }) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SelectLinesDialog(
    onApply: (SelectLines.Criteria) -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by remember { mutableStateOf("") }
    var field by remember { mutableStateOf(SelectLines.Field.TEXT) }
    var mode by remember { mutableStateOf(SelectLines.MatchMode.CONTAINS) }
    var action by remember { mutableStateOf(SelectLines.Action.SET) }
    var invert by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("按条件批量选行 (Select Lines)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("匹配关键字或正则") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    SelectLines.Field.entries.forEach { f ->
                        FilterChip(selected = field == f, onClick = { field = f }, label = { Text(f.name) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectLines.MatchMode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.name) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    SelectLines.Action.entries.forEach { a ->
                        FilterChip(selected = action == a, onClick = { action = a }, label = { Text(a.name) })
                    }
                }
                SgmyCheckRow("反向匹配（选择不包含该条件的行）", invert) { invert = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(SelectLines.Criteria(pattern = pattern, field = field, matchMode = mode, invertMatch = invert, action = action))
            }) { Text("执行选行") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun StyleReplaceDialog(
    styleNames: List<String>,
    hasSelection: Boolean,
    onApply: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by remember { mutableStateOf(styleNames.firstOrNull() ?: "Default") }
    var to by remember { mutableStateOf(styleNames.lastOrNull() ?: "Default") }
    var selOnly by remember { mutableStateOf(hasSelection) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量替换样式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = from, onValueChange = { from = it }, label = { Text("原样式名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("目标样式名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (hasSelection) SgmyCheckRow("仅替换选中行", selOnly) { selOnly = it }
            }
        },
        confirmButton = { Button(onClick = { onApply(from, to, selOnly) }) { Text("替换") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ShiftTimesDialog(
    hasSelection: Boolean,
    onApply: (Long, ShiftTarget, Boolean, Boolean) -> Unit,
    onOpenPostProcess: () -> Unit,
    onDismiss: () -> Unit,
) {
    var msText by remember { mutableStateOf("500") }
    var backward by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf(ShiftTarget.BOTH) }
    var afterActive by remember { mutableStateOf(false) }
    var selOnly by remember { mutableStateOf(hasSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("平移时间轴 (Shift Times)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = msText,
                    onValueChange = { msText = it },
                    label = { Text("偏移毫秒数 (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !backward, onClick = { backward = false }, label = { Text("向后推迟 (+)") })
                    FilterChip(selected = backward, onClick = { backward = true }, label = { Text("向前提前 (-)") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = target == ShiftTarget.BOTH, onClick = { target = ShiftTarget.BOTH }, label = { Text("起止同时") })
                    FilterChip(selected = target == ShiftTarget.START, onClick = { target = ShiftTarget.START }, label = { Text("仅起点") })
                    FilterChip(selected = target == ShiftTarget.END, onClick = { target = ShiftTarget.END }, label = { Text("仅终点") })
                }
                SgmyCheckRow("仅平移当前行及之后的字幕", afterActive) { afterActive = it }
                if (hasSelection) SgmyCheckRow("仅平移多选行", selOnly) { selOnly = it }
                TextButton(onClick = onOpenPostProcess) { Text("打开「时间后处理器（前后导/去重叠）」→") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val absMs = msText.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                onApply(if (backward) -absMs else absMs, target, afterActive, selOnly)
            }) { Text("平移时间") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun KanjiTimerDialog(
    styleNames: List<String>,
    onApply: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var src by remember { mutableStateOf(styleNames.firstOrNull() ?: "Default") }
    var dst by remember { mutableStateOf(styleNames.lastOrNull() ?: "Default") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("卡拉OK汉字计时复制器 (Kanji Timer)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("将源样式（如罗马音）已打好的 \\k / \\kf 音节时长自动按比例投射到目标样式（如汉字/译文）对应行：", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = src, onValueChange = { src = it }, label = { Text("源卡拉OK样式") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dst, onValueChange = { dst = it }, label = { Text("目标汉字样式") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onApply(src, dst) }) { Text("复制计时") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FramerateConvertDialog(
    onApply: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromText by remember { mutableStateOf("23.976") }
    var toText by remember { mutableStateOf("25.0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("帧率时间轴转换") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = fromText, onValueChange = { fromText = it }, label = { Text("源帧率 FPS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = toText, onValueChange = { toText = it }, label = { Text("目标帧率 FPS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FramerateConverter.PRESETS.forEach { (lbl, v) ->
                        FilterChip(selected = toText == lbl, onClick = { toText = v.toString() }, label = { Text(lbl) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val f = fromText.toDoubleOrNull()?.takeIf { it > 0 } ?: 23.976
                val t = toText.toDoubleOrNull()?.takeIf { it > 0 } ?: 25.0
                onApply(f, t)
            }) { Text("转换") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ResampleResolutionDialog(
    currentW: String,
    currentH: String,
    onApply: (Int, Int, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var toW by remember { mutableStateOf("1920") }
    var toH by remember { mutableStateOf("1080") }
    var scalePos by remember { mutableStateOf(true) }
    var scaleBord by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分辨率重采样 (当前 ${currentW}×$currentH)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = toW, onValueChange = { toW = it }, label = { Text("目标宽度") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = toH, onValueChange = { toH = it }, label = { Text("目标高度") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("1920" to "1080", "3840" to "2160", "1280" to "720", "1080" to "1920").forEach { (w, h) ->
                        FilterChip(selected = toW == w && toH == h, onClick = { toW = w; toH = h }, label = { Text("${w}×$h") })
                    }
                }
                SgmyCheckRow("等比缩放 \\pos / \\move 坐标", scalePos) { scalePos = it }
                SgmyCheckRow("等比缩放描边与阴影宽度", scaleBord) { scaleBord = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply((toW.toIntOrNull() ?: 1920).coerceAtLeast(16), (toH.toIntOrNull() ?: 1080).coerceAtLeast(16), scalePos, scaleBord)
            }) { Text("执行重采样") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun KaraokeGeneratorDialog(
    onApply: (KaraokeMode, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(KaraokeMode.BY_CHAR) }
    var useKf by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生成卡拉OK音节标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == KaraokeMode.BY_CHAR, onClick = { mode = KaraokeMode.BY_CHAR }, label = { Text("逐字切分（中日韩推荐）") })
                    FilterChip(selected = mode == KaraokeMode.BY_WORD, onClick = { mode = KaraokeMode.BY_WORD }, label = { Text("按空格单词切分") })
                }
                SgmyCheckRow("使用平滑扫字 \\kf（取消勾选则用硬切 \\k）", useKf) { useKf = it }
            }
        },
        confirmButton = { Button(onClick = { onApply(mode, useKf) }) { Text("生成音节") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
