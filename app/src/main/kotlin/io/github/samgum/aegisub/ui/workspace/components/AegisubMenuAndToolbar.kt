package io.github.samgum.aegisub.ui.workspace.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.data.settings.TagDisplayMode
import io.github.samgum.aegisub.ui.adaptive.AdaptiveWindowState
import io.github.samgum.aegisub.ui.workspace.WorkspaceDisplayPreset

/**
 * 统一工作台动作路由枚举。
 *
 * @author 伤感咩吖
 */
enum class WorkspaceDialogType {
    EXPORT,
    PROPERTIES,
    FONTS_COLLECTOR,
    HISTORY_SNAPSHOTS,
    BOOKMARKS,
    FIND_REPLACE,
    PASTE_OVER,
    SELECT_LINES,
    STYLE_REPLACE,
    SORT_LINES,
    SHIFT_TIMES,
    TIMING_POST_PROCESS,
    KANJI_TIMER,
    FRAMERATE_CONVERT,
    RESAMPLE_RESOLUTION,
    STYLING_ASSISTANT,
    TRANSLATION_ASSISTANT,
    KARAOKE_GENERATOR,
    SGMY_FIX_COMMON_ERRORS,
    SGMY_LYRIC_SCROLL,
    SGMY_CHINESE_CONVERT,
    SGMY_TEXT_CLEANUP,
    SGMY_PAIR_CHECK,
    SGMY_FURIGANA,
    SGMY_STITCH_TIMINGS,
    SGMY_STYLE_OVERLAP,
    SGMY_AI_ANALYSIS,
}

/**
 * 平板 / 折叠全展 / DeX 桌面级菜单栏与快捷图标栏（自然中文命名 + 右对齐快捷键提示 + 鼠标手型光标）。
 */
