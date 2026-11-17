package io.github.samgum.aegisub.ui.workspace.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
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
 * 桌面级高密度字幕网格（支持宽屏多列表格与手机舒适双行卡片，原生支持鼠标悬停、右键菜单、Ctrl多选与Shift连选）。
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
        // 多选批量操作条
        if (inMultiSelect) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已选 ${selectedIds.size} / ${events.size} 行",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "复制", modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onSelectAll, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.SelectAll, contentDescription = "全选", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClearSelection, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "取消多选", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 宽屏多列表头
        if (wideTableMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#", Modifier.width(38.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                SortableHeaderCell("层", Modifier.width(30.dp), sortKey == SortKey.LAYER, sortOrder) { triggerSort(SortKey.LAYER) }
                SortableHeaderCell("开始", Modifier.width(88.dp), sortKey == SortKey.START, sortOrder) { triggerSort(SortKey.START) }
                SortableHeaderCell("结束", Modifier.width(88.dp), sortKey == SortKey.END, sortOrder) { triggerSort(SortKey.END) }
                Text("语速", Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                SortableHeaderCell("样式", Modifier.width(88.dp), sortKey == SortKey.STYLE, sortOrder) { triggerSort(SortKey.STYLE) }
                SortableHeaderCell("说话人", Modifier.width(74.dp), sortKey == SortKey.ACTOR, sortOrder) { triggerSort(SortKey.ACTOR) }
                SortableHeaderCell("字幕内容", Modifier.weight(1f), sortKey == SortKey.TEXT, sortOrder) { triggerSort(SortKey.TEXT) }
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

                val hoverSource = remember { MutableInteractionSource() }
                val isHovered by hoverSource.collectIsHoveredAsState()

                val displayLineText = when (tagDisplayMode) {
                    TagDisplayMode.SHOW_TAGS -> event.text
                    TagDisplayMode.SIMPLIFY_TAGS -> event.simplifiedText
                    TagDisplayMode.HIDE_TAGS -> event.strippedText
                }.replace("\\N", " ↵ ")

                val rowBg = when {
                    isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f)
                    isMultiSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
                    overflow?.isOverflowing == true -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.24f)
                    isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
                    event.comment -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                    isVisibleInVideo -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
                    index % 2 == 1 -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.25f)
                    else -> Color.Transparent
                }

                // 键鼠增强修饰器：支持鼠标右键直接弹上下文菜单、Shift+左键区间连选、Ctrl+左键加减多选
                val mouseRowModifier = Modifier
                    .hoverable(hoverSource)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(event.id, inMultiSelect) {
                        awaitPointerEventScope {
                            while (true) {
                                val pe = awaitPointerEvent(PointerEventPass.Initial)
                                if (pe.type == PointerEventType.Press) {
                                    when {
                                        pe.buttons.isSecondaryPressed -> {
                                            if (!inMultiSelect) onSelectRow(event.id)
                                            contextMenuEventId = event.id
                                            pe.changes.forEach { it.consume() }
                                        }
                                        pe.keyboardModifiers.isShiftPressed -> {
                                            onSelectRangeTo(event.id)
                                            pe.changes.forEach { it.consume() }
                                        }
                                        pe.keyboardModifiers.isCtrlPressed -> {
                                            onToggleSelectRow(event.id)
                                            pe.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                        }
                    }

                Box {
                    if (wideTableMode) {
                        Row(
                            mouseRowModifier
                                .fillMaxWidth()
                                .height(36.dp)
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
                                if (event.visibleCharCount > 0) "${event.cps}" else "",
                                Modifier.width(44.dp),
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
                                text = displayLineText.ifBlank { "—" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontStyle = if (event.comment) FontStyle.Italic else FontStyle.Normal,
                                ),
                                color = when {
                                    displayLineText.isBlank() -> MaterialTheme.colorScheme.outline
                                    event.comment -> MaterialTheme.colorScheme.outline
                                    overflow?.isOverflowing == true -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (overflow?.isOverflowing == true) {
                                Text(
                                    "超框",
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
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "行菜单", modifier = Modifier.size(16.dp))
                            }
                        }
                    } else {
                        // 手机紧凑舒适两行卡片
                        Row(
                            mouseRowModifier
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
                                    .height(54.dp)
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
                                    modifier = Modifier.padding(start = 8.dp).size(18.dp),
                                )
                            }
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    )
                                    Text(
                                        "${event.start.toAssString(false)} – ${event.end.toAssString(false)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (event.style != "Default") {
                                        Text(
                                            event.style,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            maxLines = 1,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    if (overflow?.isOverflowing == true) {
                                        Text("超框", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    } else if (overflow?.isHighCps == true) {
                                        Text("${event.cps}字/秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                                Text(
                                    text = displayLineText.ifBlank { "—" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = if (event.comment) FontStyle.Italic else FontStyle.Normal,
                                    ),
                                    color = when {
                                        displayLineText.isBlank() -> MaterialTheme.colorScheme.outline
                                        event.comment -> MaterialTheme.colorScheme.outline
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    onSelectRow(event.id)
                                    contextMenuEventId = event.id
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多操作", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = contextMenuEventId == event.id,
                        onDismissRequest = { contextMenuEventId = null },
                    ) {
                        DropdownMenuItem(text = { Text("在上方插入行") }, onClick = { contextMenuEventId = null; onInsertBefore(false) })
                        DropdownMenuItem(text = { Text("在下方插入行") }, onClick = { contextMenuEventId = null; onInsertAfter(false) })
                        DropdownMenuItem(text = { Text("按视频时间在上方插入") }, onClick = { contextMenuEventId = null; onInsertBefore(true) })
                        DropdownMenuItem(text = { Text("按视频时间在下方插入") }, onClick = { contextMenuEventId = null; onInsertAfter(true) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("复制行") }, onClick = { contextMenuEventId = null; onDuplicate() })
                        DropdownMenuItem(text = { Text("在文本中点分割") }, onClick = { contextMenuEventId = null; onSplitMidpoint() })
                        DropdownMenuItem(text = { Text("在当前视频位置分割") }, onClick = { contextMenuEventId = null; onSplitVideoTime() })
                        DropdownMenuItem(text = { Text("按卡拉OK音节拆分") }, onClick = { contextMenuEventId = null; onSplitKaraoke() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("合并行（拼接文本）") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.CONCAT) })
                        DropdownMenuItem(text = { Text("合并行（保留首句）") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.KEEP_FIRST) })
                        DropdownMenuItem(text = { Text("合并为卡拉OK") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.AS_KARAOKE) })
                        DropdownMenuItem(text = { Text("双行交叉重组") }, onClick = { contextMenuEventId = null; onJoin(WorkspaceViewModel.JoinMode.RECOMBINE) })
                        if (selectedIds.size == 2) {
                            DropdownMenuItem(text = { Text("交换选中的两行") }, onClick = { contextMenuEventId = null; onSwapTwo() })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("使时间连续（延长前句结束）") }, onClick = { contextMenuEventId = null; onMakeContinuous(true) })
                        DropdownMenuItem(text = { Text("使时间连续（提前下句开始）") }, onClick = { contextMenuEventId = null; onMakeContinuous(false) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("区间连选到此行") }, onClick = { contextMenuEventId = null; onSelectRangeTo(event.id) })
                        DropdownMenuItem(text = { Text("复制选中行") }, onClick = { contextMenuEventId = null; onCopyLines() })
                        DropdownMenuItem(text = { Text("粘贴行") }, onClick = { contextMenuEventId = null; onPasteLines() })
                        DropdownMenuItem(text = { Text("粘贴覆盖文本…") }, onClick = { contextMenuEventId = null; onOpenPasteOver() })
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除行", color = MaterialTheme.colorScheme.error) },
                            onClick = { contextMenuEventId = null; onDelete() },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
    val arrow = if (active) (if (order == SortOrder.ASCENDING) " ↑" else " ↓") else ""
    Text(
        text = "$title$arrow",
        modifier = modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
