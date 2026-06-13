package io.github.samgum.aegisub.feature.editor

import io.github.samgum.aegisub.domain.model.AssScript

/**
 * 编辑器界面状态。
 *
 * @author 伤感咩吖
 */
sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Loaded(val script: AssScript) : EditorUiState
    data class Error(val message: String) : EditorUiState
}
