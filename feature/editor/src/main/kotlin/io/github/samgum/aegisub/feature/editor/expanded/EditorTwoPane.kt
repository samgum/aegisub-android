package io.github.samgum.aegisub.feature.editor.expanded

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.toPersistentList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import io.github.samgum.aegisub.feature.editor.components.EventRow

/**
 * 平板/大屏双栏布局（expanded/Medium）：左列表 | 右详情常驻。
 * 与 compact 共用 EventRow / EventEditFields / EditorActions。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTwoPane(
    script: AssScript,
    editingId: Long?,
    onEventClick: (AssEvent) -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTextChanged: (eventId: Long, text: String) -> Unit,
    onTimesChanged: (eventId: Long, start: SubTime, end: SubTime) -> Unit,
    onStyleChanged: (eventId: Long, style: String) -> Unit,
    onLayerChanged: (eventId: Long, layer: Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPreview) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "预览")
                    }
                    EditorActions(canUndo, canRedo, onUndo, onRedo)
                },
            )
        },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 左：字幕列表
            LazyColumn(modifier = Modifier.weight(0.4f)) {
                items(script.events, key = { it.id }) { ev ->
                    EventRow(event = ev, onClick = { onEventClick(ev) })
                }
            }
            // 右：选中事件详情常驻
            val selected = script.events.firstOrNull { it.id == editingId }
            if (selected != null) {
                EventDetail(
                    event = selected,
                    styles = script.styles.map { it.name }.toPersistentList(),
                    onTextChanged = { onTextChanged(selected.id, it) },
                    onTimesChanged = { s, e -> onTimesChanged(selected.id, s, e) },
                    onStyleChanged = { onStyleChanged(selected.id, it) },
                    onLayerChanged = { onLayerChanged(selected.id, it) },
                    modifier = Modifier.weight(0.6f),
                )
            } else {
                Box(
                    modifier = Modifier.weight(0.6f).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "选择左侧字幕行进行编辑",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
