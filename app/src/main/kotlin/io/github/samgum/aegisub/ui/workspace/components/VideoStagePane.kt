package io.github.samgum.aegisub.ui.workspace.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo
import io.github.samgum.aegisub.feature.preview.PlaybackState
import io.github.samgum.aegisub.feature.preview.VideoPlayer
import io.github.samgum.aegisub.feature.preview.components.PlayerSurface
import io.github.samgum.aegisub.feature.preview.components.SubtitleOverlay
import io.github.samgum.aegisub.feature.preview.components.VisualToolMode
import io.github.samgum.aegisub.feature.preview.components.VisualTypesettingOverlay
import kotlin.math.roundToInt

/**
 * 定比视频预览与可视化排版舞台（支持触控捏合缩放 + 鼠标滚轮缩放 + 单击播放暂停 + 8 大排版工具）。
 *
 * @author 伤感咩吖
 */
@Composable
fun VideoStagePane(
    script: AssScript,
    activeEvent: AssEvent?,
    renderInfos: List<SubtitleRenderInfo>,
    hasMedia: Boolean,
    playback: PlaybackState,
    player: VideoPlayer,
    visualToolMode: VisualToolMode,
    visualOverlayEnabled: Boolean,
    onVisualOverlayEnabledChange: (Boolean) -> Unit,
    onVisualToolModeChange: (VisualToolMode) -> Unit,
    onPickVideo: () -> Unit,
    onPlayPause: () -> Unit,
    onPlayLine: () -> Unit,
    onSeek: (Long) -> Unit,
    onFrameBack: () -> Unit,
    onFrameForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPosChange: (Int, Int) -> Unit,
    onMoveChange: (Int, Int, Int, Int) -> Unit,
    onRotationZChange: (Int) -> Unit,
    onRotation3dChange: (Int, Int) -> Unit,
    onOriginChange: (Int, Int) -> Unit,
    onScaleChange: (Int, Int) -> Unit,
    onClipRectChange: (Int, Int, Int, Int, Boolean) -> Unit,
    onVectorClipChange: (VisualTags.VectorClip) -> Unit,
    onClearVisualTags: () -> Unit,
    showTypesettingStrip: Boolean = true,
    compactControls: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playResX = script.getScriptInfo("PlayResX")?.toIntOrNull()?.takeIf { it > 0 } ?: 1920
    val playResY = script.getScriptInfo("PlayResY")?.toIntOrNull()?.takeIf { it > 0 } ?: 1080

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var arOverride by remember { mutableFloatStateOf(0f) }
    var showSafeArea by remember { mutableStateOf(false) }
    var inverseClipToggle by remember(activeEvent?.id) {
        mutableStateOf(
            activeEvent?.text?.let { VisualTags.getClip(it)?.inverse ?: VisualTags.getVectorClip(it)?.inverse } ?: false,
        )
    }

    val effectiveAspect = when {
        arOverride > 0f -> arOverride
        playback.videoWidth > 0 && playback.videoHeight > 0 -> playback.videoWidth.toFloat() / playback.videoHeight.toFloat()
        else -> (playResX.toFloat() / playResY.toFloat()).coerceIn(0.4f, 3.0f)
    }

    Column(modifier.background(Color(0xFF0A0D12))) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // 左侧 8 工具可视化排版条（手机常规预览模式可隐藏以释放全宽画面）
            if (showTypesettingStrip) {
                Column(
                    Modifier
                        .width(38.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    VisualStripIcon(Icons.Filled.AdsClick, "坐标标尺", visualOverlayEnabled && visualToolMode == VisualToolMode.CROSSHAIR) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.CROSSHAIR) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.CROSSHAIR) }
                    }
                    VisualStripIcon(Icons.Filled.OpenWith, "位置定位", visualOverlayEnabled && visualToolMode == VisualToolMode.POSITION) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.POSITION) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.POSITION) }
                    }
                    VisualStripIcon(Icons.Filled.Timeline, "移动路径", visualOverlayEnabled && visualToolMode == VisualToolMode.MOVE) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.MOVE) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.MOVE) }
                    }
                    VisualStripIcon(Icons.AutoMirrored.Filled.RotateRight, "平面旋转", visualOverlayEnabled && visualToolMode == VisualToolMode.ROTATE_Z) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.ROTATE_Z) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.ROTATE_Z) }
                    }
                    VisualStripIcon(Icons.AutoMirrored.Filled.RotateLeft, "3D透视与原点", visualOverlayEnabled && visualToolMode == VisualToolMode.ROTATE_XY) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.ROTATE_XY) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.ROTATE_XY) }
                    }
                    VisualStripIcon(Icons.Filled.CropFree, "文字缩放", visualOverlayEnabled && visualToolMode == VisualToolMode.SCALE) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.SCALE) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.SCALE) }
                    }
                    VisualStripIcon(Icons.Filled.Crop, "矩形裁剪", visualOverlayEnabled && visualToolMode == VisualToolMode.CLIP_RECT) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.CLIP_RECT) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.CLIP_RECT) }
                    }
                    VisualStripIcon(Icons.Filled.Polyline, "多边形裁剪", visualOverlayEnabled && visualToolMode == VisualToolMode.CLIP_VECTOR) {
                        if (visualOverlayEnabled && visualToolMode == VisualToolMode.CLIP_VECTOR) onVisualOverlayEnabledChange(false)
                        else { onVisualOverlayEnabledChange(true); onVisualToolModeChange(VisualToolMode.CLIP_VECTOR) }
                    }
                    if (zoomScale != 1f || panOffset != Offset.Zero) {
                        VisualStripIcon(Icons.Filled.ZoomInMap, "重置画面缩放", false) {
                            zoomScale = 1f; panOffset = Offset.Zero
                        }
                    }
                }
            }

            // 居中定比视口（鼠标滚轮缩放 + 未开启打字时单击播放/暂停、双击复位缩放）
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.type == PointerEventType.Scroll) {
                                    val deltaY = ev.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (deltaY != 0f && (ev.keyboardModifiers.isCtrlPressed || !visualOverlayEnabled)) {
                                        val factor = if (deltaY < 0f) 1.12f else 0.89f
                                        val nextZoom = (zoomScale * factor).coerceIn(0.5f, 3.0f)
                                        zoomScale = nextZoom
                                        if (nextZoom == 1f) panOffset = Offset.Zero
                                        ev.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(visualOverlayEnabled, hasMedia) {
                        if (!visualOverlayEnabled) {
                            detectTapGestures(
                                onTap = { if (hasMedia) onPlayPause() },
                                onDoubleTap = { zoomScale = 1f; panOffset = Offset.Zero },
                            )
                        }
                    }
                    .pointerInput(visualOverlayEnabled) {
                        if (!visualOverlayEnabled) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextZoom = (zoomScale * zoom).coerceIn(0.5f, 3.0f)
                                zoomScale = nextZoom
                                panOffset = if (nextZoom == 1f) Offset.Zero else panOffset + pan
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val containerW = maxWidth
                val containerH = maxHeight
                val containerRatio = if (containerH.value > 0f) containerW.value / containerH.value else 1.777f
                val (frameW, frameH) = if (containerRatio > effectiveAspect) {
                    (containerH * effectiveAspect) to containerH
                } else {
                    containerW to (containerW / effectiveAspect)
                }

                Box(
                    modifier = Modifier
                        .width(frameW)
                        .height(frameH)
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                        .background(Color(0xFF141820)),
                ) {
                    if (hasMedia) {
                        PlayerSurface(player = player, modifier = Modifier.fillMaxSize())
                    } else {
                        DummyVideoBackdrop()
                    }

                    SubtitleOverlay(
                        renderInfos = renderInfos,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (showSafeArea) {
                        SafeAreaGuideOverlay()
                    }

                    if (visualOverlayEnabled && activeEvent != null) {
                        VisualTypesettingOverlay(
                            playResX = playResX,
                            playResY = playResY,
                            mode = visualToolMode,
                            currentPos = VisualTags.getPos(activeEvent.text),
                            currentMove = VisualTags.getMove(activeEvent.text),
                            currentRotation = VisualTags.getRotation3d(activeEvent.text),
                            currentOrigin = VisualTags.getOrigin(activeEvent.text),
                            currentScale = VisualTags.getScale(activeEvent.text),
                            currentClipRect = VisualTags.getClip(activeEvent.text)?.copy(inverse = inverseClipToggle),
                            currentVectorClip = VisualTags.getVectorClip(activeEvent.text)?.copy(inverse = inverseClipToggle),
                            onPosChange = onPosChange,
                            onMoveChange = onMoveChange,
                            onRotationZChange = onRotationZChange,
                            onRotation3dChange = onRotation3dChange,
                            onOriginChange = onOriginChange,
                            onScaleChange = onScaleChange,
                            onClipRectChange = { x1, y1, x2, y2, _ ->
                                onClipRectChange(x1, y1, x2, y2, inverseClipToggle)
                            },
                            onVectorClipChange = { vc ->
                                onVectorClipChange(vc.copy(inverse = inverseClipToggle))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // 未加载媒体时，仅在未进入排版且无台词遮挡时居中显示清爽的打开按钮
                if (!hasMedia && !visualOverlayEnabled && renderInfos.isEmpty()) {
                    Surface(
                        color = Color(0xCC1E293B),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(onClick = onPickVideo),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Movie, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("打开视频或音频", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // 右上角 HDR 色彩空间徽标（仅打开真实 HDR/BT.2020 片源时低调呈现）
                if (hasMedia && (playback.isHdr || playback.colorInfoLabel.isNotEmpty())) {
                    Surface(
                        color = if (playback.isHdr) Color(0xCC7C2D12) else Color(0x991E293B),
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    ) {
                        Text(
                            text = playback.colorInfoLabel.ifEmpty { "HDR" },
                            color = if (playback.isHdr) Color(0xFFFDE68A) else Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // 处于排版模式时右下角辅助操作胶囊
                if (visualOverlayEnabled && activeEvent != null) {
                    Surface(
                        color = Color(0xCC0F172A),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (visualToolMode == VisualToolMode.CLIP_RECT || visualToolMode == VisualToolMode.CLIP_VECTOR) {
                                TextButton(
                                    onClick = { inverseClipToggle = !inverseClipToggle },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp),
                                ) {
                                    Text(
                                        if (inverseClipToggle) "反向遮罩" else "正向裁剪",
                                        color = Color(0xFF38BDF8),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            TextButton(
                                onClick = onClearVisualTags,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp),
                            ) {
                                Text("重置排版", color = Color(0xFFFCA5A5), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        if (hasMedia || !compactControls) {
            VideoTransportBar(
                playback = playback,
                compact = compactControls,
                showSafeArea = showSafeArea,
                onToggleSafeArea = { showSafeArea = !showSafeArea },
                onArOverrideChange = { arOverride = it },
                onPlayPause = onPlayPause,
                onPlayLine = onPlayLine,
                onSeek = onSeek,
                onFrameBack = onFrameBack,
                onFrameForward = onFrameForward,
                onSpeedChange = onSpeedChange,
            )
        }
    }
}

@Composable
private fun VisualStripIcon(
    icon: ImageVector,
    tooltip: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .size(32.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DummyVideoBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val cell = (size.minDimension / 14f).coerceAtLeast(12f)
        val cols = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                drawRect(
                    color = if ((r + c) % 2 == 0) Color(0xFF181E29) else Color(0xFF131822),
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}

@Composable
private fun SafeAreaGuideOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        drawRect(
            color = Color(0x6638BDF8),
            topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
            size = Size(size.width * 0.90f, size.height * 0.90f),
            style = Stroke(width = 1.2f, pathEffect = dash),
        )
        drawRect(
            color = Color(0x66FBBF24),
            topLeft = Offset(size.width * 0.10f, size.height * 0.10f),
            size = Size(size.width * 0.80f, size.height * 0.80f),
            style = Stroke(width = 1.2f, pathEffect = dash),
        )
    }
}

@Composable
private fun VideoTransportBar(
    playback: PlaybackState,
    compact: Boolean,
    showSafeArea: Boolean,
    onToggleSafeArea: () -> Unit,
    onArOverrideChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPlayLine: () -> Unit,
    onSeek: (Long) -> Unit,
    onFrameBack: () -> Unit,
    onFrameForward: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f)
    var speedMenu by remember { mutableStateOf(false) }
    var arMenu by remember { mutableStateOf(false) }

    val fps = if (playback.fps > 0f) playback.fps else 23.976f
    val frameNumber = ((playback.positionMs / 1000f) * fps).roundToInt()

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
            if (playback.durationMs > 0L) {
                Slider(
                    value = (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { ratio -> onSeek((ratio * playback.durationMs).toLong()) },
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = onPlayPause, modifier = Modifier.size(34.dp).pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(
                        if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onPlayLine, modifier = Modifier.size(34.dp).pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(Icons.Filled.PlayCircleOutline, contentDescription = "试听当前句", modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onFrameBack, modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一帧", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onFrameForward, modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon.Hand)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一帧", modifier = Modifier.size(20.dp))
                }

                Text(
                    text = "${SubTimeFmt.ass(playback.positionMs)} / ${SubTimeFmt.ass(playback.durationMs)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                if (!compact) {
                    Text(
                        text = "帧 $frameNumber",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }

                Box {
                    TextButton(
                        onClick = { speedMenu = true },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.height(28.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text("${playback.speed}x", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        speeds.forEach { r ->
                            DropdownMenuItem(
                                text = { Text("${r}× 倍速") },
                                onClick = { onSpeedChange(r); speedMenu = false },
                            )
                        }
                    }
                }

                if (!compact) {
                    Box {
                        TextButton(
                            onClick = { arMenu = true },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.height(28.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Text("画幅比例", style = MaterialTheme.typography.labelSmall)
                        }
                        DropdownMenu(expanded = arMenu, onDismissRequest = { arMenu = false }) {
                            DropdownMenuItem(text = { Text("自动原始比例") }, onClick = { onArOverrideChange(0f); arMenu = false })
                            DropdownMenuItem(text = { Text("16:9 宽屏") }, onClick = { onArOverrideChange(16f / 9f); arMenu = false })
                            DropdownMenuItem(text = { Text("4:3 标准") }, onClick = { onArOverrideChange(4f / 3f); arMenu = false })
                            DropdownMenuItem(text = { Text("2.35:1 电影宽银幕") }, onClick = { onArOverrideChange(2.35f); arMenu = false })
                            DropdownMenuItem(text = { Text("9:16 竖屏") }, onClick = { onArOverrideChange(9f / 16f); arMenu = false })
                        }
                    }
                    TextButton(
                        onClick = onToggleSafeArea,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.height(28.dp).pointerHoverIcon(PointerIcon.Hand),
                    ) {
                        Text(if (showSafeArea) "隐藏安全框" else "安全框", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

internal object SubTimeFmt {
    fun ass(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val cs = (safe / 10L) % 100L
        val totalSec = safe / 1000L
        val s = totalSec % 60L
        val m = (totalSec / 60L) % 60L
        val h = totalSec / 3600L
        return "%d:%02d:%02d.%02d".format(h, m, s, cs)
    }
}
