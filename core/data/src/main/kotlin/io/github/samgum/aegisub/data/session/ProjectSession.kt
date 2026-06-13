package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import kotlinx.coroutines.flow.StateFlow

/**
 * 一个字幕工程的共享编辑会话：单一 script 数据源 + 撤销栈 + 防抖回写。
 * 编辑器屏与预览屏通过 [ProjectSessionManager.open] 拿到同一实例，
 * 任何一端编辑，两端订阅的 [script] 自动一致。
 *
 * 加载是异步的：构造后 [script] 为 null，加载完成才有值；[errorMessage] 非空表示加载失败。
 *
 * @author 伤感咩吖
 */
interface ProjectSession {
    val projectId: Long

    /** 当前脚本；null 表示尚未加载完成。 */
    val script: StateFlow<AssScript?>

    /** 加载错误信息；null 表示无错误。 */
    val errorMessage: StateFlow<String?>

    val canUndo: StateFlow<Boolean>
    val canRedo: StateFlow<Boolean>

    /** 对指定 id 的事件应用变换，产出新不可变脚本并入撤销栈（触发防抖回写）。 */
    fun editEvent(eventId: Long, transform: (AssEvent) -> AssEvent)

    /** 对所有事件逐条应用变换，一次提交（一个撤销点，用于查找替换等批量编辑）。 */
    fun editAllEvents(transform: (AssEvent) -> AssEvent)

    /** 对整个事件列表应用任意变换（可增删、改顺序），一次提交（一个撤销点，用于删除空行/时间偏移等）。 */
    fun editEvents(transform: (List<AssEvent>) -> List<AssEvent>)

    /** 对整个样式列表应用任意变换（增删改），一次提交（一个撤销点，用于样式编辑器）。 */
    fun editStyles(transform: (List<AssStyle>) -> List<AssStyle>)

    /** 对整个 [Script Info] 键值列表应用任意变换（增删改），一次提交（一个撤销点，用于脚本属性编辑）。 */
    fun editInfo(transform: (List<AssInfo>) -> List<AssInfo>)

    /** 对整个脚本应用任意变换（事件+样式+信息），一次提交（一个撤销点，用于分辨率重采样等整脚本操作）。 */
    fun editScript(transform: (AssScript) -> AssScript)

    /**
     * 用序列化内容覆盖当前脚本，作为一个新的撤销点入栈（用于「历史版本恢复」）。
     * 解析失败时忽略。不重置撤销历史——恢复后仍可撤销回恢复前状态。
     */
    fun restoreFromContent(content: String)

    fun undo()
    fun redo()
}
