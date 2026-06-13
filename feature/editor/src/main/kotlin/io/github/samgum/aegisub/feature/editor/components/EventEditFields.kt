package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime

/**
 * 事件编辑字段集：文本/起止时间/样式/层。容器无关——
 * 由 EventEditSheet（底栏，compact）或 EventDetail（右栏，expanded）包装复用。
 * 字段变化实时回写；时间输入容错解析，非法值不回写。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditFields(
    event: AssEvent,
    styles: ImmutableList<String>,
    onTextChanged: (String) -> Unit,
    onTimesChanged: (start: SubTime, end: SubTime) -> Unit,
    onStyleChanged: (String) -> Unit,
    onLayerChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 文本（实时回写）
        OutlinedTextField(
            value = event.text,
            onValueChange = onTextChanged,
            label = { Text("文本") },
            modifier = Modifier.fillMaxWidth(),
        )

        // 起止时间（本地缓存输入串，容错解析后回写）
        var startText by remember(event.id) { mutableStateOf(event.start.toAssString(false)) }
        var endText by remember(event.id) { mutableStateOf(event.end.toAssString(false)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = startText,
                onValueChange = {
                    startText = it
                    runCatching { SubTime.parseAss(it) }
                        .onSuccess { s -> onTimesChanged(s, event.end) }
                },
                label = { Text("开始") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.5f),
            )
            OutlinedTextField(
                value = endText,
                onValueChange = {
                    endText = it
                    runCatching { SubTime.parseAss(it) }
                        .onSuccess { e -> onTimesChanged(event.start, e) }
                },
                label = { Text("结束") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 样式下拉（可手输）
        var styleExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = styleExpanded,
            onExpandedChange = { styleExpanded = it },
        ) {
            OutlinedTextField(
                value = event.style,
                onValueChange = onStyleChanged,
                label = { Text("样式") },
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = styleExpanded,
                onDismissRequest = { styleExpanded = false },
            ) {
                styles.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onStyleChanged(name)
                            styleExpanded = false
                        },
                    )
                }
            }
        }

        // 层（数字）
        OutlinedTextField(
            value = event.layer.toString(),
            onValueChange = { raw -> raw.toIntOrNull()?.let(onLayerChanged) },
            label = { Text("层") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
