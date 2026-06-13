package io.github.samgum.aegisub.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toPersistentList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.feature.editor.compact.EventEditSheet
import io.github.samgum.aegisub.feature.editor.compact.EventListScreen
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import io.github.samgum.aegisub.feature.editor.expanded.EditorTwoPane

/**
 * 编辑器入口屏：按 [EditorUiState] 分发，再按窗口宽度选 compact（列表+底栏）
 * 或 expanded（双栏列表|详情）布局。撤销/重做与编辑统一回写 [EditorViewModel]。
 *
 * @author 伤感咩吖
 */
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<Long?>(null) }

    when (val s = state) {
        EditorUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is EditorUiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "加载失败：${s.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onBack) { Text("返回") }
                }
            }

        is EditorUiState.Loaded -> {
            // Material 宽度断点：< 600dp 为 Compact（手机竖屏），否则 Medium/Expanded（平板/横屏）
            val isCompact = LocalConfiguration.current.screenWidthDp < 600
            if (isCompact) {
                CompactEditor(
                    script = s.script,
                    editingId = editingId,
                    onEventClick = { editingId = it.id },
                    onDismissEdit = { editingId = null },
                    onBack = onBack,
                    onOpenPreview = { onOpenPreview(viewModel.projectId) },
                    canUndo = canUndo,
                    canRedo = canRedo,
                    viewModel = viewModel,
                )
            } else {
                EditorTwoPane(
                    script = s.script,
                    editingId = editingId,
                    onEventClick = { editingId = it.id },
                    onBack = onBack,
                    onOpenPreview = { onOpenPreview(viewModel.projectId) },
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onTextChanged = viewModel::updateEventText,
                    onTimesChanged = viewModel::updateEventTimes,
                    onStyleChanged = viewModel::updateEventStyle,
                    onLayerChanged = viewModel::setEventLayer,
                )
            }
        }
    }
}

@Composable
private fun CompactEditor(
    script: AssScript,
    editingId: Long?,
    onEventClick: (AssEvent) -> Unit,
    onDismissEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    viewModel: EditorViewModel,
) {
    EventListScreen(
        events = script.events,
        onEventClick = onEventClick,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenPreview) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "预览")
            }
            EditorActions(
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
            )
        },
    )
    script.events.firstOrNull { it.id == editingId }?.let { ev ->
        EventEditSheet(
            event = ev,
            styles = script.styles.map { it.name }.toPersistentList(),
            onDismiss = onDismissEdit,
            onTextChanged = { viewModel.updateEventText(ev.id, it) },
            onTimesChanged = { start, end -> viewModel.updateEventTimes(ev.id, start, end) },
            onStyleChanged = { viewModel.updateEventStyle(ev.id, it) },
            onLayerChanged = { viewModel.setEventLayer(ev.id, it) },
        )
    }
}
