package io.github.samgum.aegisub.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.edit.ScriptInfoOps
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.SubtitleImport
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 主屏工程管理 ViewModel：新建空白字幕、导入字幕/歌词、重命名、复制与删除。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ProjectRepository,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repo.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 新建干净的 1920×1080 空白 ASS 工程并直接进入编辑。 */
    fun createBlankAssProject(customTitle: String = "", onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
            val title = customTitle.trim().ifEmpty { "未命名字幕 $stamp" }
            var info = ScriptInfoOps.set(AssScript.default().info, "Title", title)
            info = ScriptInfoOps.set(info, "PlayResX", "1920")
            info = ScriptInfoOps.set(info, "PlayResY", "1080")
            info = ScriptInfoOps.set(info, "WrapStyle", "0")
            info = ScriptInfoOps.set(info, "ScaledBorderAndShadow", "yes")
            info = ScriptInfoOps.set(info, "YCbCr Matrix", "TV.709")

            val defaultStyle = AssStyle(
                name = "Default",
                font = "sans-serif",
                fontSize = 62.0,
                primary = AssColor.WHITE,
                secondary = AssColor(255, 212, 59),
                outline = AssColor.BLACK,
                shadow = AssColor(0, 0, 0, 180),
                outlineWidth = 3.0,
                shadowWidth = 1.5,
            )
            val script = AssScript(
                info = info.toPersistentList(),
                styles = persistentListOf(defaultStyle),
                events = persistentListOf(
                    AssEvent(
                        id = 1L,
                        start = SubTime.ofMillis(0L),
                        end = SubTime.ofMillis(5_000L),
                        style = "Default",
                        text = "",
                    ),
                ),
            )
            val id = repo.createProject(name = title, format = "ass", content = AssFormat.write(script))
            onCreated(id)
        }
    }

    /** 导入外部字幕或歌词文件并打开。 */
    fun importSubtitle(fileName: String, content: String, onImported: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val resolved = SubtitleImport.resolve(fileName, content)
            val id = repo.createProject(name = resolved.name, format = resolved.format, content = content)
            onImported(id)
        }
    }

    fun renameProject(projectId: Long, newName: String) {
        viewModelScope.launch {
            repo.rename(projectId, newName)
        }
    }

    fun duplicateProject(project: Project) {
        viewModelScope.launch {
            val content = repo.getContent(project.id)
            repo.createProject(name = "${project.name} 副本", format = project.format, content = content)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch { repo.delete(projectId) }
    }
}
