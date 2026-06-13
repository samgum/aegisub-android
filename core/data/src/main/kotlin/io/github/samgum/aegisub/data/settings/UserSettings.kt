package io.github.samgum.aegisub.data.settings

import io.github.samgum.aegisub.domain.format.TimePrecision

/**
 * 主题模式：跟随系统 / 强制浅色 / 强制深色。
 *
 * @author 伤感咩吖
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 编辑器布局模式：跟随屏宽自动分派 / 强制紧凑（列表+底栏）/ 强制双栏（列表|详情）。
 *
 * @author 伤感咩吖
 */
enum class LayoutMode { AUTO, COMPACT, EXPANDED }

/**
 * 用户设置：主题、ASS 导出时间精度、编辑器布局。后续可继续扩展。
 *
 * @author 伤感咩吖
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val exportPrecision: TimePrecision = TimePrecision.AUTO,
    val layoutMode: LayoutMode = LayoutMode.AUTO,
)
