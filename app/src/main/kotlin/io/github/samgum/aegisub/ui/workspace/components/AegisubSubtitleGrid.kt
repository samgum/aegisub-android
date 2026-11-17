package io.github.samgum.aegisub.ui.workspace.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.data.settings.TagDisplayMode
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.sgmy.SubtitleOverflowDetector
import io.github.samgum.aegisub.ui.workspace.WorkspaceViewModel

/**
 * 桌面级高密度 Aegisub 字幕网格（支持宽屏多列表格与手机紧凑双行卡片自适应）：
 * - 表头点击排序（层 / 开始 / 结束 / 样式 / 说话人 / 文本）
 * - 支持三种 ASS 标签显示模式（`{完整标签}` / `☼简化` / `隐藏标签`）
 * - 实时标记 **SGMY 字幕超出画幅红标 (`溢出`)**、**高 CPS 琥珀标**、**视频正在显示绿条**、**注释行斜体灰蓝底**
 * - 长按进入多选批量模式（复制/删除/上下移/区间连选/全选），点 `⋮` 或长按弹出 **完整桌面 Aegisub 网格右键菜单**！
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AegisubSubtitleGrid(
    events: List<AssEvent>,
    activeEventId: Long?,
    currentVisibleEventId: Long?,
    selectedIds: Set<Long>,
    tagDisplayMode: TagDisplayMode,
    overflowMap: Map<Long, SubtitleOverflowDetector.OverflowResult>,
    wideTableMode: Boolean,
    onSelectRow: (Long) -> Unit,
    onToggleSelectRow: (Long) -> Unit,
    onSelectRangeTo: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSort: (SortKey, SortOrder) -> Unit,
    onInsertBefore: (atVideoTime: Boolean) -> Unit,
    onInsertAfter: (atVideoTime: Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSplitMidpoint: () -> Unit,
    onSplitVideoTime: () -> Unit,
    onSplitKaraoke: () -> Unit,
    onJoin: (WorkspaceViewModel.JoinMode) -> Unit,
    onSwapTwo: () -> Unit,
    onMakeContinuous: (changeEnd: Boolean) -> Unit,
    onCopyLines: () -> Unit,
    onPasteLines: () -> Unit,
    onOpenPasteOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var sortKey by remember { mutableStateOf<SortKey?>(null) }
    var sortOrder by remember { mutableStateOf(SortOrder.ASCENDING) }
    var contextMenuEventId by remember { mutableStateOf<Long?>(null) }

    val inMultiSelect = selectedIds.isNotEmpty()

    // 切换选中行时，若当前行不在可见范围附近则平滑滚动到位
    LaunchedEffect(activeEventId) {
        if (activeEventId != null) {
            val idx = events.indexOfFirst { it.id == activeEventId }
            if (idx >= 0) {
                val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
                if (visibleIndices.isNotEmpty() && idx !in visibleIndices) {
                    listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
                }
            }
        }
    }

    fun triggerSort(key: SortKey) {
        val nextOrder = if (sortKey == key && sortOrder == SortOrder.ASCENDING) {
            SortOrder.DESCENDING
        } else {
            SortOrder.ASCENDING
        }
        sortKey = key
        sortOrder = nextOrder
        onSort(key, nextOrder)
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 多选批量操作横幅
        if (inMultiSelect) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已多选 ${selectedIds.size}/${events.size} 行",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移选中块", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移选中块", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制选中", modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除选中", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onSelectAll, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "全选", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClearSelection, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "退出多选", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 宽屏桌面多列网格表头（# | L | 开始 | 结束 | CPS | 样式 | 说话人 | 文本）
        if (wideTableMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#", Modifier.width(38.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                SortableHeaderCell("层", Modifier.width(30.dp), sortKey == SortKey.LAYER, sortOrder) { triggerSort(SortKey.LAYER) }
                SortableHeaderCell("开始", Modifier.width(88.dp), sortKey == SortKey.START, sortOrder) { triggerSort(SortKey.START) }
                SortableHeaderCell("结束", Modifier.width(88.dp), sortKey == SortKey.END, sortOrder) { triggerSort(SortKey.END) }
                Text("CPS", Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                SortableHeaderCell("样式", Modifier.width(88.dp), sortKey == SortKey.STYLE, sortOrder) { triggerSort(SortKey.STYLE) }
                SortableHeaderCell("说话人", Modifier.width(74.dp), sortKey == SortKey.ACTOR, sortOrder) { triggerSort(SortKey.ACTOR) }
                SortableHeaderCell("字幕文本", Modifier.weight(1f), sortKey == SortKey.TEXT, sortOrder) { triggerSort(SortKey.TEXT) }
                Spacer(Modifier.width(28.dp))
            }
            HorizontalDivider()
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(events, key = { _, e -> e.id }) { index, event ->
                val isActive = event.id == activeEventId
                val isVisibleInVideo = event.id == currentVisibleEventId
                val isMultiSelected = event.id in selectedIds
                val overflow = overflowMap[event.id]

                val displayLineText = when (tagDisplayMode) {
                    TagDisplayMode.SHOW_TAGS -> event.text
                    TagDisplayMode.SIMPLIFY_TAGS -> event.simplifiedText
                    TagDisplayMode.HIDE_TAGS -> event.strippedText
                }.replace("\\N", " ↵ ")

                val rowBg = when {
                    isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    isMultiSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                    overflow?.isOverflowing == true -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                    event.comment -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    isVisibleInVideo -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
                    index % 2 == 1 -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.35f)
                    else -> Color.Transparent
                }

                Box {
                    if (wideTableMode) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .background(rowBg)
                                .combinedClickable(
                                    onClick = {
                                        if (inMultiSelect) onToggleSelectRow(event.id) else onSelectRow(event.id)
                                    },
                                    onLongClick = { onToggleSelectRow(event.id) },
                                )
                                .padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 左侧状态色带（绿=画面正在显示，红=SGMY 超出画幅，蓝=当前编辑）
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(
                                        when {
                                            overflow?.isOverflowing == true -> MaterialTheme.colorScheme.error
                                            isVisibleInVideo -> MaterialTheme.colorScheme.secondary
                                            isActive -> MaterialTheme.colorScheme.primary
                                            else -> Color.Transparent
                                        },
                                    ),
                            )
                            Spacer(Modifier.width(4.dp))
                            if (inMultiSelect) {
                                Icon(
                                    if (isMultiSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp).padding(end = 2.dp),
                                )
                            }
                            Text(
                                "${index + 1}",
                                Modifier.width(if (inMultiSelect) 24.dp else 34.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                            )
                            Text(
                                "${event.layer}",
                                Modifier.width(30.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                            )
                            Text(
                                event.start.toAssString(false),
                                Modifier.width(88.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                            )
                            Text(
                                event.end.toAssString(false),
                                Modifier.width(88.dp),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                            )
                            Text(
                                "${event.cps}",
                                Modifier.width(42.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (overflow?.isHighCps == true) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (overflow?.isHighCps == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                event.style,
                                Modifier.width(88.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                event.actor,
                                Modifier.width(74.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = displayLineText.ifBlank { "（空）" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = if (event.comment) FontStyle.Italic else FontStyle.Normal,
                                ),
                                color = when {
                                    event.comment -> MaterialTheme.colorScheme.outline
                                    overflow?.isOverflowing == true -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (overflow?.isOverflowing == true) {
                                Text(
                                    "溢出",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSelectRow(event.id)
                                    contextMenuEventId = event.id
                                },
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "行菜单", modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        // 手机紧凑高密度两行卡片式网格行
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(rowBg)
                                .combinedClickable(
                                    onClick = {
                                        if (inMultiSelect) onToggleSelectRow(event.id) else onSelectRow(event.id)
                                    },
                                    onLongClick = { onToggleSelectRow(event.id) },
                                )
                                .padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(50.dp)
                                    .background(
                                        when {
                                            overflow?.isOverflowing == true -> MaterialTheme.colorScheme.error
                                            isVisibleInVideo -> MaterialTheme.colorScheme.secondary
                                            isActive -> MaterialTheme.colorScheme.primary
                                            else -> Color.Transparent
                                        },
                                    ),
                            )
                            if (inMultiSelect) {
                                Icon(
                                    if (isMultiSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "${event.start.toAssString(false)} → ${event.end.toAssString(false)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        event.style,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    if (overflow?.isOverflowing == true) {
                                        Text("⚠溢出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    } else if (overflow?.isHighCps == true) {
                                        Text("${event.cps}CPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                                Text(
                                    text = displayLineText.ifBlank { "（空白字幕行）" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = if (event.comment) FontStyle.Italic else FontStyle.Normal,
                                    ),
                                    color = if (event.comment) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSelectRow(event.id)
                                    contextMenuEventId = event.id
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "行操作菜单", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // 完整桌面 Aegisub 网格右键上下文菜单（对齐 `default_menu.json` -> `grid_context`）
                    DropdownMenu(
                        expanded = contextMenuEventId == event.id,
                        onDismissRequest = { contextMenuEventId = null },
                    ) {
                        DropdownMenuItem(text = { Text("在上方插入行") }, onClick = { contextMenuEventId = null; onInsertBefore(false) })
                        DropdownMenuItem(text = { Text("在下方插入行") }, onClick = { contextMenuEventId = null; onInsertAfter(false) })
                        DropdownMenuItem(text = { Text("按视频时间在上方插入") }, onClick = { contextMenuEventId = null; onInsertBefore(true) })
                        DropdownMenuItem(text = { Text("按视频时间在下方插入") }, onClick = { contextMenuEventId = null; onInsertAfter(true) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("复制行 (Duplicate)") }, onClick = { contextMenuEventId = null; onDuplicate() })
                        DropdownMenuItem(text = { Text("在文本中点分割行") }, onClick = { contextMenuEventId = null; onSplitMidpoint() })
                        DropdownMenuItem(text = { Text("在当前视频时间分割行") }, onClick = { contextMenuEventId = null; onSplitVideoTime() })
                        DropdownMenuItem(text = { Text("按卡拉OK音节拆分") }, onClick = { contextMenuEventId = null; onSplitKaraoke() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("合并下句/选中（拼接文本）") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.CONCAT) })
                        DropdownMenuItem(text = { Text("合并下句/选中（保留首句）") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.KEEP_FIRST) })
                        DropdownMenuItem(text = { Text("合并为卡拉OK (\\k)") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.AS_KARAOKE) })
                        DropdownMenuItem(text = { Text("双行交叉重组 (Recombine)") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.RECOMBINE) })
                        if (selectedIds.size == 2) {
                            DropdownMenuItem(text = { Text("交换选中的两行") }, onClick = { contextMenuEventId = null; onSwapTwo() })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("使时间连续（改结束）") }, onClick = { contextMenuEventId = null; onMakeContinuous(true) })
                        DropdownMenuItem(text = { Text("使时间连续（改开始）") }, onClick = { contextMenuEventId = null; onMakeContinuous(false) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("区间连选到此行") }, onClick = { contextMenuEventId = null; onSelectRangeTo(event.id) })
                        DropdownMenuItem(text = { Text("复制为剪贴板 ASS 行") }, onClick = { contextMenuEventId = null; onCopyLines() })
                        DropdownMenuItem(text = { Text("从剪贴板粘贴行") }, onClick = { contextMenuEventId = null; onPasteLines() })
                        DropdownMenuItem(text = { Text("粘贴覆盖…") }, onClick = { contextMenuEventId = null; onOpenPasteOver() })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除行", color = MaterialTheme.colorScheme.error) },
                            onClick = { contextMenuEventId = null; onDelete() },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun SortableHeaderCell(
    title: String,
    modifier: Modifier,
    active: Boolean,
    order: SortOrder,
    onClick: () -> Unit,
) {
    val arrow = if (active) (if (order == SortOrder.ASCENDING) "↑" else "↓") else ""
    Text(
        text = "$title$arrow",
        modifier = modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
