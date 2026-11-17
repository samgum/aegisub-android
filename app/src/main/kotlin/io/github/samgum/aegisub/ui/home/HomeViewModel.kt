package io.github.samgum.aegisub.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.SettingsRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Studio 主屏仪表盘 ViewModel：支持新建标准 1080p ASS 工程、双语/卡拉OK演示工程、外部字幕/歌词导入、复制与删除。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ProjectRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repo.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val layoutMode: StateFlow<LayoutMode> = settingsRepo.settings
        .map { it.layoutMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LayoutMode.AUTO)

    /** 新建干净的 1920×1080 标准 ASS 空白工程并立即进入工作台。 */
    fun createBlankAssProject(customTitle: String = "", onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val stamp = SimpleDateFormat("MMdd-HHmm", Locale.getDefault()).format(Date())
            val title = customTitle.trim().ifEmpty { "新建字幕工程 $stamp" }
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
                        end = SubTime.ofMillis(3_000L),
                        style = "Default",
                        text = "",
                    ),
                ),
            )
            val id = repo.createProject(name = title, format = "ass", content = AssFormat.write(script))
            onCreated(id)
        }
    }

    /** 新建包含 SGMY 特效、卡拉OK音节与双语示例的演示工程，方便立即体验全部功能。 */
    fun createShowcaseProject(onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            var info = ScriptInfoOps.set(AssScript.default().info, "Title", "Aegisub SGMY 演示工程")
            info = ScriptInfoOps.set(info, "PlayResX", "1920")
            info = ScriptInfoOps.set(info, "PlayResY", "1080")
            info = ScriptInfoOps.set(info, "ScaledBorderAndShadow", "yes")

            val mainStyle = AssStyle(
                name = "Default",
                font = "sans-serif",
                fontSize = 64.0,
                primary = AssColor.WHITE,
                secondary = AssColor(56, 189, 248),
                outline = AssColor(15, 23, 42),
                outlineWidth = 3.2,
                shadowWidth = 1.5,
            )
            val noteStyle = AssStyle(
                name = "TopSign",
                font = "sans-serif",
                fontSize = 48.0,
                alignment = 8,
                primary = AssColor(253, 224, 71),
                outline = AssColor.BLACK,
                outlineWidth = 2.5,
            )

            val demo = AssScript(
                info = info.toPersistentList(),
                styles = persistentListOf(mainStyle, noteStyle),
                events = persistentListOf(
                    AssEvent(
                        id = 1L,
                        start = SubTime.ofMillis(500L),
                        end = SubTime.ofMillis(4_000L),
                        style = "Default",
                        text = "{\\fad(250,250)}欢迎使用 Aegisub SGMY Android 专业字幕工作台",
                    ),
                    AssEvent(
                        id = 2L,
                        start = SubTime.ofMillis(1_000L),
                        end = SubTime.ofMillis(4_000L),
                        layer = 2,
                        style = "TopSign",
                        text = "{\\pos(960,110)\\frz-2}左侧工具条支持 8 种可视化打字实时拖拽",
                    ),
                    AssEvent(
                        id = 3L,
                        start = SubTime.ofMillis(4_200L),
                        end = SubTime.ofMillis(8_200L),
                        style = "Default",
                        text = "{\\kf80}支{\\kf80}持{\\kf100}卡{\\kf100}拉{\\kf120}OK{\\kf120}逐{\\kf100}字{\\kf100}扫{\\kf100}描",
                    ),
                    AssEvent(
                        id = 4L,
                        start = SubTime.ofMillis(8_400L),
                        end = SubTime.ofMillis(12_000L),
                        style = "Default",
                        text = "顶栏「SGMY工具」内置：修复常见错误、音乐滚动歌词生成、简繁转换与日语注音",
                    ),
                ),
            )
            val id = repo.createProject(
                name = "Aegisub SGMY 全功能演示",
                format = "ass",
                content = AssFormat.write(demo),
            )
            onCreated(id)
        }
    }

    /** 导入外部字幕或歌词（ASS/SSA/SRT/VTT/TTML/LRC/SUB/TXT）并自动打开。 */
    fun importSubtitle(fileName: String, content: String, onImported: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val resolved = SubtitleImport.resolve(fileName, content)
            val id = repo.createProject(name = resolved.name, format = resolved.format, content = content)
            onImported(id)
        }
    }

    fun duplicateProject(project: Project) {
        viewModelScope.launch {
            val content = repo.getContent(project.id)
            repo.createProject(name = "${project.name} (副本)", format = project.format, content = content)
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch { repo.delete(projectId) }
    }
}