@Composable
fun DesktopMenuAndToolbar(
    projectName: String,
    adaptiveState: AdaptiveWindowState,
    canUndo: Boolean,
    canRedo: Boolean,
    tagDisplayMode: TagDisplayMode,
    displayPreset: WorkspaceDisplayPreset,
    hasMedia: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPickVideo: () -> Unit,
    onCloseVideo: () -> Unit,
    onImportKeyframes: () -> Unit,
    onExportKeyframes: () -> Unit,
    onOpenStyleManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onCycleTagMode: () -> Unit,
    onSetDisplayPreset: (WorkspaceDisplayPreset) -> Unit,
    onOpenDialog: (WorkspaceDialogType) -> Unit,
    onQuickSnapshot: () -> Unit,
    onInsertBefore: () -> Unit,
    onInsertAfter: () -> Unit,
    onDuplicateLines: () -> Unit,
    onSplitLine: () -> Unit,
    onSplitByKaraoke: () -> Unit,
    onJoinConcat: () -> Unit,
    onJoinKeepFirst: () -> Unit,
    onJoinKaraoke: () -> Unit,
    onRecombine: () -> Unit,
    onMakeContinuousEnd: () -> Unit,
    onMakeContinuousStart: () -> Unit,
    onCopyLines: () -> Unit,
    onPasteLines: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteEmpty: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 第一行：桌面菜单栏（简洁无冗余字母括号，快捷键右对齐显示）
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp).pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                VerticalDivider(Modifier.height(16.dp).padding(horizontal = 4.dp))

                MenuDropdownButton("文件") { dismiss ->
                    ShortcutMenuItem("保存快照", "Ctrl+S") { dismiss(); onQuickSnapshot() }
                    ShortcutMenuItem("历史版本管理…") { dismiss(); onOpenDialog(WorkspaceDialogType.HISTORY_SNAPSHOTS) }
                    ShortcutMenuItem("导出字幕与歌词…", "Ctrl+E") { dismiss(); onOpenDialog(WorkspaceDialogType.EXPORT) }
                    HorizontalDivider()
                    ShortcutMenuItem("脚本属性…") { dismiss(); onOpenDialog(WorkspaceDialogType.PROPERTIES) }
                    ShortcutMenuItem("字体使用检查…") { dismiss(); onOpenDialog(WorkspaceDialogType.FONTS_COLLECTOR) }
                    HorizontalDivider()
                    ShortcutMenuItem("返回工程列表") { dismiss(); onBack() }
                }

                MenuDropdownButton("编辑") { dismiss ->
                    ShortcutMenuItem("撤销", "Ctrl+Z", enabled = canUndo) { dismiss(); onUndo() }
                    ShortcutMenuItem("重做", "Ctrl+Y", enabled = canRedo) { dismiss(); onRedo() }
                    HorizontalDivider()
                    ShortcutMenuItem("复制选中行", "Ctrl+Shift+C") { dismiss(); onCopyLines() }
                    ShortcutMenuItem("粘贴字幕行", "Ctrl+Shift+V") { dismiss(); onPasteLines() }
                    ShortcutMenuItem("粘贴覆盖文本…") { dismiss(); onOpenDialog(WorkspaceDialogType.PASTE_OVER) }
                    HorizontalDivider()
                    ShortcutMenuItem("查找与替换…", "Ctrl+F") { dismiss(); onOpenDialog(WorkspaceDialogType.FIND_REPLACE) }
                }

                MenuDropdownButton("字幕") { dismiss ->
                    ShortcutMenuItem("样式管理器…") { dismiss(); onOpenStyleManager() }
                    ShortcutMenuItem("样式助手…") { dismiss(); onOpenDialog(WorkspaceDialogType.STYLING_ASSISTANT) }
                    ShortcutMenuItem("翻译助手…") { dismiss(); onOpenDialog(WorkspaceDialogType.TRANSLATION_ASSISTANT) }
                    ShortcutMenuItem("批量替换样式…") { dismiss(); onOpenDialog(WorkspaceDialogType.STYLE_REPLACE) }
                    ShortcutMenuItem("分辨率重采样…") { dismiss(); onOpenDialog(WorkspaceDialogType.RESAMPLE_RESOLUTION) }
                    HorizontalDivider()
                    ShortcutMenuItem("在上方插入行", "Ctrl+I") { dismiss(); onInsertBefore() }
                    ShortcutMenuItem("在下方插入行", "Ctrl+Shift+I") { dismiss(); onInsertAfter() }
                    ShortcutMenuItem("复制行", "Ctrl+D") { dismiss(); onDuplicateLines() }
                    ShortcutMenuItem("分割当前行", "Ctrl+Shift+K") { dismiss(); onSplitLine() }
                    ShortcutMenuItem("按卡拉OK音节拆分") { dismiss(); onSplitByKaraoke() }
                    HorizontalDivider()
                    ShortcutMenuItem("合并行（拼接文本）", "Ctrl+Shift+J") { dismiss(); onJoinConcat() }
                    ShortcutMenuItem("合并行（保留首句）", "Ctrl+J") { dismiss(); onJoinKeepFirst() }
                    ShortcutMenuItem("合并为卡拉OK") { dismiss(); onJoinKaraoke() }
                    ShortcutMenuItem("双行交叉重组") { dismiss(); onRecombine() }
                    HorizontalDivider()
                    ShortcutMenuItem("排序字幕行…") { dismiss(); onOpenDialog(WorkspaceDialogType.SORT_LINES) }
                    ShortcutMenuItem("按条件选择行…") { dismiss(); onOpenDialog(WorkspaceDialogType.SELECT_LINES) }
                    ShortcutMenuItem("全选所有行", "Ctrl+Shift+A") { dismiss(); onSelectAll() }
                    ShortcutMenuItem("删除空白行") { dismiss(); onDeleteEmpty() }
                }

                MenuDropdownButton("时间轴") { dismiss ->
                    ShortcutMenuItem("平移时间…") { dismiss(); onOpenDialog(WorkspaceDialogType.SHIFT_TIMES) }
                    ShortcutMenuItem("时间后处理（前后导 / 去重叠）…") { dismiss(); onOpenDialog(WorkspaceDialogType.TIMING_POST_PROCESS) }
                    ShortcutMenuItem("时间缝合器…") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_STITCH_TIMINGS) }
                    ShortcutMenuItem("同样式重叠检查…") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_STYLE_OVERLAP) }
                    HorizontalDivider()
                    ShortcutMenuItem("使时间连续（延长前句结束）") { dismiss(); onMakeContinuousEnd() }
                    ShortcutMenuItem("使时间连续（提前下句开始）") { dismiss(); onMakeContinuousStart() }
                    HorizontalDivider()
                    ShortcutMenuItem("帧率转换…") { dismiss(); onOpenDialog(WorkspaceDialogType.FRAMERATE_CONVERT) }
                    ShortcutMenuItem("生成卡拉OK音节…") { dismiss(); onOpenDialog(WorkspaceDialogType.KARAOKE_GENERATOR) }
                    ShortcutMenuItem("汉字卡拉OK计时复制…") { dismiss(); onOpenDialog(WorkspaceDialogType.KANJI_TIMER) }
                    ShortcutMenuItem("时间书签…") { dismiss(); onOpenDialog(WorkspaceDialogType.BOOKMARKS) }
                }

                MenuDropdownButton("音视频") { dismiss ->
                    ShortcutMenuItem("打开视频或音频文件…") { dismiss(); onPickVideo() }
                    if (hasMedia) {
                        ShortcutMenuItem("关闭当前媒体") { dismiss(); onCloseVideo() }
                    }
                    HorizontalDivider()
                    ShortcutMenuItem("导入关键帧文件…") { dismiss(); onImportKeyframes() }
                    ShortcutMenuItem("导出关键帧文件…") { dismiss(); onExportKeyframes() }
                }

                MenuDropdownButton("工具") { dismiss ->
                    ShortcutMenuItem("修复常见错误…", "Ctrl+Shift+F") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS) }
                    ShortcutMenuItem("滚动歌词生成器…", "Ctrl+Shift+L") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_LYRIC_SCROLL) }
                    HorizontalDivider()
                    ShortcutMenuItem("简繁中文转换…") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_CHINESE_CONVERT) }
                    ShortcutMenuItem("字幕文本清理…") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_TEXT_CLEANUP) }
                    ShortcutMenuItem("配对标点检查…") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_PAIR_CHECK) }
                    ShortcutMenuItem("日语假名注音…") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_FURIGANA) }
                    HorizontalDivider()
                    ShortcutMenuItem("AI 字幕分析与翻译…") { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_AI_ANALYSIS) }
                }

                MenuDropdownButton("视图") { dismiss ->
                    ShortcutMenuItem(if (displayPreset == WorkspaceDisplayPreset.FULL) "✓ 完整工作台" else "完整工作台") {
                        dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.FULL)
                    }
                    ShortcutMenuItem(if (displayPreset == WorkspaceDisplayPreset.VIDEO_SUBS) "✓ 视频 + 字幕" else "视频 + 字幕") {
                        dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.VIDEO_SUBS)
                    }
                    ShortcutMenuItem(if (displayPreset == WorkspaceDisplayPreset.AUDIO_SUBS) "✓ 音频 + 字幕" else "音频 + 字幕") {
                        dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.AUDIO_SUBS)
                    }
                    ShortcutMenuItem(if (displayPreset == WorkspaceDisplayPreset.SUBS_ONLY) "✓ 仅字幕列表" else "仅字幕列表") {
                        dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.SUBS_ONLY)
                    }
                    HorizontalDivider()
                    ShortcutMenuItem("切换网格标签显示", "Ctrl+Shift+T") { dismiss(); onCycleTagMode() }
                    ShortcutMenuItem("偏好与热键设置…") { dismiss(); onOpenSettings() }
                    ShortcutMenuItem("关于 Aegisub…") { dismiss(); onOpenAbout() }
                }
            }

            HorizontalDivider()

            // 第二行：清爽的快捷图标工具条
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ToolbarIconBtn(Icons.AutoMirrored.Filled.Undo, "撤销", enabled = canUndo, onClick = onUndo)
                ToolbarIconBtn(Icons.AutoMirrored.Filled.Redo, "重做", enabled = canRedo, onClick = onRedo)
                ToolbarDivider()
                ToolbarIconBtn(Icons.Filled.Movie, "打开音视频", onClick = onPickVideo)
                ToolbarIconBtn(Icons.Filled.FileDownload, "导出文件") { onOpenDialog(WorkspaceDialogType.EXPORT) }
                ToolbarIconBtn(Icons.Filled.History, "历史快照") { onOpenDialog(WorkspaceDialogType.HISTORY_SNAPSHOTS) }
                ToolbarDivider()
                ToolbarIconBtn(Icons.Filled.Palette, "样式管理器", onClick = onOpenStyleManager)
                ToolbarIconBtn(Icons.Filled.Tune, "脚本属性") { onOpenDialog(WorkspaceDialogType.PROPERTIES) }
                ToolbarIconBtn(Icons.Filled.FontDownload, "字体检查") { onOpenDialog(WorkspaceDialogType.FONTS_COLLECTOR) }
                ToolbarIconBtn(Icons.Filled.AspectRatio, "分辨率重采样") { onOpenDialog(WorkspaceDialogType.RESAMPLE_RESOLUTION) }
                ToolbarDivider()
                ToolbarIconBtn(Icons.Filled.FindReplace, "查找替换") { onOpenDialog(WorkspaceDialogType.FIND_REPLACE) }
                ToolbarIconBtn(Icons.Filled.Schedule, "平移时间") { onOpenDialog(WorkspaceDialogType.SHIFT_TIMES) }
                ToolbarIconBtn(Icons.Filled.SelectAll, "按条件选行") { onOpenDialog(WorkspaceDialogType.SELECT_LINES) }
                ToolbarIconBtn(Icons.Filled.ContentPaste, "粘贴覆盖") { onOpenDialog(WorkspaceDialogType.PASTE_OVER) }
                ToolbarIconBtn(Icons.Filled.Translate, "翻译助手") { onOpenDialog(WorkspaceDialogType.TRANSLATION_ASSISTANT) }
                ToolbarIconBtn(Icons.Filled.BookmarkBorder, "书签") { onOpenDialog(WorkspaceDialogType.BOOKMARKS) }
                ToolbarDivider()
                ToolbarIconBtn(Icons.Filled.AutoFixHigh, "修复常见错误", tintPrimary = true) {
                    onOpenDialog(WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS)
                }
                ToolbarIconBtn(Icons.Filled.MusicNote, "滚动歌词生成器", tintPrimary = true) {
                    onOpenDialog(WorkspaceDialogType.SGMY_LYRIC_SCROLL)
                }
                ToolbarIconBtn(Icons.Filled.Spellcheck, "中日韩文本工具") {
                    onOpenDialog(WorkspaceDialogType.SGMY_CHINESE_CONVERT)
                }
                ToolbarIconBtn(Icons.Filled.AutoAwesome, "AI 字幕助手", tintPrimary = true) {
                    onOpenDialog(WorkspaceDialogType.SGMY_AI_ANALYSIS)
                }

                Spacer(Modifier.weight(1f))

                TextButton(
                    onClick = onCycleTagMode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp).pointerHoverIcon(PointerIcon.Hand),
                ) {
                    val badge = when (tagDisplayMode) {
                        TagDisplayMode.SHOW_TAGS -> "显示标签"
                        TagDisplayMode.SIMPLIFY_TAGS -> "☼ 简化标签"
                        TagDisplayMode.HIDE_TAGS -> "隐藏标签"
                    }
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(badge, style = MaterialTheme.typography.labelSmall)
                }
                ToolbarIconBtn(Icons.Filled.Settings, "设置", onClick = onOpenSettings)
            }
            HorizontalDivider()
        }
    }
}

