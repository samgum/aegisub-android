package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssStyle

/**
 * 样式助手（复刻桌面 Aegisub Styling Assistant）：
 * 逐行浏览字幕，从样式列表点选一个 → 应用到当前行并自动前进到下一行。
 *
 * @param event 当前行（被分配样式的对象）
 * @param position 当前行序号（0 基，用于显示「第 N / M 行」）
 * @param total 事件总数
 * @param styles 全部样式（点击即分配）
 * @param onAssign 点选某样式：应用到当前行并前进（由调用方实现前进逻辑）
 * @param onPrev/onNext 上一行 / 下一行
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylingAssistantSheet(
    event: AssEvent,
    position: Int,
    total: Int,
    styles: ImmutableList<AssStyle>,
    onAssign: (styleName: String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            // 顶部：行导航 + 当前行预览
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("样式助手", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第 ${position + 1} / $total 行",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconButton(onClick = onPrev, enabled = position > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一行")
                    }
                    IconButton(onClick = onNext, enabled = position < total - 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一行")
                    }
                }
            }
            Text(
                text = event.strippedText.ifBlank { "（空行）" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                "当前样式：${event.style}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("点选样式以应用到当前行（并自动前进）", style = MaterialTheme.typography.labelMedium)

            // 样式列表
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            ) {
                items(styles, key = { it.name }) { style ->
                    StyleChoiceRow(
                        style = style,
                        selected = style.name == event.style,
                        onClick = { onAssign(style.name) },
                    )
                }
            }
        }
    }
}

/** 单个样式选项：色块 + 名称 + 字体/字号描述，点击即分配。 */
@Composable
private fun StyleChoiceRow(style: AssStyle, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(style.primary.argb), CircleShape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                style.name,
                style = if (selected) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${style.font} ${style.fontSize.toInt()}px · 对齐 ${style.alignment}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text("✓", color = MaterialTheme.colorScheme.primary)
        }
    }
}
