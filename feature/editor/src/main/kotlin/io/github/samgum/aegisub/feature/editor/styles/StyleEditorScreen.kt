package io.github.samgum.aegisub.feature.editor.styles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.feature.editor.components.EditorActions

/**
 * 样式编辑器：列出工程全部样式，逐张卡片展开编辑（颜色/字体/描边/对齐/边距/编码），
 * 支持新增/删除。所有改动经 session.editStyles，与正文共用撤销栈。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleEditorScreen(
    onBack: () -> Unit,
    viewModel: StyleEditorViewModel = hiltViewModel(),
) {
    val styles by viewModel.styles.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("样式（${styles.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    EditorActions(
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                    )
                    IconButton(onClick = viewModel::addStyle) {
                        Icon(Icons.Filled.Add, contentDescription = "新增样式")
                    }
                },
            )
        },
    ) { padding ->
        if (styles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无样式，点右上角 + 新增", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(styles, key = { i, s -> s.name + "#" + i }) { index, style ->
                    StyleCard(
                        style = style,
                        onUpdate = { transform -> viewModel.updateStyle(index, transform) },
                        onDelete = { viewModel.deleteStyle(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: AssStyle,
    onUpdate: (transform: (AssStyle) -> AssStyle) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 头部：色块 + 名称 + 展开/删除
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(style.primary.toComposeColor())
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Text(
                    style.name.ifEmpty { "（未命名）" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = "展开")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除样式")
                }
            }
            AnimatedVisibility(visible = expanded) {
                StyleFields(style, onUpdate)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除样式") },
            text = { Text("删除「${style.name.ifEmpty { "未命名" }}」？引用它的事件会变成无效样式名。此操作可撤销。") },
            confirmButton = { TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StyleFields(style: AssStyle, onUpdate: (transform: (AssStyle) -> AssStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("名称与字体")
        OutlinedTextField(
            value = style.name,
            onValueChange = { v -> onUpdate { it.copy(name = v) } },
            label = { Text("样式名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = style.font,
                onValueChange = { v -> onUpdate { it.copy(font = v) } },
                label = { Text("字体") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            NumberField("字号", style.fontSize) { v -> onUpdate { it.copy(fontSize = v) } }
        }

        SectionLabel("颜色（点击展开调色）")
        AssColorField("主色 Primary", style.primary) { c -> onUpdate { it.copy(primary = c) } }
        AssColorField("次色 Secondary", style.secondary) { c -> onUpdate { it.copy(secondary = c) } }
        AssColorField("描边 Outline", style.outline) { c -> onUpdate { it.copy(outline = c) } }
        AssColorField("阴影 Shadow", style.shadow) { c -> onUpdate { it.copy(shadow = c) } }

        SectionLabel("字形")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("粗体", style.bold) { v -> onUpdate { it.copy(bold = v) } }
            ToggleChip("斜体", style.italic) { v -> onUpdate { it.copy(italic = v) } }
            ToggleChip("下划线", style.underline) { v -> onUpdate { it.copy(underline = v) } }
            ToggleChip("删除线", style.strikeout) { v -> onUpdate { it.copy(strikeout = v) } }
        }

        SectionLabel("对齐（\\an 1-9）")
        AlignmentGrid(style.alignment) { a -> onUpdate { it.copy(alignment = a) } }

        SectionLabel("边距（L / R / V）")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("左", style.margins.left.toDouble()) { v -> onUpdate { it.copy(margins = it.margins.copy(left = v.toInt())) } }
            NumberField("右", style.margins.right.toDouble()) { v -> onUpdate { it.copy(margins = it.margins.copy(right = v.toInt())) } }
            NumberField("纵", style.margins.vertical.toDouble()) { v -> onUpdate { it.copy(margins = it.margins.copy(vertical = v.toInt())) } }
        }

        SectionLabel("描边与阴影")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("描边宽", style.outlineWidth) { v -> onUpdate { it.copy(outlineWidth = v) } }
            NumberField("阴影宽", style.shadowWidth) { v -> onUpdate { it.copy(shadowWidth = v) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = style.borderStyle == 1,
                onClick = { onUpdate { it.copy(borderStyle = 1) } },
                label = { Text("描边+阴影") },
            )
            FilterChip(
                selected = style.borderStyle == 3,
                onClick = { onUpdate { it.copy(borderStyle = 3) } },
                label = { Text("不透明底框") },
            )
        }

        SectionLabel("变换（缩放/间距/旋转）")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("缩放X%", style.scaleX) { v -> onUpdate { it.copy(scaleX = v) } }
            NumberField("缩放Y%", style.scaleY) { v -> onUpdate { it.copy(scaleY = v) } }
            NumberField("间距", style.spacing) { v -> onUpdate { it.copy(spacing = v) } }
            NumberField("旋转°", style.angle) { v -> onUpdate { it.copy(angle = v) } }
        }

        SectionLabel("编码")
        NumberField("Encoding", style.encoding.toDouble()) { v -> onUpdate { it.copy(encoding = v.toInt()) } }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
}

/** 数字输入：接受小数/整数，输入框内编辑文本，失焦或回车解析。 */
@Composable
private fun NumberField(label: String, value: Double, onParsed: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            v.toDoubleOrNull()?.let(onParsed)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(96.dp),
    )
}

@Composable
private fun ToggleChip(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = checked, onClick = { onChange(!checked) }, label = { Text(label) })
}

/** RGBA 颜色编辑：色块 + 折叠的 R/G/B/A 滑块（0-255）。 */
@Composable
private fun AssColorField(label: String, color: AssColor, onChange: (AssColor) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(color.toComposeColor())
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(label, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text(color.toAssString(), style = MaterialTheme.typography.bodySmall)
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 4.dp)) {
                ColorChannelSlider("R", color.r) { v -> onChange(AssColor(v, color.g, color.b, color.a)) }
                ColorChannelSlider("G", color.g) { v -> onChange(AssColor(color.r, v, color.b, color.a)) }
                ColorChannelSlider("B", color.b) { v -> onChange(AssColor(color.r, color.g, v, color.a)) }
                ColorChannelSlider("A", color.a) { v -> onChange(AssColor(color.r, color.g, color.b, v)) }
            }
        }
    }
}

@Composable
private fun ColorChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(value.toString(), modifier = Modifier.width(32.dp))
    }
}

/** \an 1-9 九宫对齐选择器（numpad 布局）。 */
@Composable
private fun AlignmentGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 行序：顶 7/8/9，中 4/5/6，底 1/2/3
        for (row in listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in row) {
                    AlignCell(a, selected == a) { onSelect(a) }
                }
            }
        }
    }
}

@Composable
private fun AlignCell(value: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(value.toString(), color = fg)
    }
}

/** AssColor → Compose Color（注意 ASS alpha 反转：这里 a 已是 0-255 透明度，直接用）。 */
private fun AssColor.toComposeColor(): Color = Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a / 255f)
