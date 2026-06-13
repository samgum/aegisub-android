package io.github.samgum.aegisub.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
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
            val sample = AssScript.default().withEvents(
                listOf(
                    AssEvent(
                        start = SubTime.ofMillis(1_000),
                        end = SubTime.ofMillis(3_000),
                        text = "欢迎来到 Aegisub Android",
                    ),
                    AssEvent(
                        start = SubTime.ofMillis(3_200),
                        end = SubTime.ofMillis(6_000),
                        text = "点击字幕行即可编辑（Task 2B）",
                    ),
                    AssEvent(
                        comment = true,
                        text = "这是一条注释行示例",
                    ),
                ),
            )
            val content = AssFormat.write(sample)
            val now = System.currentTimeMillis()
            repo.createProject(name = "字幕工程 $now", format = "ass", content = content)
        }
    }
}
