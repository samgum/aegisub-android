package io.github.samgum.aegisub.ui.workspace.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.data.settings.TagDisplayMode
import io.github.samgum.aegisub.ui.adaptive.AdaptiveWindowState
import io.github.samgum.aegisub.ui.workspace.WorkspaceDisplayPreset

/**
 * 统一工作台动作路由枚举（覆盖桌面 `samgum/Aegisub` 全部菜单与工具栏动作）。
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
    // SGMY 独家功能弹窗
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
 * 桌面级顶栏菜单条 + 图标工具栏（平板 / 折叠全展 / DeX 使用完整菜单栏+图标条，手机使用紧凑单行顶栏+全菜单抽屉）。
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
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 第一行：经典 Aegisub 桌面菜单栏（File / Edit / Subtitle / Timing / Video / Audio / SGMY Tools / View / Help）
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回项目列表", modifier = Modifier.size(18.dp))
                }
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                VerticalDivider(Modifier.height(18.dp).padding(horizontal = 4.dp))

                MenuDropdownButton("文件(F)") { dismiss ->
                    DropdownMenuItem(text = { Text("保存历史快照") }, onClick = { dismiss(); onQuickSnapshot() })
                    DropdownMenuItem(text = { Text("历史快照管理…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.HISTORY_SNAPSHOTS) })
                    DropdownMenuItem(text = { Text("导出转换字幕 (ASS/SRT/VTT/TTML/LRC)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.EXPORT) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("脚本属性 (Script Info)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.PROPERTIES) })
                    DropdownMenuItem(text = { Text("字体检查与收集 (Fonts Collector)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.FONTS_COLLECTOR) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("返回工程列表") }, onClick = { dismiss(); onBack() })
                }

                MenuDropdownButton("编辑(E)") { dismiss ->
                    DropdownMenuItem(text = { Text("撤销 (Ctrl+Z)") }, enabled = canUndo, onClick = { dismiss(); onUndo() })
                    DropdownMenuItem(text = { Text("重做 (Ctrl+Y)") }, enabled = canRedo, onClick = { dismiss(); onRedo() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("复制选中行") }, onClick = { dismiss(); onCopyLines() })
                    DropdownMenuItem(text = { Text("粘贴行") }, onClick = { dismiss(); onPasteLines() })
                    DropdownMenuItem(text = { Text("粘贴覆盖 (Paste Over)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.PASTE_OVER) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("查找与替换 (Ctrl+F)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.FIND_REPLACE) })
                }

                MenuDropdownButton("字幕(S)") { dismiss ->
                    DropdownMenuItem(text = { Text("样式管理器 (Styles Manager)…") }, onClick = { dismiss(); onOpenStyleManager() })
                    DropdownMenuItem(text = { Text("样式助手 (Styling Assistant)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.STYLING_ASSISTANT) })
                    DropdownMenuItem(text = { Text("翻译助手 (Translation Assistant)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.TRANSLATION_ASSISTANT) })
                    DropdownMenuItem(text = { Text("样式批量替换…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.STYLE_REPLACE) })
                    DropdownMenuItem(text = { Text("分辨率重采样 (Resample Resolution)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.RESAMPLE_RESOLUTION) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("前插新行") }, onClick = { dismiss(); onInsertBefore() })
                    DropdownMenuItem(text = { Text("后插新行") }, onClick = { dismiss(); onInsertAfter() })
                    DropdownMenuItem(text = { Text("复制当前/选中行") }, onClick = { dismiss(); onDuplicateLines() })
                    DropdownMenuItem(text = { Text("分割当前行") }, onClick = { dismiss(); onSplitLine() })
                    DropdownMenuItem(text = { Text("按卡拉OK音节拆分为多行") }, onClick = { dismiss(); onSplitByKaraoke() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("合并行（拼接文本）") }, onClick = { dismiss(); onJoinConcat() })
                    DropdownMenuItem(text = { Text("合并行（保留首句）") }, onClick = { dismiss(); onJoinKeepFirst() })
                    DropdownMenuItem(text = { Text("合并行（转为卡拉OK \\k）") }, onClick = { dismiss(); onJoinKaraoke() })
                    DropdownMenuItem(text = { Text("双行交叉重组 (Recombine)") }, onClick = { dismiss(); onRecombine() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("排序字幕行…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SORT_LINES) })
                    DropdownMenuItem(text = { Text("按条件选择行 (Select Lines)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SELECT_LINES) })
                    DropdownMenuItem(text = { Text("全选所有行") }, onClick = { dismiss(); onSelectAll() })
                    DropdownMenuItem(text = { Text("删除空白行") }, onClick = { dismiss(); onDeleteEmpty() })
                }

                MenuDropdownButton("时间轴(T)") { dismiss ->
                    DropdownMenuItem(text = { Text("平移时间 (Shift Times)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SHIFT_TIMES) })
                    DropdownMenuItem(text = { Text("时间后处理 (去重叠 / 前后导)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.TIMING_POST_PROCESS) })
                    DropdownMenuItem(text = { Text("帧率转换 (Change Framerate)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.FRAMERATE_CONVERT) })
                    DropdownMenuItem(text = { Text("汉字卡拉OK计时器 (Kanji Timer)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.KANJI_TIMER) })
                    DropdownMenuItem(text = { Text("生成卡拉OK音节 (\\k / \\kf)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.KARAOKE_GENERATOR) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("使时间连续（修改结束时间）") }, onClick = { dismiss(); onMakeContinuousEnd() })
                    DropdownMenuItem(text = { Text("使时间连续（修改开始时间）") }, onClick = { dismiss(); onMakeContinuousStart() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("书签管理 (Bookmarks)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.BOOKMARKS) })
                }

                MenuDropdownButton("视频/音频(V)") { dismiss ->
                    DropdownMenuItem(text = { Text("打开/更换视频或音频文件…") }, onClick = { dismiss(); onPickVideo() })
                    if (hasMedia) {
                        DropdownMenuItem(text = { Text("关闭当前媒体") }, onClick = { dismiss(); onCloseVideo() })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("导入关键帧文件 (# keyframe v1)…") }, onClick = { dismiss(); onImportKeyframes() })
                    DropdownMenuItem(text = { Text("导出当前关键帧文件…") }, onClick = { dismiss(); onExportKeyframes() })
                }

                MenuDropdownButton("SGMY工具(G)") { dismiss ->
                    DropdownMenuItem(
                        text = { Text("⚡ 修复常见错误 (Fix Common Errors)…", fontWeight = FontWeight.SemiBold) },
                        onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS) },
                    )
                    DropdownMenuItem(
                        text = { Text("🎵 音乐滚动歌词生成器 (Lyric Scroll)…", fontWeight = FontWeight.SemiBold) },
                        onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_LYRIC_SCROLL) },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("简繁中文互转 (ICU)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_CHINESE_CONVERT) })
                    DropdownMenuItem(text = { Text("字幕文本清理（标点/双空格）…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_TEXT_CLEANUP) })
                    DropdownMenuItem(text = { Text("配对标点符号检查…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_PAIR_CHECK) })
                    DropdownMenuItem(text = { Text("日语汉字假名注音 (Furigana)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_FURIGANA) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("时间缝合器 (Stitch Timings)…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_STITCH_TIMINGS) })
                    DropdownMenuItem(text = { Text("同样式时间重叠检查…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_STYLE_OVERLAP) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("✨ AI 字幕语法分析与润色…") }, onClick = { dismiss(); onOpenDialog(WorkspaceDialogType.SGMY_AI_ANALYSIS) })
                }

                MenuDropdownButton("视图(W)") { dismiss ->
                    DropdownMenuItem(text = { Text("完整视图（视频+音频+字幕）") }, onClick = { dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.FULL) })
                    DropdownMenuItem(text = { Text("视频 + 字幕视图") }, onClick = { dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.VIDEO_SUBS) })
                    DropdownMenuItem(text = { Text("音频打轴 + 字幕视图") }, onClick = { dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.AUDIO_SUBS) })
                    DropdownMenuItem(text = { Text("纯字幕专注视图") }, onClick = { dismiss(); onSetDisplayPreset(WorkspaceDisplayPreset.SUBS_ONLY) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("切换网格标签隐藏模式 (☼)") }, onClick = { dismiss(); onCycleTagMode() })
                    DropdownMenuItem(text = { Text("全局偏好与热键设置…") }, onClick = { dismiss(); onOpenSettings() })
                    DropdownMenuItem(text = { Text("关于 Aegisub SGMY…") }, onClick = { dismiss(); onOpenAbout() })
                }

                Spacer(Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        adaptiveState.postureBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            HorizontalDivider()

            // 第二行：桌面 Aegisub 快捷图标工具栏（含 SGMY 修复常见错误与滚动歌词按钮）
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ToolbarIconBtn(Icons.Filled.History, "历史快照") { onOpenDialog(WorkspaceDialogType.HISTORY_SNAPSHOTS) }
                ToolbarIconBtn(Icons.Filled.FileDownload, "导出转换") { onOpenDialog(WorkspaceDialogType.EXPORT) }
                ToolbarDivider()
                ToolbarIconBtn(Icons.AutoMirrored.Filled.Undo, "撤销", enabled = canUndo, onClick = onUndo)
                ToolbarIconBtn(Icons.AutoMirrored.Filled.Redo, "重做", enabled = canRedo, onClick = onRedo)
                ToolbarDivider()
                ToolbarIconBtn(Icons.Filled.Movie, "打开视频/音频", onClick = onPickVideo)
                ToolbarIconBtn(Icons.Filled.Palette, "样式管理器", onClick = onOpenStyleManager)
                ToolbarIconBtn(Icons.Filled.Tune, "脚本属性") { onOpenDialog(WorkspaceDialogType.PROPERTIES) }
                ToolbarIconBtn(Icons.Filled.FontDownload, "字体检查") { onOpenDialog(WorkspaceDialogType.FONTS_COLLECTOR) }
                ToolbarDivider()
                ToolbarIconBtn(Icons.Filled.FindReplace, "查找替换") { onOpenDialog(WorkspaceDialogType.FIND_REPLACE) }
                ToolbarIconBtn(Icons.Filled.Schedule, "平移时间") { onOpenDialog(WorkspaceDialogType.SHIFT_TIMES) }
                // SGMY 标志性工具栏按钮
                ToolbarIconBtn(Icons.Filled.AutoFixHigh, "SGMY 修复常见错误", tintPrimary = true) {
                    onOpenDialog(WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS)
                }
                ToolbarIconBtn(Icons.Filled.MusicNote, "SGMY 滚动歌词生成", tintPrimary = true) {
                    onOpenDialog(WorkspaceDialogType.SGMY_LYRIC_SCROLL)
                }
                ToolbarIconBtn(Icons.Filled.Spellcheck, "SGMY 中日韩文本工具") {
                    onOpenDialog(WorkspaceDialogType.SGMY_CHINESE_CONVERT)
                }
                ToolbarIconBtn(Icons.Filled.AutoAwesome, "SGMY AI 分析", tintPrimary = true) {
                    onOpenDialog(WorkspaceDialogType.SGMY_AI_ANALYSIS)
                }
                ToolbarDivider()
                ToolbarIconBtn(Icons.Filled.Translate, "翻译助手") { onOpenDialog(WorkspaceDialogType.TRANSLATION_ASSISTANT) }
                ToolbarIconBtn(Icons.Filled.AspectRatio, "重采样分辨率") { onOpenDialog(WorkspaceDialogType.RESAMPLE_RESOLUTION) }
                ToolbarIconBtn(Icons.Filled.SelectAll, "按条件选行") { onOpenDialog(WorkspaceDialogType.SELECT_LINES) }
                ToolbarIconBtn(Icons.Filled.ContentPaste, "粘贴覆盖") { onOpenDialog(WorkspaceDialogType.PASTE_OVER) }
                ToolbarIconBtn(Icons.Filled.BookmarkBorder, "书签") { onOpenDialog(WorkspaceDialogType.BOOKMARKS) }
                ToolbarDivider()
                TextButton(onClick = onCycleTagMode) {
                    val badge = when (tagDisplayMode) {
                        TagDisplayMode.SHOW_TAGS -> "{标签}"
                        TagDisplayMode.SIMPLIFY_TAGS -> "☼简化"
                        TagDisplayMode.HIDE_TAGS -> "纯文本"
                    }
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(badge, style = MaterialTheme.typography.labelSmall)
                }
                ToolbarIconBtn(Icons.Filled.Settings, "全局设置", onClick = onOpenSettings)
            }
            HorizontalDivider()
        }
    }
}

/**
 * 手机竖屏 / 紧凑姿态顶栏：保留高频一键按钮 + 完整 Aegisub 菜单聚合按钮，零功能阉割。
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
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
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
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "撤销")
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "重做")
                }
                IconButton(onClick = { onOpenDialog(WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS) }) {
                    Icon(
                        Icons.Filled.AutoFixHigh,
                        contentDescription = "修复常见错误",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onPickVideo) {
                    Icon(Icons.Filled.Movie, contentDescription = "挂载媒体")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "全部菜单")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("⚡ 修复常见错误 (SGMY)") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_FIX_COMMON_ERRORS) },
                        )
                        DropdownMenuItem(
                            text = { Text("🎵 音乐滚动歌词生成器 (SGMY)") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_LYRIC_SCROLL) },
                        )
                        DropdownMenuItem(
                            text = { Text("✨ AI 字幕语法分析与翻译 (SGMY)") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_AI_ANALYSIS) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("简繁中文互转…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_CHINESE_CONVERT) },
                        )
                        DropdownMenuItem(
                            text = { Text("字幕文本清理…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_TEXT_CLEANUP) },
                        )
                        DropdownMenuItem(
                            text = { Text("配对标点检查…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_PAIR_CHECK) },
                        )
                        DropdownMenuItem(
                            text = { Text("日语假名注音 (Furigana)…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_FURIGANA) },
                        )
                        DropdownMenuItem(
                            text = { Text("时间缝合 / 同样式重叠检查…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SGMY_STITCH_TIMINGS) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("样式管理器 (Styles Manager)…") },
                            onClick = { menuOpen = false; onOpenStyleManager() },
                        )
                        DropdownMenuItem(
                            text = { Text("查找与替换…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.FIND_REPLACE) },
                        )
                        DropdownMenuItem(
                            text = { Text("平移时间 / 时间后处理…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.SHIFT_TIMES) },
                        )
                        DropdownMenuItem(
                            text = { Text("分辨率重采样 / 脚本属性…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.PROPERTIES) },
                        )
                        DropdownMenuItem(
                            text = { Text("样式助手 / 翻译助手 / 汉字计时…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.TRANSLATION_ASSISTANT) },
                        )
                        DropdownMenuItem(
                            text = { Text("切换标签显示 (${tagDisplayMode.name})") },
                            onClick = { menuOpen = false; onCycleTagMode() },
                        )
                        DropdownMenuItem(
                            text = { Text("历史快照…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.HISTORY_SNAPSHOTS) },
                        )
                        DropdownMenuItem(
                            text = { Text("导出转换 (ASS/SRT/VTT/TTML/LRC)…") },
                            onClick = { menuOpen = false; onOpenDialog(WorkspaceDialogType.EXPORT) },
                        )
                        DropdownMenuItem(
                            text = { Text("全局设置与热键…") },
                            onClick = { menuOpen = false; onOpenSettings() },
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
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
            modifier = Modifier.height(30.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
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
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
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
            .padding(horizontal = 3.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
