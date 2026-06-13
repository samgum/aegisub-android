package io.github.samgum.aegisub.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.samgum.aegisub.feature.editor.navigation.editorRoute
import io.github.samgum.aegisub.feature.editor.navigation.editorScreen
import io.github.samgum.aegisub.ui.home.HomeScreen

/** 主屏路由常量。 */
private const val HOME_ROUTE = "home"

/**
 * 应用导航图：home（项目列表）↔ editor（字幕编辑器）。
 *
 * @author 伤感咩吖
 */
@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            HomeScreen(onOpenProject = { id -> nav.navigate(editorRoute(id)) })
        }
        editorScreen(onBack = { nav.popBackStack() })
    }
}
