package io.github.samgum.aegisub.domain.edit

/**
 * 可绑定热键的动作（仿桌面 Aegisub 默认热键集）。
 * 纯枚举，无平台依赖；键位组合在 UI 层定义并可经设置修改。
 *
 * @author 伤感咩吖
 */
enum class HotkeyAction {
    // 编辑（通用）
    UNDO, REDO, SAVE, EXPORT,
    FIND_REPLACE,
    // 行级操作
    DUPLICATE_LINE, DELETE_LINE, SPLIT_LINE,
    JOIN_KEEP_FIRST, JOIN_CONCAT,
    MOVE_LINE_UP, MOVE_LINE_DOWN, INSERT_AFTER,
    // 导航 / 选择
    SELECT_PREV, SELECT_NEXT,
    // 播放 / 定位
    PLAY_PAUSE, SEEK_BACK, SEEK_FORWARD, FRAME_BACK, FRAME_FORWARD,
    // 打轴
    SET_START_TO_POS, SET_END_TO_POS,
}
