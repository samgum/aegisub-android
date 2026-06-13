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
 * 单条字幕事件行：时间区间 + 样式 + 去标签纯文本。
 *
 * @author 伤感咩吖
 */
@Composable
fun EventRow(event: AssEvent, onClick: () -> Unit) {
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
            // 注释行标记 //（ASS Comment），正常行显示图层号
            Text(
                text = if (event.comment) "//" else "L${event.layer}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