/**
 * 手机 / 紧凑分屏简洁顶栏：仅保留最核心的返回、撤销/重做、加载媒体与「工具箱」面板入口。
 */
@Composable
fun CompactStudioTopBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    tagDisplayMode: TagDisplayMode,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPickVideo: () -> Unit,
    onOpenStyleManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleTagMode: () -> Unit,
    onOpenDialog: (WorkspaceDialogType) -> Unit,
) {
    var toolboxOpen by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 1.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
                }
                IconButton(onClick = onPickVideo) {
                    Icon(Icons.Filled.Movie, contentDescription = "打开音视频")
                }
                IconButton(onClick = { toolboxOpen = true }) {
                    Icon(Icons.Filled.Apps, contentDescription = "工具箱")
                }
            }
            HorizontalDivider()
        }
    }

    if (toolboxOpen) {
        StudioToolboxSheet(
            tagDisplayMode = tagDisplayMode,
            onDismiss = { toolboxOpen = false },
            onOpenStyleManager = { toolboxOpen = false; onOpenStyleManager() },
            onOpenSettings = { toolboxOpen = false; onOpenSettings() },
            onCycleTagMode = { onCycleTagMode() },
            onOpenDialog = { type -> toolboxOpen = false; onOpenDialog(type) },
        )
    }
}

