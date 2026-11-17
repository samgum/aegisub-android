package io.github.samgum.aegisub.ui.workspace.dialogs

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.automation.AutomationEngine
import io.github.samgum.aegisub.domain.edit.ExportPipeline
import io.github.samgum.aegisub.domain.edit.FindReplaceStepper
import io.github.samgum.aegisub.domain.edit.FullTimingProcessor
import io.github.samgum.aegisub.domain.edit.PasteOverFields
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.edit.TextImportProcessor
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.format.SubtitleFormat
import io.github.samgum.aegisub.domain.model.AssAttachment
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AttachmentGroup
import io.github.samgum.aegisub.domain.text.FindReplace
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.preview.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 桌面 Aegisub 全功能对话框集合：
 * 导出滤镜与编码、11 字段粘贴覆盖、步进查找替换、原位排序、四合一时间后处理器、
 * Automation 4 管理器、ASS 内嵌附件管理、虚拟视频画布、帧/时间跳转、视频详情、纯文本分角解析与错字检查。
 *
 * @author 伤感咩吖
 */

data class ExportDialogSelection(
    val format: SubtitleFormat,
    val filters: ExportPipeline.FilterOptions,
    val charset: ExportPipeline.ExportCharset,
)

@Composable
internal fun ExportPipelineDialog(
    onConfirm: (ExportDialogSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val formats = FormatRegistry.formats
    var selectedFormat by remember { mutableStateOf(formats.first()) }
    var charset by remember { mutableStateOf(ExportPipeline.ExportCharset.UTF8) }
    var applyKaraoke by remember { mutableStateOf(false) }
    var cleanTags by remember { mutableStateOf(false) }
    var stripTags by remember { mutableStateOf(false) }
    var transformFps by remember { mutableStateOf(false) }
    var fromFps by remember { mutableStateOf("23.976") }
    var toFps by remember { mutableStateOf("25.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出字幕与滤镜") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("目标格式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    formats.forEach { fmt ->
                        val ext = fmt.extensions.firstOrNull() ?: ".ass"
                        val desc = when (fmt.name.lowercase()) {
                            "ass" -> "Advanced SubStation Alpha（样式、排版、内嵌附件）"
                            "ssa" -> "SubStation Alpha v4.00"
                            "srt" -> "SubRip 通用视频字幕"
                            "vtt" -> "WebVTT 网页字幕"
                            "ttml" -> "TTML / DFXP 时序文本"
                            "lrc" -> "LRC 逐字/逐行歌词"
                            "sub" -> "MicroDVD 帧字幕"
                            "ttxt" -> "MPEG-4 Timed Text (.ttxt)"
                            "transtation" -> "TranStation 时间码字幕"
                            "encore" -> "Adobe Encore 字幕稿"
                            else -> "纯文本台词"
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { selectedFormat = fmt }
                                .padding(vertical = 3.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedFormat.name == fmt.name, onClick = { selectedFormat = fmt })
                            Column(Modifier.padding(start = 4.dp)) {
                                Text("${fmt.name.uppercase()} ($ext)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("导出转换滤镜", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                SgmyCheckRow("应用卡拉OK模板 (Karaoke Templater)", applyKaraoke) { applyKaraoke = it }
                SgmyCheckRow("清理整理冗余标签 (Clean Tags)", cleanTags) { cleanTags = it }
                SgmyCheckRow("剥离全部标签仅输出纯文本 (Strip Tags)", stripTags) { stripTags = it }
                SgmyCheckRow("转换输出帧率 (Transform Framerate)", transformFps) { transformFps = it }
                if (transformFps) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromFps,
                            onValueChange = { fromFps = it },
                            label = { Text("源 FPS") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = toFps,
                            onValueChange = { toFps = it },
                            label = { Text("目标 FPS") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                HorizontalDivider()
                Text("文本字符编码", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ExportPipeline.ExportCharset.entries.forEach { cs ->
                        FilterChip(
                            selected = charset == cs,
                            onClick = { charset = cs },
                            label = { Text(cs.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    ExportDialogSelection(
                        format = selectedFormat,
                        filters = ExportPipeline.FilterOptions(
                            applyKaraokeTemplates = applyKaraoke,
                            cleanTags = cleanTags,
                            stripAllTags = stripTags,
                            transformFramerate = transformFps,
                            fromFps = fromFps.toDoubleOrNull()?.takeIf { it > 0 } ?: 23.976,
                            toFps = toFps.toDoubleOrNull()?.takeIf { it > 0 } ?: 25.0,
                        ),
                        charset = charset,
                    ),
                )
            }) {
                Text("导出文件…")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun FullPasteOverDialog(
    targetCount: Int,
    onApply: (String, Set<PasteOverFields.Field>) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var raw by remember { mutableStateOf("") }
    var selectedFields by remember { mutableStateOf(PasteOverFields.PRESET_TEXT_ONLY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择字段粘贴覆盖") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("目标范围：$targetCount 行", style = MaterialTheme.typography.labelMedium)
                    FilledTonalButton(
                        onClick = { raw = clipboard.getText()?.text.orEmpty() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("读取剪贴板", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = raw,
                    onValueChange = { text ->
                        raw = text
                        if (text.lineSequence().any { it.trimStart().startsWith("Dialogue:", true) } &&
                            selectedFields == PasteOverFields.PRESET_TEXT_ONLY
                        ) {
                            selectedFields = PasteOverFields.PRESET_TEXT_ONLY
                        }
                    },
                    placeholder = { Text("粘贴每行一条纯文本，或完整的 ASS Dialogue 行…") },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedFields == PasteOverFields.PRESET_TEXT_ONLY,
                        onClick = { selectedFields = PasteOverFields.PRESET_TEXT_ONLY },
                        label = { Text("仅文本") },
                    )
                    FilterChip(
                        selected = selectedFields == PasteOverFields.PRESET_TIMES_ONLY,
                        onClick = { selectedFields = PasteOverFields.PRESET_TIMES_ONLY },
                        label = { Text("仅起止时间") },
                    )
                    FilterChip(
                        selected = selectedFields == PasteOverFields.PRESET_STYLE_MARGINS,
                        onClick = { selectedFields = PasteOverFields.PRESET_STYLE_MARGINS },
                        label = { Text("样式与边距") },
                    )
                    FilterChip(
                        selected = selectedFields == PasteOverFields.PRESET_ALL,
                        onClick = { selectedFields = PasteOverFields.PRESET_ALL },
                        label = { Text("全选") },
                    )
                    FilterChip(
                        selected = selectedFields.isEmpty(),
                        onClick = { selectedFields = emptySet() },
                        label = { Text("全不选") },
                    )
                }

                val allFields = PasteOverFields.Field.entries
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in allFields.indices step 2) {
                        Row(Modifier.fillMaxWidth()) {
                            val left = allFields[i]
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedFields = if (left in selectedFields) selectedFields - left else selectedFields + left
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = left in selectedFields,
                                    onCheckedChange = { c ->
                                        selectedFields = if (c) selectedFields + left else selectedFields - left
                                    },
                                )
                                Text(left.labelZh, style = MaterialTheme.typography.labelSmall)
                            }
                            val right = allFields.getOrNull(i + 1)
                            if (right != null) {
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedFields = if (right in selectedFields) selectedFields - right else selectedFields + right
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = right in selectedFields,
                                        onCheckedChange = { c ->
                                            selectedFields = if (c) selectedFields + right else selectedFields - right
                                        },
                                    )
                                    Text(right.labelZh, style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = raw.isNotBlank() && selectedFields.isNotEmpty(),
                onClick = { onApply(raw, selectedFields) },
            ) {
                Text("覆盖选中字段")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun FullFindReplaceDialog(
    events: List<AssEvent>,
    selectedIds: Set<Long>,
    onFindNext: (String, FindReplaceStepper.TargetField, Boolean, Boolean) -> Unit,
    onReplaceNext: (String, String, FindReplaceStepper.TargetField, Boolean, Boolean) -> Unit,
    onReplaceAll: (String, String, FindReplaceStepper.TargetField, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var field by remember { mutableStateOf(FindReplaceStepper.TargetField.TEXT) }
    var useRegex by remember { mutableStateOf(false) }
    var ignoreCase by remember { mutableStateOf(true) }
    var selectedOnly by remember { mutableStateOf(selectedIds.isNotEmpty()) }

    val matchCount = remember(events, query, field, useRegex, ignoreCase, selectedOnly) {
        if (query.isEmpty()) 0
        else events.sumOf { e ->
            if (selectedOnly && selectedIds.isNotEmpty() && e.id !in selectedIds) 0
            else {
                val hay = when (field) {
                    FindReplaceStepper.TargetField.TEXT -> e.text
                    FindReplaceStepper.TargetField.STRIPPED_TEXT -> e.strippedText
                    FindReplaceStepper.TargetField.STYLE -> e.style
                    FindReplaceStepper.TargetField.ACTOR -> e.actor
                    FindReplaceStepper.TargetField.EFFECT -> e.effect
                }
                FindReplace.count(hay, query, useRegex, ignoreCase)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查找与替换") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("查找内容") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换为") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FindReplaceStepper.TargetField.entries.forEach { tf ->
                        FilterChip(
                            selected = field == tf,
                            onClick = { field = tf },
                            label = { Text(tf.labelZh, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                SgmyCheckRow("忽略大小写", ignoreCase) { ignoreCase = it }
                SgmyCheckRow("使用正则表达式 (Regex)", useRegex) { useRegex = it }
                if (selectedIds.isNotEmpty()) {
                    SgmyCheckRow("全部替换时仅限选中的 ${selectedIds.size} 行", selectedOnly) { selectedOnly = it }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("匹配总数：$matchCount 处", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            enabled = query.isNotEmpty(),
                            onClick = { onFindNext(query, field, useRegex, ignoreCase) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        ) { Text("查找下一个", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            enabled = query.isNotEmpty(),
                            onClick = { onReplaceNext(query, replacement, field, useRegex, ignoreCase) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        ) { Text("替换并下一个", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = query.isNotEmpty(),
                onClick = { onReplaceAll(query, replacement, field, useRegex, ignoreCase, selectedOnly) },
            ) {
                Text("全部替换")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun FullSortLinesDialog(
    selectedCount: Int,
    onSort: (SortKey, SortOrder, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(SortKey.START) }
    var order by remember { mutableStateOf(SortOrder.ASCENDING) }
    var selectedOnly by remember { mutableStateOf(selectedCount >= 2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序字幕行") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    SortKey.entries.forEach { k ->
                        val label = when (k) {
                            SortKey.START -> "开始时间"
                            SortKey.END -> "结束时间"
                            SortKey.STYLE -> "样式名"
                            SortKey.ACTOR -> "说话人"
                            SortKey.EFFECT -> "特效备注"
                            SortKey.LAYER -> "图层"
                            SortKey.TEXT -> "台词文本"
                        }
                        FilterChip(selected = key == k, onClick = { key = k }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = order == SortOrder.ASCENDING, onClick = { order = SortOrder.ASCENDING }, label = { Text("升序 ↑") })
                    FilterChip(selected = order == SortOrder.DESCENDING, onClick = { order = SortOrder.DESCENDING }, label = { Text("降序 ↓") })
                }
                if (selectedCount >= 2) {
                    SgmyCheckRow("仅在原位排序选中的 $selectedCount 行（未选中行保持原位置）", selectedOnly) { selectedOnly = it }
                }
            }
        },
        confirmButton = { Button(onClick = { onSort(key, order, selectedOnly) }) { Text("执行排序") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun FullTimingProcessorDialog(
    styleNames: List<String>,
    selectedIds: Set<Long>,
    keyframeCount: Int,
    defaultLeadIn: Int,
    defaultLeadOut: Int,
    onApply: (FullTimingProcessor.Config) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedOnly by remember { mutableStateOf(selectedIds.isNotEmpty()) }
    var allowedStyles by remember { mutableStateOf<Set<String>>(emptySet()) } // empty = 全部样式
    var enableLeadIn by remember { mutableStateOf(true) }
    var leadIn by remember { mutableStateOf(defaultLeadIn.toString()) }
    var enableLeadOut by remember { mutableStateOf(true) }
    var leadOut by remember { mutableStateOf(defaultLeadOut.toString()) }

    var enableAdjacent by remember { mutableStateOf(true) }
    var maxGap by remember { mutableStateOf("300") }
    var maxOverlap by remember { mutableStateOf("150") }
    var adjacentBias by remember { mutableFloatStateOf(0.8f) }

    var enableKfSnap by remember { mutableStateOf(keyframeCount > 0) }
    var startsBeforeKf by remember { mutableStateOf("200") }
    var startsAfterKf by remember { mutableStateOf("200") }
    var endsBeforeKf by remember { mutableStateOf("200") }
    var endsAfterKf by remember { mutableStateOf("250") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时间后处理器") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedIds.isNotEmpty()) {
                    SgmyCheckRow("仅处理当前选中的 ${selectedIds.size} 行", selectedOnly) { selectedOnly = it }
                }
                if (styleNames.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = allowedStyles.isEmpty(),
                            onClick = { allowedStyles = emptySet() },
                            label = { Text("全部样式") },
                        )
                        styleNames.forEach { st ->
                            FilterChip(
                                selected = st in allowedStyles,
                                onClick = {
                                    allowedStyles = if (st in allowedStyles) allowedStyles - st else allowedStyles + st
                                },
                                label = { Text(st) },
                            )
                        }
                    }
                }

                HorizontalDivider()
                Text("1. 防碰撞前后导延长", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableLeadIn, onCheckedChange = { enableLeadIn = it })
                    OutlinedTextField(
                        value = leadIn,
                        onValueChange = { leadIn = it },
                        enabled = enableLeadIn,
                        label = { Text("前导 Lead-In (ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enableLeadOut, onCheckedChange = { enableLeadOut = it })
                    OutlinedTextField(
                        value = leadOut,
                        onValueChange = { leadOut = it },
                        enabled = enableLeadOut,
                        label = { Text("后导 Lead-Out (ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                HorizontalDivider()
                SgmyCheckRow("2. 相邻字幕连轴与消除微重叠", enableAdjacent) { enableAdjacent = it }
                if (enableAdjacent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxGap,
                            onValueChange = { maxGap = it },
                            label = { Text("最大连轴间隙 (ms)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = maxOverlap,
                            onValueChange = { maxOverlap = it },
                            label = { Text("最大去重叠 (ms)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("边界偏置 ${(adjacentBias * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(8.dp))
                        Slider(value = adjacentBias, onValueChange = { adjacentBias = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
                    }
                }

                HorizontalDivider()
                SgmyCheckRow("3. 关键帧吸附（当前已载入 $keyframeCount 个关键帧）", enableKfSnap) { enableKfSnap = it }
                if (enableKfSnap) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startsBeforeKf,
                            onValueChange = { startsBeforeKf = it },
                            label = { Text("起点早于关键帧 (ms)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = startsAfterKf,
                            onValueChange = { startsAfterKf = it },
                            label = { Text("起点晚于关键帧 (ms)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = endsBeforeKf,
                            onValueChange = { endsBeforeKf = it },
                            label = { Text("终点早于关键帧 (ms)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = endsAfterKf,
                            onValueChange = { endsAfterKf = it },
                            label = { Text("终点晚于关键帧 (ms)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(
                    FullTimingProcessor.Config(
                        allowedStyles = allowedStyles,
                        selectedIds = if (selectedOnly) selectedIds else emptySet(),
                        enableLeadIn = enableLeadIn,
                        leadInMs = leadIn.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        enableLeadOut = enableLeadOut,
                        leadOutMs = leadOut.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        enableMakeAdjacent = enableAdjacent,
                        maxGapMs = maxGap.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        maxOverlapMs = maxOverlap.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        adjacentBias = adjacentBias.toDouble(),
                        enableKeyframeSnap = enableKfSnap,
                        startsBeforeKfMs = startsBeforeKf.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        startsAfterKfMs = startsAfterKf.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        endsBeforeKfMs = endsBeforeKf.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                        endsAfterKfMs = endsAfterKf.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                    ),
                )
            }) { Text("执行后处理") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun AutomationManagerDialog(
    onRunMacro: (AutomationEngine.BuiltinMacro) -> Unit,
    onInsertTemplateStarter: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Automation 4 自动化脚本") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onInsertTemplateStarter,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("在当前行上方插入卡拉OK模板示例 (template syl)")
                }
                HorizontalDivider()
                AutomationEngine.BuiltinMacro.entries.forEach { macro ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(macro.titleZh, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(macro.subtitleZh, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalButton(
                                onClick = { onRunMacro(macro); onDismiss() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("运行", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
internal fun AttachmentsDialog(
    attachments: List<AssAttachment>,
    onAddAttachment: (String, AttachmentGroup, ByteArray) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingGroup by remember { mutableStateOf(AttachmentGroup.FONT) }
    var pendingExtract by remember { mutableStateOf<AssAttachment?>(null) }

    val openAttachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val group = pendingGroup
            scope.launch {
                val (name, bytes) = withContext(Dispatchers.IO) {
                    var resolvedName = if (group == AttachmentGroup.FONT) "embedded_font.ttf" else "graphic.png"
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) resolvedName = cursor.getString(idx) ?: resolvedName
                        }
                    }
                    val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                    resolvedName to raw
                }
                if (bytes.isNotEmpty()) onAddAttachment(name, group, bytes)
            }
        }
    }

    val extractLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val att = pendingExtract
        pendingExtract = null
        if (uri != null && att != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = att.decodeBytes()
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ASS 内嵌附件管理 (${attachments.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            pendingGroup = AttachmentGroup.FONT
                            openAttachmentLauncher.launch(arrayOf("font/*", "*/*"))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("内嵌字体") }
                    FilledTonalButton(
                        onClick = {
                            pendingGroup = AttachmentGroup.GRAPHIC
                            openAttachmentLauncher.launch(arrayOf("image/*"))
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("内嵌图片") }
                }

                if (attachments.isEmpty()) {
                    Text(
                        "当前脚本未包含 [Fonts] 或 [Graphics] 内嵌附件。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                        items(attachments, key = { it.fileName }) { att ->
                            val kb = (att.approximateByteSize / 1024).coerceAtLeast(1)
                            val typeTag = if (att.group == AttachmentGroup.FONT) "字体 [Fonts]" else "图片 [Graphics]"
                            ListItem(
                                headlineContent = { Text(att.displayFileName, maxLines = 1) },
                                supportingContent = { Text("$typeTag · 约 ${kb} KB") },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            pendingExtract = att
                                            extractLauncher.launch(att.displayFileName)
                                        }) {
                                            Icon(Icons.Filled.FileDownload, contentDescription = "提取保存")
                                        }
                                        IconButton(onClick = { onRemoveAttachment(att.fileName) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "移除")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
internal fun DummyVideoDialog(
    playback: PlaybackState,
    onApply: (Int, Int, Float, Long, Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var widthText by remember { mutableStateOf((playback.videoWidth.takeIf { it > 0 } ?: 1920).toString()) }
    var heightText by remember { mutableStateOf((playback.videoHeight.takeIf { it > 0 } ?: 1080).toString()) }
    var fpsText by remember { mutableStateOf((playback.fps.takeIf { it > 0f } ?: 23.976f).toString()) }
    var durationMinutes by remember { mutableStateOf(((playback.durationMs / 60_000L).takeIf { it > 0L } ?: 10L).toString()) }
    var checkerboard by remember { mutableStateOf(playback.dummyCheckerboard) }
    var colorArgb by remember { mutableIntStateOf(playback.dummyColorArgb) }

    val colorChoices = remember {
        listOf(
            0xFF181E29.toInt() to "深蓝灰",
            0xFF000000.toInt() to "纯黑",
            0xFF2F3640.toInt() to "中性灰",
            0xFF00B140.toInt() to "绿幕",
            0xFFFFFFFF.toInt() to "纯白",
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("虚拟视频画布 (Dummy Video)") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        Triple("1080p", "1920", "1080"),
                        Triple("720p", "1280", "720"),
                        Triple("4K", "3840", "2160"),
                        Triple("竖屏 1080×1920", "1080", "1920"),
                        Triple("SD 640×480", "640", "480"),
                    ).forEach { (lbl, w, h) ->
                        FilterChip(
                            selected = widthText == w && heightText == h,
                            onClick = { widthText = w; heightText = h },
                            label = { Text(lbl) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it },
                        label = { Text("画幅宽度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("画幅高度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fpsText,
                        onValueChange = { fpsText = it },
                        label = { Text("帧率 FPS") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = durationMinutes,
                        onValueChange = { durationMinutes = it },
                        label = { Text("总时长 (分钟)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("23.976", "24", "25", "29.97", "59.94", "60").forEach { f ->
                        FilterChip(selected = fpsText == f, onClick = { fpsText = f }, label = { Text("${f}fps") })
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    colorChoices.forEach { (argb, name) ->
                        Row(
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .clickable { colorArgb = argb }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (colorArgb == argb) 2.dp else 1.dp,
                                        color = if (colorArgb == argb) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                SgmyCheckRow("叠加双色棋盘格底纹（便于观察半透明与遮罩）", checkerboard) { checkerboard = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = (widthText.toIntOrNull() ?: 1920).coerceIn(64, 7680)
                val h = (heightText.toIntOrNull() ?: 1080).coerceIn(64, 4320)
                val fps = (fpsText.toFloatOrNull() ?: 23.976f).coerceIn(1f, 240f)
                val mins = (durationMinutes.toLongOrNull() ?: 10L).coerceIn(1L, 600L)
                onApply(w, h, fps, mins * 60_000L, colorArgb, checkerboard)
            }) { Text("启用画布") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun JumpToDialog(
    playback: PlaybackState,
    timecodesMs: List<Long>,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val fps = if (playback.fps > 0f) playback.fps.toDouble() else 23.976
    fun msToFrame(ms: Long): Int {
        if (timecodesMs.isNotEmpty()) {
            val idx = timecodesMs.binarySearch(ms)
            return if (idx >= 0) idx else (-idx - 1).coerceIn(0, timecodesMs.lastIndex)
        }
        return ((ms / 1000.0) * fps).roundToInt().coerceAtLeast(0)
    }
    fun frameToMs(frame: Int): Long {
        if (timecodesMs.isNotEmpty()) {
            return timecodesMs[frame.coerceIn(0, timecodesMs.lastIndex)]
        }
        return ((frame.coerceAtLeast(0) * 1000.0) / fps).toLong()
    }

    var targetMs by remember { mutableStateOf(playback.positionMs) }
    var frameInput by remember { mutableStateOf(msToFrame(playback.positionMs).toString()) }
    var timeInput by remember { mutableStateOf(SubTime.ofMillis(playback.positionMs).toAssString(false)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到时间或帧号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = frameInput,
                    onValueChange = { raw ->
                        frameInput = raw
                        raw.toIntOrNull()?.let { f ->
                            val ms = frameToMs(f)
                            targetMs = ms
                            timeInput = SubTime.ofMillis(ms).toAssString(false)
                        }
                    },
                    label = { Text("帧号 (Frame)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = { raw ->
                        timeInput = raw
                        runCatching { SubTime.parseAss(raw) }.onSuccess { st ->
                            targetMs = st.millis
                            frameInput = msToFrame(st.millis).toString()
                        }
                    },
                    label = { Text("时间码 (H:MM:SS.cc)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSeek(targetMs.coerceAtLeast(0L)); onDismiss() }) { Text("跳转") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun VideoDetailsDialog(
    script: AssScript,
    hasMedia: Boolean,
    playback: PlaybackState,
    keyframeCount: Int,
    timecodeCount: Int,
    onImportKeyframes: () -> Unit,
    onExportKeyframes: () -> Unit,
    onImportTimecodes: () -> Unit,
    onExportTimecodes: () -> Unit,
    onOpenDummyVideo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fps = if (playback.fps > 0f) playback.fps else 23.976f
    val totalFrames = ((playback.durationMs / 1000.0) * fps).roundToInt()
    val resLabel = if (playback.videoWidth > 0 && playback.videoHeight > 0) {
        "${playback.videoWidth} × ${playback.videoHeight}"
    } else "未加载"
    val playRes = "${script.getScriptInfo("PlayResX") ?: "1920"} × ${script.getScriptInfo("PlayResY") ?: "1080"}"
    val sourceType = when {
        hasMedia -> "本地音视频文件"
        playback.isDummyVideo -> "虚拟视频画布 (Dummy Video)"
        else -> "未关联视频"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("视频与时间码详情") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailKvRow("当前源类型", sourceType)
                DetailKvRow("视频分辨率", resLabel)
                DetailKvRow("脚本 PlayRes", playRes)
                DetailKvRow("帧率与总帧数", "${"%.3f".format(fps)} fps · 约 $totalFrames 帧")
                DetailKvRow("总时长", SubTime.ofMillis(playback.durationMs).toAssString(false))
                DetailKvRow("色彩信息", playback.colorInfoLabel.ifEmpty { script.getScriptInfo("YCbCr Matrix") ?: "TV.709 (SDR)" })
                DetailKvRow("关键帧数量", "$keyframeCount 个")
                DetailKvRow("VFR 时间码表", if (timecodeCount > 0) "已载入 $timecodeCount 帧" else "恒定帧率 (CFR)")

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDismiss(); onImportKeyframes() }, modifier = Modifier.weight(1f)) { Text("导入关键帧") }
                    OutlinedButton(onClick = { onDismiss(); onExportKeyframes() }, modifier = Modifier.weight(1f)) { Text("导出关键帧") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onDismiss(); onImportTimecodes() }, modifier = Modifier.weight(1f)) { Text("导入时间码") }
                    OutlinedButton(onClick = { onDismiss(); onExportTimecodes() }, modifier = Modifier.weight(1f)) { Text("导出时间码") }
                }
                TextButton(onClick = onOpenDummyVideo) { Text("配置虚拟视频画布…") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun DetailKvRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun TextImportOptionsDialog(
    onApply: (TextImportProcessor.Options) -> Unit,
    onDismiss: () -> Unit,
) {
    var actorSep by remember { mutableStateOf(":") }
    var commentStarter by remember { mutableStateOf("#") }
    var includeBlanks by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("纯文本台词分角解析") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(":" to "英文冒号 (:)", "：" to "中文冒号 (：)", " - " to "破折号 ( - )").forEach { (sep, lbl) ->
                        FilterChip(selected = actorSep == sep, onClick = { actorSep = sep }, label = { Text(lbl) })
                    }
                }
                OutlinedTextField(
                    value = actorSep,
                    onValueChange = { actorSep = it },
                    label = { Text("说话人分隔符（例如 Alice: 台词）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = commentStarter,
                    onValueChange = { commentStarter = it },
                    label = { Text("注释行识别前缀") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SgmyCheckRow("保留空白行", includeBlanks) { includeBlanks = it }
            }
        },
        confirmButton = {
            Button(onClick = {
                onApply(TextImportProcessor.Options(actorSep, commentStarter, includeBlanks))
            }) { Text("解析说话人") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private data class SpellIssue(
    val eventId: Long,
    val rowNumber: Int,
    val description: String,
    val originalText: String,
    val suggestedText: String,
)

@Composable
internal fun SpellCheckerDialog(
    events: List<AssEvent>,
    onJumpToEvent: (Long) -> Unit,
    onFixEventText: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val issues = remember(events) { scanSpellAndTypoIssues(events) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("拼写与错字检查 (${issues.size})") },
        text = {
            if (issues.isEmpty()) {
                Text("未发现重复词、未闭合花括号或常见英文拼写错误。", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(issues, key = { "${it.eventId}_${it.description}" }) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().clickable { onJumpToEvent(item.eventId) },
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${item.rowNumber} · ${item.description}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    if (item.suggestedText != item.originalText) {
                                        TextButton(
                                            onClick = { onFixEventText(item.eventId, item.suggestedText) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(26.dp),
                                        ) { Text("修正", style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                                Text(item.originalText, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (issues.any { it.suggestedText != it.originalText }) {
                Button(onClick = {
                    issues.filter { it.suggestedText != it.originalText }
                        .distinctBy { it.eventId }
                        .forEach { onFixEventText(it.eventId, it.suggestedText) }
                    onDismiss()
                }) { Text("一键全部修正") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private val repeatedWordRe = Regex("""\b([A-Za-z]{2,})\s+\1\b""", RegexOption.IGNORE_CASE)
private val doubleSpaceRe = Regex("""(?<!\\N) {2,}""")
private val contractionTypos = mapOf(
    Regex("""\bdont\b""", RegexOption.IGNORE_CASE) to "don't",
    Regex("""\bcant\b""", RegexOption.IGNORE_CASE) to "can't",
    Regex("""\bwont\b""", RegexOption.IGNORE_CASE) to "won't",
    Regex("""\bdidnt\b""", RegexOption.IGNORE_CASE) to "didn't",
    Regex("""\bdoesnt\b""", RegexOption.IGNORE_CASE) to "doesn't",
    Regex("""\bisnt\b""", RegexOption.IGNORE_CASE) to "isn't",
    Regex("""\barent\b""", RegexOption.IGNORE_CASE) to "aren't",
    Regex("""\bteh\b""", RegexOption.IGNORE_CASE) to "the",
    Regex("""\brecieve\b""", RegexOption.IGNORE_CASE) to "receive",
)

private fun scanSpellAndTypoIssues(events: List<AssEvent>): List<SpellIssue> {
    val result = mutableListOf<SpellIssue>()
    events.forEachIndexed { idx, ev ->
        if (ev.comment || ev.text.isBlank()) return@forEachIndexed
        val rowNum = idx + 1
        val openBraces = ev.text.count { it == '{' }
        val closeBraces = ev.text.count { it == '}' }
        if (openBraces != closeBraces) {
            val fixed = if (openBraces > closeBraces) ev.text + "}".repeat(openBraces - closeBraces) else ev.text
            result += SpellIssue(ev.id, rowNum, "ASS 标签花括号未闭合", ev.text, fixed)
        }
        val stripped = ev.strippedText
        if (repeatedWordRe.containsMatchIn(stripped)) {
            val fixed = repeatedWordRe.replace(ev.text) { it.groupValues[1] }
            result += SpellIssue(ev.id, rowNum, "连续重复单词", ev.text, fixed)
        }
        if (doubleSpaceRe.containsMatchIn(stripped)) {
            val fixed = doubleSpaceRe.replace(ev.text, " ")
            result += SpellIssue(ev.id, rowNum, "多余连续空格", ev.text, fixed)
        }
        for ((re, replacement) in contractionTypos) {
            if (re.containsMatchIn(stripped)) {
                val fixed = re.replace(ev.text) { m ->
                    val hit = m.value
                    if (hit.first().isUpperCase()) replacement.replaceFirstChar { it.uppercase() } else replacement
                }
                result += SpellIssue(ev.id, rowNum, "英文拼写/缩写建议 ($replacement)", ev.text, fixed)
                break
            }
        }
    }
    return result
}
