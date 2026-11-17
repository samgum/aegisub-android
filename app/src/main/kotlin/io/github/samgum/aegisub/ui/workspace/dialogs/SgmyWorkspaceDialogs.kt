package io.github.samgum.aegisub.ui.workspace.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.data.ai.AiAnalysisService
import io.github.samgum.aegisub.data.settings.UserSettings
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.sgmy.ChineseConverter
import io.github.samgum.aegisub.domain.sgmy.FixCommonErrors
import io.github.samgum.aegisub.domain.sgmy.FuriganaAnnotator
import io.github.samgum.aegisub.domain.sgmy.LyricScrollGenerator
import io.github.samgum.aegisub.domain.sgmy.PairedPunctuationChecker
import io.github.samgum.aegisub.domain.sgmy.StitchTimings
import io.github.samgum.aegisub.domain.sgmy.StyleOverlapChecker
import io.github.samgum.aegisub.domain.sgmy.SubtitleTextCleaner
import io.github.samgum.aegisub.ui.workspace.WorkspaceViewModel
import io.github.samgum.aegisub.ui.workspace.components.WorkspaceDialogType
import kotlinx.coroutines.launch

/**
 * `samgum/Aegisub` (SGMY) 全部独家专业工具对话框集合：
 * 1. 修复常见错误 (`DialogFixCommonErrors`)
 * 2. 音乐滚动歌词生成器 (`DialogLyricScroll`)
 * 3. 简繁中文互转 (`ICU Chinese Conversion`)
 * 4. 字幕文本清理 (`Subtitle Text Cleanup`)
 * 5. 配对标点检查 (`Paired Punctuation Checker`)
 * 6. 日语汉字假名注音 (`Furigana Annotator`)
 * 7. 时间缝合器 (`Stitch Timings`)
 * 8. 同样式时间重叠检查 (`Style Overlap Checker`)
 * 9. AI 字幕语法分析 / 翻译 / 润色 (`DialogAiAnalysis`)
 *
 * @author 伤感咩吖
 */
