package io.github.samgum.aegisub.navigation

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.samgum.aegisub.feature.editor.navigation.styleEditorScreen
import io.github.samgum.aegisub.feature.editor.navigation.stylesRoute
import io.github.samgum.aegisub.ui.about.AboutScreen
import io.github.samgum.aegisub.ui.home.HomeScreen
import io.github.samgum.aegisub.ui.settings.SettingsScreen
import io.github.samgum.aegisub.ui.settings.SettingsViewModel
import io.github.samgum.aegisub.ui.theme.AegisubTheme
import io.github.samgum.aegisub.ui.workspace.WorkspaceScreen

private const val HOME_ROUTE = "home"
private const val SETTINGS_ROUTE = "settings"
private const val ABOUT_ROUTE = "about"
private const val WORKSPACE_ROUTE_PATTERN = "workspace/{projectId}"
private fun workspaceRoute(projectId: Long) = "workspace/$projectId"

/**
 * 统一自适应工作台导航图：
 * - `home`（Studio 工程仪表盘）
 * - `workspace/{projectId}`（全功能自适应字幕工作台：手机/平板/折叠屏一键直达，彻底取代旧版割裂双屏）
 * - `styles/{projectId}`（双栏全局样式库 + 当前脚本样式管理器）
 * - `settings`（全局偏好与热键绑定）
 * - `about`（关于 Aegisub SGMY Android）
 *
 * @author 伤感咩吖
 */
@Composable
fun AppNavigation(
    externalSubtitleUri: Uri? = null,
    onExternalUriConsumed: () -> Unit = {},
) {
    val settings: SettingsViewModel = hiltViewModel()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()
    val langCode by settings.langCode.collectAsStateWithLifecycle()

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
                    externalSubtitleUri = externalSubtitleUri,
                    onExternalUriConsumed = onExternalUriConsumed,
                    onOpenProject = { id -> nav.navigate(workspaceRoute(id)) },
                    onOpenSettings = { nav.navigate(SETTINGS_ROUTE) },
                    onOpenAbout = { nav.navigate(ABOUT_ROUTE) },
                )
            }

            composable(
                route = WORKSPACE_ROUTE_PATTERN,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) {
                WorkspaceScreen(
                    onBack = { nav.popBackStack() },
                    onOpenStyles = { id -> nav.navigate(stylesRoute(id)) },
                    onOpenSettings = { nav.navigate(SETTINGS_ROUTE) },
                    onOpenAbout = { nav.navigate(ABOUT_ROUTE) },
                )
            }

            styleEditorScreen(onBack = { nav.popBackStack() })

            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onOpenAbout = { nav.navigate(ABOUT_ROUTE) },
                )
            }

            composable(ABOUT_ROUTE) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}
