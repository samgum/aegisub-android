package io.github.samgum.aegisub.ui.adaptive

import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.data.settings.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖全部 5 种自适应形态姿态（手机竖屏 / 手机横屏 / 折叠悬停 Tabletop / 折叠双页 Book / 平板桌面三区）
 * 及用户强制布局模式覆盖的单测。
 *
 * @author 伤感咩吖
 */
class AdaptivePostureTest {

    @Test
    fun phonePortrait_resolvesToPhoneCompact() {
        val state = resolveWorkspacePosture(widthDp = 393, heightDp = 852, layoutMode = LayoutMode.AUTO)
        assertEquals(WorkspacePosture.PHONE_COMPACT, state.posture)
    }

    @Test
    fun phoneLandscape_resolvesToLandscapeSplit() {
        val state = resolveWorkspacePosture(widthDp = 812, heightDp = 375, layoutMode = LayoutMode.AUTO)
        assertEquals(WorkspacePosture.LANDSCAPE_SPLIT, state.posture)
    }

    @Test
    fun tabletOrUnfoldedFlat_resolvesToDesktopThreeZone() {
        val state = resolveWorkspacePosture(widthDp = 1280, heightDp = 800, layoutMode = LayoutMode.AUTO)
        assertEquals(WorkspacePosture.DESKTOP_THREE_ZONE, state.posture)
    }

    @Test
    fun foldableHalfOpenedHorizontal_resolvesToTabletopWithHingeOffset() {
        val state = resolveWorkspacePosture(
            widthDp = 840,
            heightDp = 900,
            layoutMode = LayoutMode.AUTO,
            isFoldHalfOpened = true,
            isFoldSeparating = true,
            isFoldHorizontal = true,
            foldTopPx = 900,
            foldBottomPx = 936,
            density = 2f,
        )
        assertEquals(WorkspacePosture.TABLETOP, state.posture)
        assertEquals(450.dp, state.tabletopTopHeightDp)
        assertEquals(18.dp, state.hingeThicknessDp)
        assertTrue(state.isFoldSeparating)
    }

    @Test
    fun foldableVerticalSeparating_resolvesToBookDualPane() {
        val state = resolveWorkspacePosture(
            widthDp = 900,
            heightDp = 840,
            layoutMode = LayoutMode.AUTO,
            isFoldHalfOpened = true,
            isFoldSeparating = true,
            isFoldHorizontal = false,
            foldLeftPx = 880,
            foldRightPx = 908,
            density = 2f,
        )
        assertEquals(WorkspacePosture.BOOK_DUAL_PANE, state.posture)
        assertEquals(440.dp, state.bookLeftWidthDp)
        assertEquals(14.dp, state.hingeThicknessDp)
        assertTrue(state.isFoldSeparating)
    }

    @Test
    fun manualLayoutOverrides_takePrecedenceOverFoldState() {
        val compact = resolveWorkspacePosture(
            widthDp = 1280,
            heightDp = 800,
            layoutMode = LayoutMode.COMPACT,
            isFoldHalfOpened = true,
            isFoldHorizontal = true,
            foldTopPx = 400,
        )
        assertEquals(WorkspacePosture.PHONE_COMPACT, compact.posture)

        val pro = resolveWorkspacePosture(
            widthDp = 412,
            heightDp = 892,
            layoutMode = LayoutMode.DESKTOP_PRO,
        )
        assertEquals(WorkspacePosture.DESKTOP_THREE_ZONE, pro.posture)
    }
}
