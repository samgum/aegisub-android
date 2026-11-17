package io.github.samgum.aegisub.feature.editor.styles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import kotlinx.coroutines.launch

/**
 * 桌面级 Aegisub 样式管理器（支持手机单栏切换与平板/折叠屏双栏：全局样式库 Storage | 当前脚本样式 Current Script + 棋盘格实时预览）。
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
    val storageStyles by viewModel.storageStyles.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isWide = LocalConfiguration.current.screenWidthDp >= 720
    var showStorageTabOnCompact by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("样式管理器（脚本 ${styles.size} · 样式库 ${storageStyles.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val count = viewModel.extractMissingStylesFromEvents()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count > 0) "已从事件行提取补齐 $count 个缺失样式" else "全部事件引用样式均已定义",
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = "从字幕提取样式")
                    }
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
        if (isWide) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                StoragePane(
                    storageStyles = storageStyles,
                    onImportToScript = { s ->
                        viewModel.importFromStorage(s)
                        scope.launch { snackbarHostState.showSnackbar("已导入「${s.name}」到当前脚本") }
                    },
                    onDeleteFromStorage = viewModel::deleteFromStorage,
                    modifier = Modifier.weight(0.36f).fillMaxHeight(),
                )
                VerticalDivider()
                ScriptStylesPane(
                    styles = styles,
                    onUpdate = viewModel::updateStyle,
                    onDuplicate = viewModel::duplicateStyle,
                    onSaveToStorage = { s ->
                        viewModel.saveToStorage(s)
                        scope.launch { snackbarHostState.showSnackbar("已存入全局样式库：「${s.name}」") }
                    },
                    onDelete = viewModel::deleteStyle,
                    modifier = Modifier.weight(0.64f).fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showStorageTabOnCompact,
                        onClick = { showStorageTabOnCompact = false },
                        label = { Text("当前脚本 (${styles.size})") },
                    )
                    FilterChip(
                        selected = showStorageTabOnCompact,
                        onClick = { showStorageTabOnCompact = true },
                        label = { Text("全局样式库 (${storageStyles.size})") },
                    )
                }
                if (showStorageTabOnCompact) {
                    StoragePane(
                        storageStyles = storageStyles,
                        onImportToScript = { s ->
                            viewModel.importFromStorage(s)
                            scope.launch { snackbarHostState.showSnackbar("已导入「${s.name}」到当前脚本") }
                        },
                        onDeleteFromStorage = viewModel::deleteFromStorage,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ScriptStylesPane(
                        styles = styles,
                        onUpdate = viewModel::updateStyle,
                        onDuplicate = viewModel::duplicateStyle,
                        onSaveToStorage = { s ->
                            viewModel.saveToStorage(s)
                            scope.launch { snackbarHostState.showSnackbar("已存入全局样式库：「${s.name}」") }
                        },
                        onDelete = viewModel::deleteStyle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoragePane(
    storageStyles: List<AssStyle>,
    onImportToScript: (AssStyle) -> Unit,
    onDeleteFromStorage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(12.dp)) {
        Text("全局样式库 (Storage)", style = MaterialTheme.typography.titleSmall)
        Text(
            "跨工程共享常用字幕样式，一键导入当前脚本。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (storageStyles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("样式库为空，在脚本样式卡片上点「存入库」即可收藏。", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(storageStyles, key = { i, s -> s.name + "#" + i }) { _, st ->
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(18.dp).clip(CircleShape)
                                    .background(st.primary.toComposeColor())
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(st.name, style = MaterialTheme.typography.bodyMedium)
                                Text("${st.font} · ${st.fontSize.toInt()}pt · \\an${st.alignment}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { onImportToScript(st) }) {
                                Icon(Icons.Filled.Download, contentDescription = "导入到脚本")
                            }
                            IconButton(onClick = { onDeleteFromStorage(st.name) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "从样式库移除")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptStylesPane(
    styles: List<AssStyle>,
    onUpdate: (Int, (AssStyle) -> AssStyle) -> Unit,
    onDuplicate: (Int) -> Unit,
    onSaveToStorage: (AssStyle) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (styles.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无样式，点右上角 + 新增", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(styles, key = { i, s -> s.name + "#" + i }) { index, style ->
                StyleCard(
                    style = style,
                    onUpdate = { transform -> onUpdate(index, transform) },
                    onDuplicate = { onDuplicate(index) },
                    onSaveToStorage = { onSaveToStorage(style) },
                    onDelete = { onDelete(index) },
                )
            }
        }
    }
}

@Composable
private fun StyleCard(
    style: AssStyle,
    onUpdate: (transform: (AssStyle) -> AssStyle) -> Unit,
    onDuplicate: () -> Unit,
    onSaveToStorage: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(style.primary.toComposeColor())
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(style.name.ifEmpty { "（未命名）" }, style = MaterialTheme.typography.titleMedium)
                    Text("${style.font} · ${style.fontSize.toInt()}pt · \\an${style.alignment}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onSaveToStorage) {
                    Icon(Icons.Filled.Upload, contentDescription = "存入全局样式库")
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制样式")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = "展开")
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除样式")
                }
            }
            // 仿桌面 Aegisub 棋盘格实时样式渲染预览框
            StyleLivePreviewBanner(style)
            AnimatedVisibility(visible = expanded) {
                StyleFields(style, onUpdate)
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除样式") },
            text = { Text("删除「${style.name.ifEmpty { "未命名" }}」？此操作可撤销。") },
            confirmButton = { TextButton(onClick = { onDelete(); confirmDelete = false }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun StyleLivePreviewBanner(style: AssStyle) {
    val measurer = rememberTextMeasurer()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small),
    ) {
        val cell = 10.dp.toPx()
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val even = (r + c) % 2 == 0
                drawRect(
                    color = if (even) Color(0xFF2B303B) else Color(0xFF1F242D),
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell),
                )
            }
        }
        val sample = "Aegisub SGMY 字幕样式预览 123"
        val previewSp = (style.fontSize.coerceIn(14.0, 56.0) * 0.42).sp
        val baseTs = TextStyle(
            fontSize = previewSp,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        )
        val layoutFill = measurer.measure(sample, style = baseTs.copy(color = style.primary.toComposeColor()))
        val layoutOutline = measurer.measure(sample, style = baseTs.copy(color = style.outline.toComposeColor()))
        val layoutShadow = measurer.measure(sample, style = baseTs.copy(color = style.shadow.toComposeColor()))
        val tl = Offset(
            ((size.width - layoutFill.size.width) / 2f).coerceAtLeast(4f),
            ((size.height - layoutFill.size.height) / 2f).coerceAtLeast(2f),
        )
        val shad = style.shadowWidth.toFloat().coerceIn(0f, 6f)
        val bord = style.outlineWidth.toFloat().coerceIn(0f, 6f)
        if (shad > 0.2f) drawText(layoutShadow, topLeft = Offset(tl.x + shad, tl.y + shad))
        if (bord > 0.2f) drawText(layoutOutline, topLeft = tl, drawStyle = Stroke(width = bord * 1.6f))
        drawText(layoutFill, topLeft = tl)
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
        AssColorField("次色 Secondary (卡拉OK预备色)", style.secondary) { c -> onUpdate { it.copy(secondary = c) } }
        AssColorField("描边 Outline", style.outline) { c -> onUpdate { it.copy(outline = c) } }
        AssColorField("阴影 Shadow", style.shadow) { c -> onUpdate { it.copy(shadow = c) } }

        SectionLabel("字形")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleChip("粗体", style.bold) { v -> onUpdate { it.copy(bold = v) } }
            ToggleChip("斜体", style.italic) { v -> onUpdate { it.copy(italic = v) } }
            ToggleChip("下划线", style.underline) { v -> onUpdate { it.copy(underline = v) } }
            ToggleChip("删除线", style.strikeout) { v -> onUpdate { it.copy(strikeout = v) } }
        }

        SectionLabel("对齐（\\an 1-9 九宫）")
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

@Composable
private fun AlignmentGrid(selected: Int, onSelect: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in row) {
                    AlignCell(a, selected == a) { onSelect(a) }
                }
            }
            Spacer(Modifier.height(4.dp))
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

private fun AssColor.toComposeColor(): Color = Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a / 255f)
