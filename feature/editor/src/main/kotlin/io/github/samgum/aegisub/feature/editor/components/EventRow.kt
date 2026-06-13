package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.samgum.aegisub.domain.model.AssEvent

/**
 * 单条字幕事件行：行号 + 时间区间 + 样式 + 去标签纯文本。
 *
 * @author 伤感咩吖
 */
@Composable
fun EventRow(event: AssEvent, index: Int, onClick: () -> Unit) {
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
            // 行号（从 1 起，对齐 Aegisub 桌面版字幕列表）；注释行同样显示行号，靠 headline 弱化色区分
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
