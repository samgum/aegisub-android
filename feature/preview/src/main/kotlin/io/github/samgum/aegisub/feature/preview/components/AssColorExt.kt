package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.ui.graphics.Color
import io.github.samgum.aegisub.domain.model.AssColor

/**
 * AssColor(0-255 RGBA) → Compose Color。隔离在预览模块，避免域层引入 Compose 依赖。
 *
 * @author 伤感咩吖
 */
fun AssColor.toColor(): Color = Color(r, g, b, a)
