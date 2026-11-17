package io.github.samgum.aegisub.domain.edit

/**
 * 可绑定热键的动作（完整对齐桌面 samgum/Aegisub SGMY 热键集）。
 * 纯枚举，无平台依赖；键位组合在 UI/数据层定义并可经设置自定义。
 *
 * @author 伤感咩吖
 */
enum class HotkeyAction {
    // 编辑（通用）
    UNDO, REDO, SAVE, EXPORT,
    FIND_REPLACE,
    // 行级操作
    DUPLICATE_LINE, DELETE_LINE, SPLIT_LINE,
    JOIN_KEEP_FIRST, JOIN_CONCAT, JOIN_KARAOKE,
    MOVE_LINE_UP, MOVE_LINE_DOWN, INSERT_BEFORE, INSERT_AFTER,
    COPY_LINES, PASTE_LINES, SELECT_ALL,
    // 导航 / 选择
    SELECT_PREV, SELECT_NEXT, COMMIT_AND_NEXT,
    // 播放 / 定位 / SGMY 倍速
    PLAY_PAUSE, PLAY_LINE, SEEK_BACK, SEEK_FORWARD, FRAME_BACK, FRAME_FORWARD,
    SPEED_DOWN, SPEED_UP, SPEED_RESET,
    // 打轴 / 吸附 / 前后导
    SET_START_TO_POS, SET_END_TO_POS,
    ADD_LEAD_IN, ADD_LEAD_OUT,
    // SGMY 特色工具与视图切换
    FIX_COMMON_ERRORS, LYRIC_SCROLL, CYCLE_TAG_HIDING,
}
