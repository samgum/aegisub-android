package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.samgum.aegisub.domain.model.AssEvent

/**
 * 单条字幕事件行：行号 + 时间区间 + 样式 + 去标签纯文本。
 *
 * 选择模式下（[selectionMode]=true）：行号位置换成复选框，点按切换选中，长按亦切换，
 * 选中行用 primaryContainer 高亮。
 * 非选择模式：点按打开编辑，长按进入选择模式并选中本行。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventRow(
    event: AssEvent,
    index: Int,
    onClick: () -> Unit,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    ListItem(
        headlineContent = {
            Text(
                text = event.strippedText.ifBlank { "（无文本）" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 注释行用弱化色，呼应 ASS Comment 语义
                color = if (event.comment) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = {
            Text(
                text = "${event.start.toAssString(false)} → ${event.end.toAssString(false)}  ·  ${event.style}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
            } else {
                // 行号（从 1 起，对齐桌面版字幕列表）
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        // 选择模式：点按=切换选中；非选择模式：点按=打开编辑、长按=进入选择
        modifier = Modifier.combinedClickable(
            onClick = if (selectionMode) onToggleSelect else onClick,
            onLongClick = if (selectionMode) onToggleSelect else onLongClick,
        ),
    )
}
