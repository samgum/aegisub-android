package io.github.samgum.aegisub.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import io.github.samgum.aegisub.data.settings.LayoutMode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * 设备与折叠屏自适应姿态模型（基于 Jetpack WindowManager [WindowInfoTracker] + [FoldingFeature]）：
 * - [PHONE_COMPACT]：手机竖屏 / 小窗（`< 600dp`）→ 紧凑可折叠媒体舞台 + 快打条 + 内联编辑 + 高密度列表 + 底栏导航
 * - [LANDSCAPE_SPLIT]：手机横屏 / 小平板（`600..839dp` 或高度紧凑）→ 左右双栏可拖拽工作台（左：视频+波形，右：编辑框+网格）
 * - [TABLETOP]：折叠屏半折悬停（水平折痕 `HALF_OPENED + HORIZONTAL`）→ 折痕上方视频预览与打字，折痕安全留白，折痕下方波形+编辑控制台+网格
 * - [BOOK_DUAL_PANE]：折叠屏书本/双屏展开（垂直分离折痕 `isSeparating + VERTICAL`）→ 左页视频+音频+卡拉OK，折痕安全间距，右页工具栏+编辑框+多列网格
 * - [DESKTOP_THREE_ZONE]：大屏平板 / 折叠屏全展宽屏 / DeX 桌面模式（`>= 840dp` 且高度充裕）→ 1:1 复刻桌面 `samgum/Aegisub` 经典三区+顶栏布局
 *
 * @author 伤感咩吖
 */
enum class WorkspacePosture {
    PHONE_COMPACT,
    LANDSCAPE_SPLIT,
    TABLETOP,
    BOOK_DUAL_PANE,
    DESKTOP_THREE_ZONE,
}

data class AdaptiveWindowState(
    val posture: WorkspacePosture,
    val widthDp: Int,
    val heightDp: Int,
    /** 折叠屏左半屏宽度（仅 [WorkspacePosture.BOOK_DUAL_PANE] 有值）。 */
    val bookLeftWidthDp: Dp? = null,
    /** 折叠屏上半屏高度（仅 [WorkspacePosture.TABLETOP] 有值）。 */
    val tabletopTopHeightDp: Dp? = null,
    /** 物理铰链/折痕避让宽度或高度。 */
    val hingeThicknessDp: Dp = 0.dp,
    val isFoldSeparating: Boolean = false,
) {
    val postureBadge: String
        get() = when (posture) {
            WorkspacePosture.PHONE_COMPACT -> "手机竖屏"
            WorkspacePosture.LANDSCAPE_SPLIT -> "横屏双栏"
            WorkspacePosture.TABLETOP -> "折叠悬停 (Tabletop)"
            WorkspacePosture.BOOK_DUAL_PANE -> "折叠双页 (Book)"
            WorkspacePosture.DESKTOP_THREE_ZONE -> "平板/桌面专业版"
        }
}

/** 纯函数：给定窗口宽高、用户布局模式及可选折叠信息，决策最终姿态（便于纯 JVM 单测全覆盖）。 */
fun resolveWorkspacePosture(
    widthDp: Int,
    heightDp: Int,
    layoutMode: LayoutMode,
    isFoldHalfOpened: Boolean = false,
    isFoldSeparating: Boolean = false,
    isFoldHorizontal: Boolean = false,
    foldLeftPx: Int = 0,
    foldTopPx: Int = 0,
    foldRightPx: Int = 0,
    foldBottomPx: Int = 0,
    density: Float = 1f,
): AdaptiveWindowState {
    val safeDensity = density.coerceAtLeast(0.5f)
    when (layoutMode) {
        LayoutMode.COMPACT -> return AdaptiveWindowState(WorkspacePosture.PHONE_COMPACT, widthDp, heightDp)
        LayoutMode.DESKTOP_PRO -> return AdaptiveWindowState(WorkspacePosture.DESKTOP_THREE_ZONE, widthDp, heightDp)
        LayoutMode.EXPANDED -> {
            val p = if (widthDp >= 840 && heightDp >= 560) WorkspacePosture.DESKTOP_THREE_ZONE else WorkspacePosture.LANDSCAPE_SPLIT
            return AdaptiveWindowState(p, widthDp, heightDp)
        }
        LayoutMode.AUTO -> Unit
    }

    // 1. 优先识别折叠屏悬停（Tabletop）姿态：水平折痕半开
    if (isFoldHalfOpened && isFoldHorizontal && foldTopPx > 0) {
        val topDp = (foldTopPx / safeDensity).dp
        val hingeDp = ((foldBottomPx - foldTopPx).coerceAtLeast(16) / safeDensity).dp
        return AdaptiveWindowState(
            posture = WorkspacePosture.TABLETOP,
            widthDp = widthDp,
            heightDp = heightDp,
            tabletopTopHeightDp = topDp,
            hingeThicknessDp = hingeDp,
            isFoldSeparating = true,
        )
    }

    // 2. 识别折叠屏书本半开或双屏垂直分离姿态（Book / Dual-Screen）
    if ((isFoldHalfOpened || isFoldSeparating) && !isFoldHorizontal && foldLeftPx > 0) {
        val leftDp = (foldLeftPx / safeDensity).dp
        val hingeDp = ((foldRightPx - foldLeftPx).coerceAtLeast(12) / safeDensity).dp
        return AdaptiveWindowState(
            posture = WorkspacePosture.BOOK_DUAL_PANE,
            widthDp = widthDp,
            heightDp = heightDp,
            bookLeftWidthDp = leftDp,
            hingeThicknessDp = hingeDp,
            isFoldSeparating = true,
        )
    }

    // 3. 平板 / 折叠屏完全展平（FLAT）大屏 / DeX 桌面窗口
    if (widthDp >= 840 && heightDp >= 540) {
        return AdaptiveWindowState(WorkspacePosture.DESKTOP_THREE_ZONE, widthDp, heightDp)
    }

    // 4. 手机横屏 / 紧凑分屏 / 小尺寸折叠外屏横置
    if (widthDp >= 600) {
        return AdaptiveWindowState(WorkspacePosture.LANDSCAPE_SPLIT, widthDp, heightDp)
    }

    // 5. 常规手机竖屏
    return AdaptiveWindowState(WorkspacePosture.PHONE_COMPACT, widthDp, heightDp)
}

@Composable
fun rememberAdaptiveWindowState(layoutMode: LayoutMode): AdaptiveWindowState {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    val activity = context.findActivity()

    val foldFlow = remember(activity) {
        if (activity == null) {
            flowOf(null)
        } else {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .map { layoutInfo ->
                    layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                }
        }
    }
    val fold by foldFlow.collectAsStateWithLifecycle(initialValue = null)
    return resolveWorkspacePosture(
        widthDp = config.screenWidthDp,
        heightDp = config.screenHeightDp,
        layoutMode = layoutMode,
        isFoldHalfOpened = fold?.state == FoldingFeature.State.HALF_OPENED,
        isFoldSeparating = fold?.isSeparating == true,
        isFoldHorizontal = fold?.orientation == FoldingFeature.Orientation.HORIZONTAL,
        foldLeftPx = fold?.bounds?.left ?: 0,
        foldTopPx = fold?.bounds?.top ?: 0,
        foldRightPx = fold?.bounds?.right ?: 0,
        foldBottomPx = fold?.bounds?.bottom ?: 0,
        density = density,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
