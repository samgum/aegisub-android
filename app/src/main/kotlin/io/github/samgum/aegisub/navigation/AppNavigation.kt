package io.github.samgum.aegisub.navigation

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
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
import io.github.samgum.aegisub.ui.about.AboutScreen
import io.github.samgum.aegisub.ui.home.HomeScreen
import io.github.samgum.aegisub.ui.settings.SettingsScreen
import io.github.samgum.aegisub.ui.settings.SettingsViewModel
import io.github.samgum.aegisub.ui.theme.AegisubTheme

/** 主屏路由常量。 */
private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val ABOUT_ROUTE = "about"

/**
 * 应用导航图：home（项目列表）↔ settings（设置）↔ about（关于）↔ editor（字幕编辑器）↔ preview（视频预览）。
 * 最外层按用户主题偏好套 [AegisubTheme]；语言偏好经 AppCompatDelegate 应用 per-app locale。
 *
 * @author 伤感咩吖
 */
@Composable
fun AppNavigation() {
    val settings: SettingsViewModel = hiltViewModel()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val langCode by settings.langCode.collectAsStateWithLifecycle()
    // 冷启动时把持久化的语言偏好同步到 per-app locale
    LaunchedEffect(langCode) {
        AppCompatDelegate.setApplicationLocales(
            when (langCode) {
                "zh" -> LocaleListCompat.forLanguageTags("zh")
                "en" -> LocaleListCompat.forLanguageTags("en")
                else -> LocaleListCompat.getEmptyLocaleList()
            },
        )
    }
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
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenAbout = { nav.navigate(ABOUT_ROUTE) },
                )
            }
            composable(ABOUT_ROUTE) {
                AboutScreen(onBack = { nav.popBackStack() })
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
