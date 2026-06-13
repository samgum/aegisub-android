package io.github.samgum.aegisub.domain.undo

/**
 * 撤销/重做栈接口。T 须为不可变类型（结构共享由不可变集合保证）。
 *
 * @author 伤感咩吖
 */
interface UndoStack<T> {
    val current: T
    val canUndo: Boolean
    val canRedo: Boolean
    fun commit(next: T, description: String): Int
    fun undo(): T?
    fun redo(): T?
}

/**
 * 基于快照的 CoW 实现：保存不可变版本引用，有界环形缓冲。
 * 替代 Aegisub 原版整文件快照（10 万行会 OOM），结构共享使每次提交仅 O(差分)。
 */
class SnapshotUndoStack<T>(
    initial: T,
    private val limit: Int = 100,
) : UndoStack<T> {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    override var current: T = initial
        private set

    override val canUndo: Boolean get() = past.isNotEmpty()
    override val canRedo: Boolean get() = future.isNotEmpty()

    override fun commit(next: T, description: String): Int {
        past.addLast(current)
        // limit = 最大可撤销深度：past 最多保留 limit 条历史版本
        while (past.size > limit) past.removeFirst()
        future.clear()
        current = next
        return past.size
    }

    override fun undo(): T? {
        if (past.isEmpty()) return null
        future.addLast(current)
        current = past.removeLast()
        return current
    }

    override fun redo(): T? {
        if (future.isEmpty()) return null
        past.addLast(current)
        current = future.removeLast()
        return current
    }
}
