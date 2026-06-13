package io.github.samgum.aegisub.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.samgum.aegisub.feature.editor.navigation.editorRoute
import io.github.samgum.aegisub.feature.editor.navigation.editorScreen
import io.github.samgum.aegisub.feature.editor.navigation.styleEditorScreen
import io.github.samgum.aegisub.feature.editor.navigation.stylesRoute
import io.github.samgum.aegisub.feature.preview.navigation.previewRoute
import io.github.samgum.aegisub.feature.preview.navigation.previewScreen
import io.github.samgum.aegisub.ui.home.HomeScreen
import io.github.samgum.aegisub.ui.settings.SettingsScreen
import io.github.samgum.aegisub.ui.settings.SettingsViewModel
import io.github.samgum.aegisub.ui.theme.AegisubTheme

/** 主屏路由常量。 */
private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"

/**
 * 应用导航图：home（项目列表）↔ settings（设置）↔ editor（字幕编辑器）↔ preview（视频预览）。
 * 最外层按用户主题偏好套 [AegisubTheme]。
 *
 * @author 伤感咩吖
 */
@Composable
fun AppNavigation() {
    val settings: SettingsViewModel = hiltViewModel()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    AegisubTheme(themeMode) {
        val nav = rememberNavController()
        NavHost(navController = nav, startDestination = HOME_ROUTE) {
            composable(HOME_ROUTE) {
                HomeScreen(
                    onOpenProject = { id -> nav.navigate(editorRoute(id)) },
                    onOpenSettings = { nav.navigate(SETTINGS_ROUTE) },
                )
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(onBack = { nav.popBackStack() })
            }
            editorScreen(
                onBack = { nav.popBackStack() },
                onOpenPreview = { id -> nav.navigate(previewRoute(id)) },
                onOpenStyles = { id -> nav.navigate(stylesRoute(id)) },
            )
            styleEditorScreen(onBack = { nav.popBackStack() })
            previewScreen(onBack = { nav.popBackStack() })
        }
    }
}
