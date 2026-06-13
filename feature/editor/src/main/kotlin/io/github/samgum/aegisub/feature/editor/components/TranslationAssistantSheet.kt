package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.model.AssEvent

/**
 * 翻译助手（复刻桌面 Aegisub Translation Assistant）：
 * 逐行显示原文，输入译文，保存时把原文存入 Name(actor)、译文写入 Text，并前进。
 *
 * 原文来源：优先 Name(actor) 字段（已存过原文）；否则取当前 Text 作为原文。
 * 译文初值：若 Name 已存原文 → 当前 Text（即既有译文）；否则空（待翻译）。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationAssistantSheet(
    event: AssEvent,
    position: Int,
    total: Int,
    onSave: (original: String, translation: String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasStoredOriginal = event.actor.isNotBlank()
    val original = if (hasStoredOriginal) event.actor else event.text
    var translation by remember(event.id) {
        mutableStateOf(if (hasStoredOriginal) event.text else "")
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("翻译助手", style = MaterialTheme.typography.titleLarge)
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

            // 原文（只读）
            Text("原文", style = MaterialTheme.typography.labelLarge)
            Text(
                text = original.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            HorizontalDivider()

            // 译文输入
            OutlinedTextField(
                value = translation,
                onValueChange = { translation = it },
                label = { Text("译文") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onSave(original, translation)
                    },
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("关闭") }
                Button(onClick = { onSave(original, translation) }) {
                    Text("保存并前进")
                }
            }
            Text(
                "保存会把原文写入 Name 字段、译文写入 Text，然后跳到下一行。可撤销。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
