package io.github.samgum.aegisub.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.model.AssScript
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主屏 ViewModel：暴露项目列表，提供新建样例项目。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repo.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createSampleProject() {
        viewModelScope.launch {
            val content = AssFormat.write(AssScript.default())
            val now = System.currentTimeMillis()
            repo.createProject(name = "字幕工程 $now", format = "ass", content = content)
        }
    }
}
