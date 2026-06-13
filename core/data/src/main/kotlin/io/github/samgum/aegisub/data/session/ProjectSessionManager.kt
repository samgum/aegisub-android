package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.ProjectRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按 projectId 缓存 [ProjectSession]：编辑器屏与预览屏 [open] 同一 projectId 拿到同一实例，
 * 从而共享 script 数据源与撤销栈。@Singleton 进程级缓存，字幕工程内存占用小，暂不主动回收。
 *
 * @author 伤感咩吖
 */
@Singleton
class ProjectSessionManager @Inject constructor(
    private val repo: ProjectRepository,
) {
    private val sessions = mutableMapOf<Long, ProjectSession>()

    /** 返回（或创建并启动）projectId 对应的共享会话。线程安全。 */
    fun open(projectId: Long): ProjectSession = synchronized(sessions) {
        sessions.getOrPut(projectId) {
            ProjectSessionImpl(projectId, repo).also { it.start() }
        }
    }
}