@Composable
fun SgmyFixCommonErrorsDialog(
    selectedIds: Set<Long>,
    onApply: (FixCommonErrors.Config) -> FixCommonErrors.Result,
    onDismiss: () -> Unit,
) {
    var fixOverlaps by remember { mutableStateOf(true) }
    var fixShortGaps by remember { mutableStateOf(true) }
    var fixShortDur by remember { mutableStateOf(true) }
    var fixLongDur by remember { mutableStateOf(false) }
    var stripTrailing by remember { mutableStateOf(true) }
    var removeEmpty by remember { mutableStateOf(false) }
    var selectedOnly by remember { mutableStateOf(selectedIds.isNotEmpty()) }

    var minGapText by remember { mutableStateOf("100") }
    var minDurText by remember { mutableStateOf("1000") }
    var maxDurText by remember { mutableStateOf("7000") }
    var lastResult by remember { mutableStateOf<FixCommonErrors.Result?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修复常见字幕错误") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SgmyCheckRow("修复重叠显示时间（截断前句结束至下句开始）", fixOverlaps) { fixOverlaps = it }
                SgmyCheckRow("修复行间过短间隔", fixShortGaps) { fixShortGaps = it }
                SgmyCheckRow("延长过短字幕时长", fixShortDur) { fixShortDur = it }
                SgmyCheckRow("截断过长字幕时长", fixLongDur) { fixLongDur = it }
                SgmyCheckRow("去除行尾空白（含全角空格 U+3000）", stripTrailing) { stripTrailing = it }
                SgmyCheckRow("删除空行 / 仅含 ASS 不可见空白的行", removeEmpty) { removeEmpty = it }

                if (selectedIds.isNotEmpty()) {
                    SgmyCheckRow("仅作用于当前多选的 ${selectedIds.size} 行", selectedOnly) { selectedOnly = it }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minGapText,
                        onValueChange = { minGapText = it },
                        label = { Text("最小间隔(ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minDurText,
                        onValueChange = { minDurText = it },
                        label = { Text("最短时长(ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxDurText,
                        onValueChange = { maxDurText = it },
                        label = { Text("最长时长(ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                lastResult?.let { res ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("修复报告（时间/文本/删空行已按独立撤销步提交）：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("• 重叠截断：${res.overlapsTrimmed} 处 · 过短间隔：${res.shortGapsFixed} 处", style = MaterialTheme.typography.bodySmall)
                            Text("• 过短延长：${res.shortDurationsExtended} 处 · 过长截断：${res.longDurationsTrimmed} 处", style = MaterialTheme.typography.bodySmall)
                            Text("• 行尾去空：${res.trailingSpacesStripped} 行 · 删除空行：${res.emptyLinesRemoved} 行", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cfg = FixCommonErrors.Config(
                        fixOverlaps = fixOverlaps,
                        fixShortGaps = fixShortGaps,
                        fixShortDurations = fixShortDur,
                        fixLongDurations = fixLongDur,
                        removeEmptyLines = removeEmpty,
                        stripTrailingWhitespace = stripTrailing,
                        minGapMs = minGapText.toLongOrNull()?.coerceAtLeast(0L) ?: 100L,
                        minDurationMs = minDurText.toLongOrNull()?.coerceAtLeast(100L) ?: 1_000L,
                        maxDurationMs = maxDurText.toLongOrNull()?.coerceAtLeast(500L) ?: 7_000L,
                        selectedIds = if (selectedOnly) selectedIds else emptySet(),
                    )
                    lastResult = onApply(cfg)
                },
            ) { Text("立即执行修复") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
fun SgmyLyricScrollDialog(
    selectedIds: Set<Long>,
    onGenerate: (LyricScrollGenerator.Settings) -> LyricScrollGenerator.Result,
    onDismiss: () -> Unit,
) {
    var preset by remember { mutableStateOf(LyricScrollGenerator.ResolutionPreset.FHD_1080P) }
    var presetMenu by remember { mutableStateOf(false) }
    var bilingual by remember { mutableStateOf(false) }
    var align by remember { mutableStateOf(LyricScrollGenerator.HorizontalAlign.CENTER) }
    var sourceAction by remember { mutableStateOf(LyricScrollGenerator.SourceAction.COMMENT_HIDE) }
    var animate by remember { mutableStateOf(true) }
    var clearPrev by remember { mutableStateOf(true) }
    var selectedOnly by remember { mutableStateOf(selectedIds.isNotEmpty()) }
    var neighborsText by remember { mutableStateOf("3") }
    var transitionMsText by remember { mutableStateOf("550") }
    var customWText by remember { mutableStateOf("1920") }
    var customHText by remember { mutableStateOf("1080") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("滚动歌词生成器") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "生成多行平滑滚动歌词，支持单语或双语自动配对。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 分辨率预设选择
                Box {
                    OutlinedButton(onClick = { presetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("目标画幅预设：${preset.labelZh}")
                    }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        LyricScrollGenerator.ResolutionPreset.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.labelZh) },
                                onClick = { preset = p; presetMenu = false },
                            )
                        }
                    }
                }

                if (preset == LyricScrollGenerator.ResolutionPreset.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customWText,
                            onValueChange = { customWText = it },
                            label = { Text("宽度 px") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = customHText,
                            onValueChange = { customHText = it },
                            label = { Text("高度 px") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // 单语 / 双语模式
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !bilingual, onClick = { bilingual = false }, label = { Text("单语歌词") })
                    FilterChip(selected = bilingual, onClick = { bilingual = true }, label = { Text("双语自动配对") })
                }

                // 水平对齐
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = align == LyricScrollGenerator.HorizontalAlign.LEFT,
                        onClick = { align = LyricScrollGenerator.HorizontalAlign.LEFT },
                        label = { Text("左对齐") },
                    )
                    FilterChip(
                        selected = align == LyricScrollGenerator.HorizontalAlign.CENTER,
                        onClick = { align = LyricScrollGenerator.HorizontalAlign.CENTER },
                        label = { Text("居中") },
                    )
                    FilterChip(
                        selected = align == LyricScrollGenerator.HorizontalAlign.RIGHT,
                        onClick = { align = LyricScrollGenerator.HorizontalAlign.RIGHT },
                        label = { Text("右对齐") },
                    )
                }

                // 原歌词处理方式
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = sourceAction == LyricScrollGenerator.SourceAction.COMMENT_HIDE,
                        onClick = { sourceAction = LyricScrollGenerator.SourceAction.COMMENT_HIDE },
                        label = { Text("注释隐藏原行") },
                    )
                    FilterChip(
                        selected = sourceAction == LyricScrollGenerator.SourceAction.KEEP,
                        onClick = { sourceAction = LyricScrollGenerator.SourceAction.KEEP },
                        label = { Text("保留原行") },
                    )
                    FilterChip(
                        selected = sourceAction == LyricScrollGenerator.SourceAction.DELETE,
                        onClick = { sourceAction = LyricScrollGenerator.SourceAction.DELETE },
                        label = { Text("删除原行") },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = neighborsText,
                        onValueChange = { neighborsText = it },
                        label = { Text("上下可见邻行数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = transitionMsText,
                        onValueChange = { transitionMsText = it },
                        label = { Text("过渡动画(ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                SgmyCheckRow("启用平滑三次缓出滚动动画 (\\move + \\t)", animate) { animate = it }
                SgmyCheckRow("自动清理旧版已生成的滚动歌词行", clearPrev) { clearPrev = it }
                if (selectedIds.isNotEmpty()) {
                    SgmyCheckRow("仅使用当前选中的 ${selectedIds.size} 行", selectedOnly) { selectedOnly = it }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val settings = LyricScrollGenerator.Settings(
                        selectedOnly = selectedOnly && selectedIds.isNotEmpty(),
                        sourceAction = sourceAction,
                        languageMode = if (bilingual) LyricScrollGenerator.LanguageMode.BILINGUAL else LyricScrollGenerator.LanguageMode.SINGLE,
                        clearPrevious = clearPrev,
                        animate = animate,
                        resolutionPreset = preset,
                        customWidth = customWText.toIntOrNull() ?: 1920,
                        customHeight = customHText.toIntOrNull() ?: 1080,
                        horizontalAlign = align,
                        visibleNeighborLines = (neighborsText.toIntOrNull() ?: 3).coerceIn(1, 6),
                        transitionMs = (transitionMsText.toIntOrNull() ?: 550).coerceIn(100, 2000),
                    )
                    onGenerate(settings)
                    onDismiss()
                },
            ) { Text("生成滚动歌词") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun SgmyCjkToolsDialog(
    initialTab: WorkspaceDialogType,
    script: AssScript,
    selectedIds: Set<Long>,
    onSwitchDialog: (WorkspaceDialogType) -> Unit,
    onChineseConvert: (ChineseConverter.Config) -> ChineseConverter.Result,
    onTextCleanup: (SubtitleTextCleaner.Config) -> SubtitleTextCleaner.Stats,
    onCheckPairs: (Boolean, String?) -> PairedPunctuationChecker.Report,
    onApplyFurigana: (String, FuriganaAnnotator.Config) -> FuriganaAnnotator.Result,
    onJumpToEvent: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val styleNames = remember(script.styles) { listOf("") + script.styles.map { it.name } }
    var styleFilter by remember { mutableStateOf("") }
    var styleMenu by remember { mutableStateOf(false) }
    var selectedOnly by remember { mutableStateOf(selectedIds.isNotEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("中日韩文本工具")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = initialTab == WorkspaceDialogType.SGMY_CHINESE_CONVERT,
                        onClick = { onSwitchDialog(WorkspaceDialogType.SGMY_CHINESE_CONVERT) },
                        label = { Text("简繁互转") },
                    )
                    FilterChip(
                        selected = initialTab == WorkspaceDialogType.SGMY_TEXT_CLEANUP,
                        onClick = { onSwitchDialog(WorkspaceDialogType.SGMY_TEXT_CLEANUP) },
                        label = { Text("文本清理") },
                    )
                    FilterChip(
                        selected = initialTab == WorkspaceDialogType.SGMY_PAIR_CHECK,
                        onClick = { onSwitchDialog(WorkspaceDialogType.SGMY_PAIR_CHECK) },
                        label = { Text("配对标点") },
                    )
                    FilterChip(
                        selected = initialTab == WorkspaceDialogType.SGMY_FURIGANA,
                        onClick = { onSwitchDialog(WorkspaceDialogType.SGMY_FURIGANA) },
                        label = { Text("日语注音") },
                    )
                }
            }
        },
        text = {
            when (initialTab) {
                WorkspaceDialogType.SGMY_CHINESE_CONVERT -> {
                    var toTrad by remember { mutableStateOf(true) }
                    var convRes by remember { mutableStateOf<ChineseConverter.Result?>(null) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("自动跳过样式标签，仅转换台词正文。", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = toTrad, onClick = { toTrad = true }, label = { Text("简体 → 繁体") })
                            FilterChip(selected = !toTrad, onClick = { toTrad = false }, label = { Text("繁体 → 简体") })
                        }
                        StyleFilterDropdown(styleNames, styleFilter, styleMenu, { styleMenu = it }) { styleFilter = it; styleMenu = false }
                        if (selectedIds.isNotEmpty()) {
                            SgmyCheckRow("仅转换选中的 ${selectedIds.size} 行", selectedOnly) { selectedOnly = it }
                        }
                        Button(
                            onClick = {
                                convRes = onChineseConvert(
                                    ChineseConverter.Config(
                                        direction = if (toTrad) ChineseConverter.Direction.TO_TRADITIONAL else ChineseConverter.Direction.TO_SIMPLIFIED,
                                        selectedIds = if (selectedOnly) selectedIds else emptySet(),
                                        styleFilter = styleFilter.ifBlank { null },
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("执行简繁转换") }
                        convRes?.let { r ->
                            Text("已完成：检查 ${r.checkedLines} 行，转换 ${r.changedLines} 行（${r.changedPlainSegments} 处文本段）。", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                WorkspaceDialogType.SGMY_TEXT_CLEANUP -> {
                    var commas by remember { mutableStateOf(true) }
                    var periods by remember { mutableStateOf(true) }
                    var smartQuotes by remember { mutableStateOf(false) }
                    var fixDoubleSpaces by remember { mutableStateOf(true) }
                    var cleanStats by remember { mutableStateOf<SubtitleTextCleaner.Stats?>(null) }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SgmyCheckRow("全角中文逗号（，）替换为空格", commas) { commas = it }
                        SgmyCheckRow("清理中文句号（句末去除，句中转空格）", periods) { periods = it }
                        SgmyCheckRow("弯引号（“ ”）替换为直引号（\"）", smartQuotes) { smartQuotes = it }
                        SgmyCheckRow("合并连续多个半角空格为单空格", fixDoubleSpaces) { fixDoubleSpaces = it }
                        if (selectedIds.isNotEmpty()) {
                            SgmyCheckRow("仅清理选中的 ${selectedIds.size} 行", selectedOnly) { selectedOnly = it }
                        }
                        Button(
                            onClick = {
                                cleanStats = onTextCleanup(
                                    SubtitleTextCleaner.Config(
                                        replaceChineseCommas = commas,
                                        cleanChinesePeriods = periods,
                                        replaceSmartQuotes = smartQuotes,
                                        checkDoubleSpaces = true,
                                        fixDoubleSpaces = fixDoubleSpaces,
                                        selectedIds = if (selectedOnly) selectedIds else emptySet(),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("执行字幕文本清理") }
                        cleanStats?.let { st ->
                            Text(
                                "检查 ${st.checkedLines} 行，修改 ${st.changedLines} 行（逗号 ${st.commaReplacements}、句号删除 ${st.periodsRemoved}/加空 ${st.periodsSpaced}、连续空格修复 ${st.doubleSpaceRunsFixed}）。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                WorkspaceDialogType.SGMY_PAIR_CHECK -> {
                    val report = remember(script.events, selectedOnly, styleFilter) {
                        onCheckPairs(selectedOnly && selectedIds.isNotEmpty(), styleFilter.ifBlank { null })
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StyleFilterDropdown(styleNames, styleFilter, styleMenu, { styleMenu = it }) { styleFilter = it; styleMenu = false }
                        if (report.ok) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                Text(
                                    "✓ 检查通过！共扫描 ${report.checkedLines} 行，所有中英文括号/引号/书名号均已正确配对。",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else {
                            Text("发现 ${report.issues.size} 行存在未闭合或错位标点（点击直接定位）：", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                                items(report.issues, key = { it.eventId }) { issue ->
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onJumpToEvent(issue.eventId)
                                                onDismiss()
                                            }
                                            .padding(vertical = 6.dp),
                                    ) {
                                        Text("#${issue.rowNumber}  ${issue.textPreview}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        issue.messages.forEach { m ->
                                            Text("  • $m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        }
                                        HorizontalDivider(Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // SGMY_FURIGANA 日语汉字假名注音
                    var position by remember { mutableStateOf(FuriganaAnnotator.Position.ABOVE) }
                    var kanaMode by remember { mutableStateOf(FuriganaAnnotator.KanaMode.HIRAGANA) }
                    var sizePct by remember { mutableStateOf("55") }
                    var readingsText by remember(script.events, styleFilter, selectedOnly, kanaMode) {
                        mutableStateOf(
                            FuriganaAnnotator.buildReadingTemplate(
                                events = script.events,
                                selectedIds = if (selectedOnly) selectedIds else emptySet(),
                                styleFilter = styleFilter.ifBlank { null },
                                kanaMode = kanaMode,
                            ),
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("填写 `汉字=かな`（全局）或 `@行号#序号:汉字=かな`（逐处）：", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = position == FuriganaAnnotator.Position.ABOVE,
                                onClick = { position = FuriganaAnnotator.Position.ABOVE },
                                label = { Text("上方注音") },
                            )
                            FilterChip(
                                selected = position == FuriganaAnnotator.Position.BELOW,
                                onClick = { position = FuriganaAnnotator.Position.BELOW },
                                label = { Text("下方注音") },
                            )
                            FilterChip(
                                selected = kanaMode == FuriganaAnnotator.KanaMode.HIRAGANA,
                                onClick = { kanaMode = FuriganaAnnotator.KanaMode.HIRAGANA },
                                label = { Text("平假名") },
                            )
                            FilterChip(
                                selected = kanaMode == FuriganaAnnotator.KanaMode.KATAKANA,
                                onClick = { kanaMode = FuriganaAnnotator.KanaMode.KATAKANA },
                                label = { Text("片假名") },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = sizePct,
                                onValueChange = { sizePct = it },
                                label = { Text("注音字号 %") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(120.dp),
                            )
                            Box(Modifier.weight(1f)) {
                                StyleFilterDropdown(styleNames, styleFilter, styleMenu, { styleMenu = it }) { styleFilter = it; styleMenu = false }
                            }
                        }
                        OutlinedTextField(
                            value = readingsText,
                            onValueChange = { readingsText = it },
                            label = { Text("汉字读音映射表") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            minLines = 6,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                onApplyFurigana(
                                    readingsText,
                                    FuriganaAnnotator.Config(
                                        position = position,
                                        kanaMode = kanaMode,
                                        sizePercent = (sizePct.toIntOrNull() ?: 55).coerceIn(25, 100),
                                        selectedIds = if (selectedOnly) selectedIds else emptySet(),
                                        styleFilter = styleFilter.ifBlank { null },
                                    ),
                                )
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("应用假名注音") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
fun SgmyTimingToolsDialog(
    initialTab: WorkspaceDialogType,
    selectedIds: Set<Long>,
    onSwitchDialog: (WorkspaceDialogType) -> Unit,
    onApplyStitch: (StitchTimings.Config) -> Int,
    onAnalyzeOverlaps: () -> StyleOverlapChecker.Report,
    onJumpToEvent: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("时间缝合与重叠检查")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = initialTab == WorkspaceDialogType.SGMY_STITCH_TIMINGS,
                        onClick = { onSwitchDialog(WorkspaceDialogType.SGMY_STITCH_TIMINGS) },
                        label = { Text("时间缝合器") },
                    )
                    FilterChip(
                        selected = initialTab == WorkspaceDialogType.SGMY_STYLE_OVERLAP,
                        onClick = { onSwitchDialog(WorkspaceDialogType.SGMY_STYLE_OVERLAP) },
                        label = { Text("同样式重叠检查") },
                    )
                }
            }
        },
        text = {
            if (initialTab == WorkspaceDialogType.SGMY_STITCH_TIMINGS) {
                var anchor by remember { mutableStateOf(StitchTimings.Anchor.PREVIOUS_LINE_END) }
                var unitMode by remember { mutableStateOf(StitchTimings.UnitMode.TIME_MS) }
                var maxGapText by remember { mutableStateOf("250") }
                var selectedOnly by remember { mutableStateOf(selectedIds.isNotEmpty()) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("自动消除相邻字幕行之间的细小空隙：", style = MaterialTheme.typography.bodySmall)
                    FilterChip(
                        selected = anchor == StitchTimings.Anchor.PREVIOUS_LINE_END,
                        onClick = { anchor = StitchTimings.Anchor.PREVIOUS_LINE_END },
                        label = { Text("以前一句结束为基准（吸附下句起点）") },
                    )
                    FilterChip(
                        selected = anchor == StitchTimings.Anchor.NEXT_LINE_START,
                        onClick = { anchor = StitchTimings.Anchor.NEXT_LINE_START },
                        label = { Text("以下一句开始为基准（延伸前句终点）") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = unitMode == StitchTimings.UnitMode.TIME_MS,
                            onClick = { unitMode = StitchTimings.UnitMode.TIME_MS },
                            label = { Text("按毫秒时间缝合") },
                        )
                        FilterChip(
                            selected = unitMode == StitchTimings.UnitMode.FRAMES,
                            onClick = { unitMode = StitchTimings.UnitMode.FRAMES },
                            label = { Text("按视频帧边界缝合") },
                        )
                    }
                    OutlinedTextField(
                        value = maxGapText,
                        onValueChange = { maxGapText = it },
                        label = { Text("最大缝合间隙阈值 (ms)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (selectedIds.isNotEmpty()) {
                        SgmyCheckRow("仅缝合选中的 ${selectedIds.size} 行", selectedOnly) { selectedOnly = it }
                    }
                    Button(
                        onClick = {
                            onApplyStitch(
                                StitchTimings.Config(
                                    anchor = anchor,
                                    unitMode = unitMode,
                                    maxGapMs = maxGapText.toLongOrNull()?.coerceAtLeast(0L) ?: 250L,
                                    selectedIds = if (selectedOnly) selectedIds else emptySet(),
                                ),
                            )
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("执行时间缝合") }
                }
            } else {
                val report = remember { onAnalyzeOverlaps() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!report.hasAnyIssue) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                            Text("✓ 当前脚本没有任何同样式时间重叠或起止冲突。", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                            items(report.styles, key = { it.styleName }) { st ->
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text(
                                        "样式「${st.styleName}」（共 ${st.totalLines} 行）",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (st.hasIssues) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "完全同轴 ${st.exactSameTimePairs} 对 · 同起点 ${st.sameStartPairs} 对 · 同终点 ${st.sameEndPairs} 对 · 交叉重叠 ${st.crossingOverlapPairs} 对",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    st.crossingExamples.forEach { pair ->
                                        TextButton(
                                            onClick = {
                                                onJumpToEvent(pair.firstEventId)
                                                onDismiss()
                                            },
                                        ) {
                                            Text("定位冲突行：#${pair.firstRowNumber} ↔ #${pair.secondRowNumber}")
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SgmyAiAnalysisSheet(
    activeEvent: AssEvent?,
    allEvents: List<AssEvent>,
    settings: UserSettings,
    viewModel: WorkspaceViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var taskMode by remember { mutableStateOf(AiAnalysisService.TaskMode.GRAMMAR_ANALYSIS) }
    var showConfig by remember { mutableStateOf(viewModel.aiService.sessionApiKey.isBlank()) }
    var providerIdx by remember { mutableIntStateOf(settings.aiProviderIndex) }
    var providerMenu by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(settings.aiBaseUrl) }
    var model by remember { mutableStateOf(settings.aiModel) }
    var apiKey by remember { mutableStateOf(viewModel.aiService.sessionApiKey) }
    var targetLang by remember { mutableStateOf(settings.aiTargetLang) }
    var temperature by remember { mutableFloatStateOf(settings.aiTemperature) }

    var loading by remember { mutableStateOf(false) }
    var outputText by remember(activeEvent?.id) { mutableStateOf("") }
    var errorText by remember(activeEvent?.id) { mutableStateOf<String?>(null) }

    val contextNeighbors = remember(activeEvent?.id, allEvents) {
        if (activeEvent == null) emptyList()
        else {
            val idx = allEvents.indexOfFirst { it.id == activeEvent.id }
            if (idx < 0) emptyList()
            else ((idx - 2).coerceAtLeast(0)..(idx + 2).coerceAtMost(allEvents.lastIndex))
                .filter { it != idx }
                .map { allEvents[it].strippedText }
                .filter { it.isNotBlank() }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("AI 字幕分析与润色", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { showConfig = !showConfig }) {
                    Text(if (showConfig) "收起端点配置" else "配置服务商 / Key")
                }
            }

            if (showConfig) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                val pName = AiAnalysisService.PRESETS.getOrNull(providerIdx)?.name ?: "自定义"
                                Text("服务商预设：$pName")
                            }
                            DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                                AiAnalysisService.PRESETS.forEachIndexed { idx, preset ->
                                    DropdownMenuItem(
                                        text = { Text("${preset.name} (${preset.defaultModel})") },
                                        onClick = {
                                            providerIdx = idx
                                            baseUrl = preset.baseUrl
                                            model = preset.defaultModel
                                            providerMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("API Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("模型名 Model") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = targetLang, onValueChange = { targetLang = it }, label = { Text("目标语言") }, singleLine = true, modifier = Modifier.width(120.dp))
                        }
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key（仅驻留当前运行内存，不落盘）") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                viewModel.saveAiSettings(
                                    enabled = true,
                                    providerIndex = providerIdx,
                                    baseUrl = baseUrl,
                                    model = model,
                                    apiKey = apiKey,
                                    targetLang = targetLang,
                                    temperature = temperature,
                                    maxTokens = settings.aiMaxTokens,
                                    thinking = settings.aiThinking,
                                    cacheSession = settings.aiCacheSession,
                                )
                                showConfig = false
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("保存配置") }
                    }
                }
            }

            Text(
                "当前行：${activeEvent?.text ?: "（未选中字幕行）"}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = taskMode == AiAnalysisService.TaskMode.GRAMMAR_ANALYSIS,
                    onClick = { taskMode = AiAnalysisService.TaskMode.GRAMMAR_ANALYSIS },
                    label = { Text("语法与句意拆解") },
                )
                FilterChip(
                    selected = taskMode == AiAnalysisService.TaskMode.TRANSLATE_LINE,
                    onClick = { taskMode = AiAnalysisService.TaskMode.TRANSLATE_LINE },
                    label = { Text("字幕翻译（保标签）") },
                )
                FilterChip(
                    selected = taskMode == AiAnalysisService.TaskMode.POLISH_SUBTITLE,
                    onClick = { taskMode = AiAnalysisService.TaskMode.POLISH_SUBTITLE },
                    label = { Text("润色校对") },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !loading && activeEvent != null,
                    onClick = {
                        val ev = activeEvent ?: return@Button
                        viewModel.aiService.sessionApiKey = apiKey.trim()
                        loading = true
                        errorText = null
                        scope.launch {
                            val effectiveSettings = settings.copy(
                                aiProviderIndex = providerIdx,
                                aiBaseUrl = baseUrl,
                                aiModel = model,
                                aiTargetLang = targetLang,
                            )
                            val res = viewModel.aiService.runTask(
                                subtitleText = ev.text,
                                contextLines = contextNeighbors,
                                mode = taskMode,
                                settings = effectiveSettings,
                                overrideApiKey = apiKey,
                            )
                            loading = false
                            res.onSuccess { outputText = it }
                                .onFailure { errorText = it.message ?: "请求失败" }
                        }
                    },
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (loading) "正在请求 AI…" else "开始分析")
                }

                if (outputText.isNotBlank() && activeEvent != null) {
                    OutlinedButton(
                        onClick = {
                            viewModel.updateEventText(activeEvent.id, outputText.lineSequence().first().trim())
                            viewModel.postBanner("已将 AI 结果写入当前字幕行（可撤销）")
                        },
                    ) { Text("一键写入当前行") }
                }
            }

            errorText?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (outputText.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
                    Text(
                        outputText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SgmyCheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun StyleFilterDropdown(
    styles: List<String>,
    current: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (String) -> Unit,
) {
    Box {
        OutlinedButton(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (current.isEmpty()) "样式过滤：全部样式" else "样式过滤：$current")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            styles.forEach { st ->
                DropdownMenuItem(
                    text = { Text(if (st.isEmpty()) "全部样式" else st) },
                    onClick = { onPick(st) },
                )
            }
        }
    }
}
