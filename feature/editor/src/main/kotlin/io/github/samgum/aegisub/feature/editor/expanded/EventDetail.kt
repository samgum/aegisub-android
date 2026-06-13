package io.github.samgum.aegisub.feature.editor.expanded

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.editor.components.EventEditFields
import io.github.samgum.aegisub.feature.editor.components.LineAction
import io.github.samgum.aegisub.feature.editor.components.LineActions

/**
 * 双栏右栏常驻详情面板（expanded）：包装 [EventEditFields]，无 Sheet 容器，附行操作工具条。
 *
 * @author 伤感咩吖
 */
@Composable
fun EventDetail(
    event: AssEvent,
    styles: ImmutableList<String>,
    onTextChanged: (String) -> Unit,
    onTimesChanged: (start: SubTime, end: SubTime) -> Unit,
    onStyleChanged: (String) -> Unit,
    onLayerChanged: (Int) -> Unit,
    onLineAction: (LineAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("详情", style = MaterialTheme.typography.titleLarge)
        EventEditFields(
            event = event,
            styles = styles,
            onTextChanged = onTextChanged,
            onTimesChanged = onTimesChanged,
            onStyleChanged = onStyleChanged,
            onLayerChanged = onLayerChanged,
        )
        HorizontalDivider()
        Text("行操作", style = MaterialTheme.typography.labelLarge)
        LineActions(onAction = onLineAction)
    }
}
