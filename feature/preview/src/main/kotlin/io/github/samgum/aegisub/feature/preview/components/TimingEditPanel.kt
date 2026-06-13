package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 微调目标端。 */
enum class NudgeTarget { START, END }

/**
 * 选中行的时间编辑面板：起/止滑块（拖动实时 seek，松手提交）+ 微调按钮组。
 *
 * @param durationMs 视频时长；<=0 时滑块值域按 1 处理（仅微调可用）。
 * @param nudgeTarget 当前微调作用端，拖动滑块会自动切换。
 * @param onNudgeTargetChange 切换微调端。
 * @param onSeek 拖动滑块时实时 seek（看画面，不提交）。
 * @param onCommitStart 松手时把起始提交到该毫秒。
 * @param onCommitEnd 松手时把结束提交到该毫秒。
 * @param onNudge 微调按钮：对当前 nudgeTarget 加减 deltaMs。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimingEditPanel(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    nudgeTarget: NudgeTarget,
    onNudgeTargetChange: (NudgeTarget) -> Unit,
    onSeek: (Long) -> Unit,
    onCommitStart: (Long) -> Unit,
    onCommitEnd: (Long) -> Unit,
    onNudge: (deltaMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        StartSlider(startMs, durationMs, onSeek, onCommitStart, onSelect = { onNudgeTargetChange(NudgeTarget.START) })
        EndSlider(endMs, durationMs, onSeek, onCommitEnd, onSelect = { onNudgeTargetChange(NudgeTarget.END) })

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TargetChip("起", selected = nudgeTarget == NudgeTarget.START, onClick = { onNudgeTargetChange(NudgeTarget.START) })
            TargetChip("止", selected = nudgeTarget == NudgeTarget.END, onClick = { onNudgeTargetChange(NudgeTarget.END) })
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NudgeButton("−1帧") { onNudge(-FRAME_MS) }
            NudgeButton("−0.1s") { onNudge(-100) }
            NudgeButton("−1s") { onNudge(-1_000) }
            NudgeButton("+1帧") { onNudge(FRAME_MS) }
            NudgeButton("+0.1s") { onNudge(100) }
            NudgeButton("+1s") { onNudge(1_000) }
        }
    }
}

@Composable
private fun StartSlider(
    valueMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    onSelect: () -> Unit,
) {
    var dragging by remember(valueMs) { mutableStateOf(valueMs.toFloat()) }
    Column {
        Text("起 ${formatMs(valueMs)}", style = MaterialTheme.typography.labelSmall)
        val max = durationMs.coerceAtLeast(1)
        Slider(
            value = dragging.coerceIn(0f, max.toFloat()),
            onValueChange = {
                dragging = it
                onSeek(it.toLong())
                onSelect()
            },
            valueRange = 0f..max.toFloat(),
            onValueChangeFinished = { onCommit(dragging.toLong()) },
        )
    }
}

@Composable
private fun EndSlider(
    valueMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onCommit: (Long) -> Unit,
    onSelect: () -> Unit,
) {
    var dragging by remember(valueMs) { mutableStateOf(valueMs.toFloat()) }
    Column {
        Text("止 ${formatMs(valueMs)}", style = MaterialTheme.typography.labelSmall)
        val max = durationMs.coerceAtLeast(1)
        Slider(
            value = dragging.coerceIn(0f, max.toFloat()),
            onValueChange = {
                dragging = it
                onSeek(it.toLong())
                onSelect()
            },
            valueRange = 0f..max.toFloat(),
            onValueChangeFinished = { onCommit(dragging.toLong()) },
        )
    }
}

@Composable
private fun TargetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = if (selected) AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) else AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun NudgeButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatMs(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    val m = total / 60_000
    val s = (total % 60_000) / 1_000
    val mm = total % 1_000
    return "%d:%02d.%03d".format(m, s, mm)
}

/** 23.976fps 一帧 ≈ 42ms。 */
private const val FRAME_MS: Long = 42L
