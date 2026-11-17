package io.github.samgum.aegisub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.R
import io.github.samgum.aegisub.data.hotkeys.KeyCombo
import io.github.samgum.aegisub.data.hotkeys.rememberHotkeyEditor
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.TagDisplayMode
import io.github.samgum.aegisub.data.settings.ThemeMode
import io.github.samgum.aegisub.domain.edit.HotkeyAction
import io.github.samgum.aegisub.domain.format.TimePrecision

/**
 * 全局偏好与热键中心（覆盖自适应折叠屏布局、SGMY 溢出检测、HDR 预览、打轴前后导与全部 34 项热键重绑定）。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s by viewModel.userSettings.collectAsStateWithLifecycle()
    val hotkeyVM = rememberHotkeyEditor()
    val hotkeys by hotkeyVM.hotkeys.collectAsStateWithLifecycle()
    var capturingAction by remember { mutableStateOf<HotkeyAction?>(null) }

    var leadInInput by remember(s.defaultLeadInMs) { mutableStateOf(s.defaultLeadInMs.toString()) }
    var leadOutInput by remember(s.defaultLeadOutMs) { mutableStateOf(s.defaultLeadOutMs.toString()) }
    var cpsInput by remember(s.cpsWarningThreshold) { mutableStateOf(s.cpsWarningThreshold.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("自适应与折叠屏布局姿态")
            OptionRow("智能自适应（自动检测手机 / 平板 / 折叠悬停 Tabletop / 书本双页 Book）", s.layoutMode == LayoutMode.AUTO) {
                viewModel.setLayoutMode(LayoutMode.AUTO)
            }
            OptionRow("强制手机紧凑单手布局", s.layoutMode == LayoutMode.COMPACT) {
                viewModel.setLayoutMode(LayoutMode.COMPACT)
            }
            OptionRow("强制横屏双栏布局", s.layoutMode == LayoutMode.EXPANDED) {
                viewModel.setLayoutMode(LayoutMode.EXPANDED)
            }
            OptionRow("强制平板/桌面 Aegisub 经典三区专业版", s.layoutMode == LayoutMode.DESKTOP_PRO) {
                viewModel.setLayoutMode(LayoutMode.DESKTOP_PRO)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("SGMY 增强与视频预览")
            SwitchRow("实时高亮超出视频画幅的溢出字幕 (SGMY Overflow)", s.highlightOverflow) {
                viewModel.setHighlightOverflow(it)
            }
            SwitchRow("4K HDR / 杜比视界 1080p 预览降采样提速 (SGMY HDR)", s.hdrPreviewOptimize) {
                viewModel.setHdrPreviewOptimize(it)
            }
            SwitchRow("编辑框显示 SGMY ✨ AI 分析入口按钮", s.aiEnabled) {
                viewModel.setAiEnabled(it)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = cpsInput,
                    onValueChange = { v ->
                        cpsInput = v
                        v.toIntOrNull()?.coerceIn(10, 60)?.let(viewModel::setCpsWarningThreshold)
                    },
                    label = { Text("CPS 语速预警阈值") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("字幕网格标签模式")
            OptionRow("简化显示覆盖标签为 ☼（桌面默认推荐）", s.tagDisplayMode == TagDisplayMode.SIMPLIFY_TAGS) {
                viewModel.setTagDisplayMode(TagDisplayMode.SIMPLIFY_TAGS)
            }
            OptionRow("完整显示全部 {...} 标签", s.tagDisplayMode == TagDisplayMode.SHOW_TAGS) {
                viewModel.setTagDisplayMode(TagDisplayMode.SHOW_TAGS)
            }
            OptionRow("隐藏全部标签（仅显示可视台词）", s.tagDisplayMode == TagDisplayMode.HIDE_TAGS) {
                viewModel.setTagDisplayMode(TagDisplayMode.HIDE_TAGS)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("音频打轴与关键帧吸附")
            SwitchRow("自动吸附到视频关键帧 (Snap to Keyframes)", s.snapToKeyframes) {
                viewModel.updateTimingPrefs(snapKeyframes = it)
            }
            SwitchRow("按下「设止 ]」后自动跳转到下一行", s.autoNextOnCommit) {
                viewModel.updateTimingPrefs(autoNext = it)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = leadInInput,
                    onValueChange = { v ->
                        leadInInput = v
                        v.toIntOrNull()?.coerceIn(0, 2000)?.let { viewModel.updateTimingPrefs(leadInMs = it) }
                    },
                    label = { Text("默认前导 Lead-In (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = leadOutInput,
                    onValueChange = { v ->
                        leadOutInput = v
                        v.toIntOrNull()?.coerceIn(0, 2000)?.let { viewModel.updateTimingPrefs(leadOutMs = it) }
                    },
                    label = { Text("默认后导 Lead-Out (ms)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_theme))
            OptionRow(stringResource(R.string.settings_theme_system), s.themeMode == ThemeMode.SYSTEM) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
            OptionRow(stringResource(R.string.settings_theme_dark), s.themeMode == ThemeMode.DARK) { viewModel.setThemeMode(ThemeMode.DARK) }
            OptionRow(stringResource(R.string.settings_theme_light), s.themeMode == ThemeMode.LIGHT) { viewModel.setThemeMode(ThemeMode.LIGHT) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_precision))
            OptionRow(stringResource(R.string.settings_precision_auto), s.exportPrecision == TimePrecision.AUTO) { viewModel.setExportPrecision(TimePrecision.AUTO) }
            OptionRow(stringResource(R.string.settings_precision_two), s.exportPrecision == TimePrecision.TWO_MS) { viewModel.setExportPrecision(TimePrecision.TWO_MS) }
            OptionRow(stringResource(R.string.settings_precision_three), s.exportPrecision == TimePrecision.THREE_MS) { viewModel.setExportPrecision(TimePrecision.THREE_MS) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_language))
            OptionRow(stringResource(R.string.settings_lang_system), s.langCode == "system") { viewModel.setLangCode("system") }
            OptionRow(stringResource(R.string.settings_lang_zh), s.langCode == "zh") { viewModel.setLangCode("zh") }
            OptionRow(stringResource(R.string.settings_lang_en), s.langCode == "en") { viewModel.setLangCode("en") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("外接键盘 / 平板键盘套热键绑定")
            HotkeyAction.entries.forEach { action ->
                val combo = hotkeys[action]
                ListItem(
                    headlineContent = { Text(action.label()) },
                    supportingContent = { Text(combo?.display() ?: "—") },
                    modifier = Modifier.clickable { capturingAction = action },
                )
            }
            TextButton(onClick = { hotkeyVM.resetAll() }, modifier = Modifier.padding(start = 12.dp)) {
                Text("恢复全部默认桌面热键")
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
        }
    }

    capturingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { capturingAction = null },
            title = { Text("重新绑定热键") },
            text = {
                Box(
                    Modifier.fillMaxWidth().onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) {
                            hotkeyVM.setBinding(
                                action,
                                KeyCombo(e.isCtrlPressed, e.isShiftPressed, e.isAltPressed, e.key.keyCode),
                            )
                            capturingAction = null
                            true
                        } else false
                    }.padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("请在外接键盘按下「${action.label()}」的新快捷键组合…") }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { capturingAction = null }) { Text("取消") } },
        )
    }
}

private fun HotkeyAction.label(): String = when (this) {
    HotkeyAction.UNDO -> "撤销"
    HotkeyAction.REDO -> "重做"
    HotkeyAction.SAVE -> "快速保存历史快照"
    HotkeyAction.EXPORT -> "导出转换字幕"
    HotkeyAction.FIND_REPLACE -> "查找与替换"
    HotkeyAction.DUPLICATE_LINE -> "复制选中行"
    HotkeyAction.DELETE_LINE -> "删除选中行"
    HotkeyAction.SPLIT_LINE -> "分割当前行"
    HotkeyAction.JOIN_KEEP_FIRST -> "合并行（保留首句）"
    HotkeyAction.JOIN_CONCAT -> "合并行（拼接文本）"
    HotkeyAction.JOIN_KARAOKE -> "合并行（转为卡拉OK \\k）"
    HotkeyAction.MOVE_LINE_UP -> "上移选中块"
    HotkeyAction.MOVE_LINE_DOWN -> "下移选中块"
    HotkeyAction.INSERT_BEFORE -> "上方插入行"
    HotkeyAction.INSERT_AFTER -> "下方插入行"
    HotkeyAction.COPY_LINES -> "复制为 ASS 文本"
    HotkeyAction.PASTE_LINES -> "从剪贴板粘贴行"
    HotkeyAction.SELECT_ALL -> "全选所有行"
    HotkeyAction.SELECT_PREV -> "上一行"
    HotkeyAction.SELECT_NEXT -> "下一行"
    HotkeyAction.COMMIT_AND_NEXT -> "提交当前行并前进 [G]"
    HotkeyAction.PLAY_PAUSE -> "播放 / 暂停"
    HotkeyAction.PLAY_LINE -> "播放当前行片段 [R]"
    HotkeyAction.SEEK_BACK -> "后退 5 秒"
    HotkeyAction.SEEK_FORWARD -> "前进 5 秒"
    HotkeyAction.FRAME_BACK -> "逐帧后退"
    HotkeyAction.FRAME_FORWARD -> "逐帧前进"
    HotkeyAction.SPEED_DOWN -> "减速播放 (-0.25x)"
    HotkeyAction.SPEED_UP -> "加速播放 (+0.25x)"
    HotkeyAction.SPEED_RESET -> "重置 1.0x 原速"
    HotkeyAction.SET_START_TO_POS -> "设起始时间到当前画面"
    HotkeyAction.SET_END_TO_POS -> "设结束时间到当前画面"
    HotkeyAction.ADD_LEAD_IN -> "添加前导 Lead-In"
    HotkeyAction.ADD_LEAD_OUT -> "添加后导 Lead-Out"
    HotkeyAction.FIX_COMMON_ERRORS -> "SGMY 修复常见错误"
    HotkeyAction.LYRIC_SCROLL -> "SGMY 音乐滚动歌词生成"
    HotkeyAction.CYCLE_TAG_HIDING -> "切换网格标签隐藏模式 (☼)"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}
