package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 多选批量操作栏（选择模式激活时置于屏底）：
 * 上移 / 下移（连续块）/ 复制 / 删除 / 全不选 + 已选计数。
 *
 * @author 伤感咩吖
 */
@Composable
fun SelectionActionBar(
    count: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出选择")
            }
            Text(
                "已选 $count",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "块上移")
            }
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "块下移")
            }
            TextButton(onClick = onDuplicate) { Text("复制") }
            TextButton(onClick = onDelete) { Text("删除") }
            TextButton(onClick = onSelectAll, enabled = count < total) { Text("全选") }
        }
    }
}
