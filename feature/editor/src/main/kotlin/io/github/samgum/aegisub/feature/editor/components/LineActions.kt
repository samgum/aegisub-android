package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 行级操作种类（对齐桌面 Aegisub Edit 菜单与字幕网格右键）。
 *
 * @author 伤感咩吖
 */
enum class LineAction {
    INSERT_BEFORE, // 在当前行前插入空行
    INSERT_AFTER,  // 在当前行后插入空行
    DUPLICATE,     // 复制当前行（紧随其后）
    SPLIT,         // 在文本中点分割为两行
    JOIN_NEXT,     // 与下一行合并（拼接文本，时间取并集）
    MOVE_UP,       // 上移一行
    MOVE_DOWN,     // 下移一行
    DELETE,        // 删除当前行
}

/**
 * 行操作工具条：水平可滚动的紧凑按钮组，供事件编辑底栏 / 详情面板复用。
 *
 * 所有操作均可撤销（经 session.editEvents 单撤销点提交）。
 *
 * @author 伤感咩吖
 */
@Composable
fun LineActions(
    onAction: (LineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Entry(Icons.Filled.KeyboardArrowUp, "上移") { onAction(LineAction.MOVE_UP) }
        Entry(Icons.Filled.KeyboardArrowDown, "下移") { onAction(LineAction.MOVE_DOWN) }
        Entry(null, "复制") { onAction(LineAction.DUPLICATE) }
        Entry(Icons.Filled.Add, "前插") { onAction(LineAction.INSERT_BEFORE) }
        Entry(null, "后插") { onAction(LineAction.INSERT_AFTER) }
        Entry(null, "分割") { onAction(LineAction.SPLIT) }
        Entry(null, "合并") { onAction(LineAction.JOIN_NEXT) }
        Entry(Icons.Filled.Delete, "删除") { onAction(LineAction.DELETE) }
    }
}

/** 单个操作按钮：可选前置图标 + 文字。紧凑内边距适配工具条。 */
@Composable
private fun RowScope.Entry(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
