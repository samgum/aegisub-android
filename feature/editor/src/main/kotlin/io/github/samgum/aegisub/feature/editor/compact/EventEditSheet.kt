package io.github.samgum.aegisub.feature.editor.compact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.editor.components.EventEditFields

/**
 * 事件编辑底栏（compact）：ModalBottomSheet 包装 [EventEditFields]。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditSheet(
    event: AssEvent,
    styles: ImmutableList<String>,
    onDismiss: () -> Unit,
    onTextChanged: (String) -> Unit,
    onTimesChanged: (start: SubTime, end: SubTime) -> Unit,
    onStyleChanged: (String) -> Unit,
    onLayerChanged: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("编辑字幕", style = MaterialTheme.typography.titleLarge)
            EventEditFields(
                event = event,
                styles = styles,
                onTextChanged = onTextChanged,
                onTimesChanged = onTimesChanged,
                onStyleChanged = onStyleChanged,
                onLayerChanged = onLayerChanged,
            )
        }
    }
}