/**
 * 触控友好的分类网格工具抽屉（取代冗长拥挤的下拉菜单）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioToolboxSheet(
    tagDisplayMode: TagDisplayMode,
    onDismiss: () -> Unit,
    onOpenStyleManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleTagMode: () -> Unit,
    onOpenDialog: (WorkspaceDialogType) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("字幕工具箱", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("设置与热键")
                }
            }

            ToolboxGroupTitle("智能修复与创意生成")
            ToolboxTwoColumnRow(
                "修复常见错误" to { onOpenDialog(WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS) },
                "滚动歌词生成器" to { onOpenDialog(WorkspaceDialogType.SGMY_LYRIC_SCROLL) },
            )
            ToolboxTwoColumnRow(
                "AI 语法与润色" to { onOpenDialog(WorkspaceDialogType.SGMY_AI_ANALYSIS) },
                "生成卡拉OK音节" to { onOpenDialog(WorkspaceDialogType.KARAOKE_GENERATOR) },
            )

            ToolboxGroupTitle("中日韩文本校对")
            ToolboxTwoColumnRow(
                "简繁中文转换" to { onOpenDialog(WorkspaceDialogType.SGMY_CHINESE_CONVERT) },
                "字幕文本清理" to { onOpenDialog(WorkspaceDialogType.SGMY_TEXT_CLEANUP) },
            )
            ToolboxTwoColumnRow(
                "配对标点检查" to { onOpenDialog(WorkspaceDialogType.SGMY_PAIR_CHECK) },
                "日语假名注音" to { onOpenDialog(WorkspaceDialogType.SGMY_FURIGANA) },
            )

            ToolboxGroupTitle("时间轴与批量编辑")
            ToolboxTwoColumnRow(
                "查找与替换" to { onOpenDialog(WorkspaceDialogType.FIND_REPLACE) },
                "平移时间轴" to { onOpenDialog(WorkspaceDialogType.SHIFT_TIMES) },
            )
            ToolboxTwoColumnRow(
                "时间缝合与重叠检查" to { onOpenDialog(WorkspaceDialogType.SGMY_STITCH_TIMINGS) },
                "时间后处理器" to { onOpenDialog(WorkspaceDialogType.TIMING_POST_PROCESS) },
            )
            ToolboxTwoColumnRow(
                "按条件批量选行" to { onOpenDialog(WorkspaceDialogType.SELECT_LINES) },
                "粘贴覆盖文本" to { onOpenDialog(WorkspaceDialogType.PASTE_OVER) },
            )

            ToolboxGroupTitle("样式、画幅与导出")
            ToolboxTwoColumnRow(
                "样式管理器" to onOpenStyleManager,
                "样式 / 翻译助手" to { onOpenDialog(WorkspaceDialogType.TRANSLATION_ASSISTANT) },
            )
            ToolboxTwoColumnRow(
                "分辨率重采样" to { onOpenDialog(WorkspaceDialogType.RESAMPLE_RESOLUTION) },
                "脚本属性与字体" to { onOpenDialog(WorkspaceDialogType.PROPERTIES) },
            )
            val tagLabel = when (tagDisplayMode) {
                TagDisplayMode.SHOW_TAGS -> "标签：完整显示"
                TagDisplayMode.SIMPLIFY_TAGS -> "标签：简化为 ☼"
                TagDisplayMode.HIDE_TAGS -> "标签：已隐藏"
            }
            ToolboxTwoColumnRow(
                tagLabel to onCycleTagMode,
                "历史版本快照" to { onOpenDialog(WorkspaceDialogType.HISTORY_SNAPSHOTS) },
            )
            ToolboxTwoColumnRow(
                "导出字幕与歌词" to { onOpenDialog(WorkspaceDialogType.EXPORT) },
                "帧率转换 / 书签" to { onOpenDialog(WorkspaceDialogType.FRAMERATE_CONVERT) },
            )
        }
    }
}

@Composable
private fun ToolboxGroupTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToolboxTwoColumnRow(
    left: Pair<String, () -> Unit>,
    right: Pair<String, () -> Unit>,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f).height(44.dp).clickable(onClick = left.second),
        ) {
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(left.first, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f).height(44.dp).clickable(onClick = right.second),
        ) {
            Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(right.first, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ShortcutMenuItem(
    title: String,
    shortcut: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (shortcut != null) {
                    Spacer(Modifier.width(20.dp))
                    Text(
                        shortcut,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun MenuDropdownButton(
    title: String,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.height(30.dp).pointerHoverIcon(PointerIcon.Hand),
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

@Composable
private fun ToolbarIconBtn(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean = true,
    tintPrimary: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon.Hand),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
                tintPrimary -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
