package io.github.samgum.aegisub.feature.preview.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.samgum.aegisub.feature.preview.PreviewScreen

/** 预览路由基段，projectId 作为路径参数（String 传递，ViewModel 内 toLong）。 */
const val PREVIEW_ROUTE_BASE = "preview"
private const val PREVIEW_ROUTE = "$PREVIEW_ROUTE_BASE/{projectId}"

/** 拼装某工程的预览路由。 */
fun previewRoute(projectId: Long): String = "$PREVIEW_ROUTE_BASE/$projectId"

/** 注册预览目的地。 */
fun NavGraphBuilder.previewScreen(onBack: () -> Unit) {
    composable(
        route = PREVIEW_ROUTE,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
    ) {
        PreviewScreen(onBack = onBack)
    }
}
