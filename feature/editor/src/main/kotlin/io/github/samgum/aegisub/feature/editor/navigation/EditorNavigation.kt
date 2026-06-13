package io.github.samgum.aegisub.feature.editor.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.samgum.aegisub.feature.editor.EditorScreen
import io.github.samgum.aegisub.feature.editor.styles.StyleEditorScreen

/** 编辑器路由基段，projectId 作为路径参数（String 传递，ViewModel 内 toLong）。 */
const val EDITOR_ROUTE_BASE = "editor"
private const val EDITOR_ROUTE = "$EDITOR_ROUTE_BASE/{projectId}"

/** 样式编辑器路由基段，同样以 projectId 为路径参数。 */
const val STYLES_ROUTE_BASE = "styles"
private const val STYLES_ROUTE = "$STYLES_ROUTE_BASE/{projectId}"

/** 拼装某工程的编辑器路由。 */
fun editorRoute(projectId: Long): String = "$EDITOR_ROUTE_BASE/$projectId"

/** 拼装某工程的样式编辑器路由。 */
fun stylesRoute(projectId: Long): String = "$STYLES_ROUTE_BASE/$projectId"

/** 注册编辑器目的地。 */
fun NavGraphBuilder.editorScreen(
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onOpenStyles: (Long) -> Unit,
) {
    composable(
        route = EDITOR_ROUTE,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
    ) {
        EditorScreen(onBack = onBack, onOpenPreview = onOpenPreview, onOpenStyles = onOpenStyles)
    }
}

/** 注册样式编辑器目的地。 */
fun NavGraphBuilder.styleEditorScreen(onBack: () -> Unit) {
    composable(
        route = STYLES_ROUTE,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
    ) {
        StyleEditorScreen(onBack = onBack)
    }
}

