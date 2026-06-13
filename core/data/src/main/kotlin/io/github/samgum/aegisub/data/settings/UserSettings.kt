package io.github.samgum.aegisub.data.settings

/**
 * 主题模式：跟随系统 / 强制浅色 / 强制深色。
 *
 * @author 伤感咩吖
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 用户设置（当前仅主题；后续可扩展导出精度、布局等）。
 *
 * @author 伤感咩吖
 */
data class UserSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM)
