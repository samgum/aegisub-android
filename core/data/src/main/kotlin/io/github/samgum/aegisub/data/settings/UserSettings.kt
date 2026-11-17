package io.github.samgum.aegisub.data.settings

import io.github.samgum.aegisub.domain.format.TimePrecision

/**
 * 主题模式：跟随系统 / 强制浅色 / 强制深色。
 *
 * @author 伤感咩吖
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 工作区布局模式：
 * - [AUTO]：按屏幕宽度、横竖屏与折叠屏姿态（Tabletop / Book / 平板大屏 / 手机紧凑）自动分派
 * - [COMPACT]：强制紧凑移动布局
 * - [EXPANDED]：强制分栏工作台布局
 * - [DESKTOP_PRO]：强制完整复刻桌面 Aegisub 经典多区工作台布局
 *
 * @author 伤感咩吖
 */
enum class LayoutMode { AUTO, COMPACT, EXPANDED, DESKTOP_PRO }

/**
 * 字幕网格 ASS 标签显示模式（复刻桌面 Aegisub 工具栏 `☼` 切换）：
 * - [SHOW_TAGS]：完整显示 `{...}` 原始标签
 * - [SIMPLIFY_TAGS]：将 `{...}` 简化显示为 `☼` 占位符
 * - [HIDE_TAGS]：完全隐藏覆盖标签，仅显示纯文本
 */
enum class TagDisplayMode { SHOW_TAGS, SIMPLIFY_TAGS, HIDE_TAGS }

/**
 * 用户全局设置：含主题、导出精度、响应式布局、SGMY 溢出检测、打轴偏好与 AI 分析配置。
 *
 * @author 伤感咩吖
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val exportPrecision: TimePrecision = TimePrecision.AUTO,
    val layoutMode: LayoutMode = LayoutMode.AUTO,
    /** 界面语言：system = 跟随系统；zh = 简体中文；en = English。 */
    val langCode: String = "system",
    val tagDisplayMode: TagDisplayMode = TagDisplayMode.SIMPLIFY_TAGS,
    /** SGMY 字幕超出画幅高亮提示。 */
    val highlightOverflow: Boolean = true,
    /** CPS 语速过高警示阈值。 */
    val cpsWarningThreshold: Int = 22,
    /** 音频打轴偏好。 */
    val autoCommitTiming: Boolean = true,
    val autoNextOnCommit: Boolean = true,
    val snapToKeyframes: Boolean = true,
    val defaultLeadInMs: Int = 80,
    val defaultLeadOutMs: Int = 120,
    /** HDR / 高分辨率视频预览降采样优化（复刻 SGMY 1080p 预览优化开关）。 */
    val hdrPreviewOptimize: Boolean = true,
    /** SGMY AI 字幕分析配置（对齐 `dialog_ai_analysis.cpp`）。 */
    val aiEnabled: Boolean = true,
    val aiProviderIndex: Int = 0,
    val aiBaseUrl: String = "https://api.deepseek.com",
    val aiModel: String = "deepseek-chat",
    val aiTargetLang: String = "Chinese",
    val aiTemperature: Float = 0.3f,
    val aiMaxTokens: Int = 2048,
    val aiThinking: Boolean = false,
    val aiCacheSession: Boolean = true,
    /** 全局样式库（Style Storage，每条为序列化 `Style: ...` 行）。 */
    val styleStorageLines: List<String> = emptyList(),
)
