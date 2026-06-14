package io.github.samgum.aegisub.data.hotkeys

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.settings.HotkeyConfigRepository
import io.github.samgum.aegisub.domain.edit.HotkeyAction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 键位组合（修饰键 + 键码）。键码用 Compose [Key.keyCode]（= Android KeyEvent 键码，稳定）。
 *
 * @author 伤感咩吖
 */
data class KeyCombo(val ctrl: Boolean, val shift: Boolean, val alt: Boolean, val keyCode: Long) {
    fun encode(): String = "${if (ctrl) 1 else 0}|${if (shift) 1 else 0}|${if (alt) 1 else 0}|$keyCode"

    fun display(): String {
        val parts = mutableListOf<String>()
        if (ctrl) parts += "Ctrl"
        if (shift) parts += "Shift"
        if (alt) parts += "Alt"
        parts += keyName(keyCode)
        return parts.joinToString("+")
    }

    companion object {
        fun decode(s: String): KeyCombo? {
            val p = s.split("|")
            if (p.size != 4) return null
            return KeyCombo(p[0] == "1", p[1] == "1", p[2] == "1", p[3].toLongOrNull() ?: return null)
        }
    }
}

/** 键码 → 可读名。 */
fun keyName(keyCode: Long): String = when (keyCode) {
    in 29L..54L -> ('A' + (keyCode - 29).toInt()).toString()
    in 7L..16L -> ('0' + ((keyCode - 7) % 10).toInt()).toString()
    Key.Spacebar.keyCode -> "Space"
    Key.DirectionLeft.keyCode -> "←"
    Key.DirectionRight.keyCode -> "→"
    Key.DirectionUp.keyCode -> "↑"
    Key.DirectionDown.keyCode -> "↓"
    Key.Delete.keyCode -> "Del"
    Key.Backspace.keyCode -> "⌫"
    Key.Enter.keyCode -> "↵"
    Key.Tab.keyCode -> "Tab"
    else -> "#$keyCode"
}

/**
 * 默认热键表（仿桌面 Aegisub 默认约定）。
 * 编辑类用 Ctrl/Shift/Alt 组合避免与文本输入冲突；打轴 W/A/S/D 仅预览屏处理。
 */
val DEFAULT_HOTKEYS: Map<HotkeyAction, KeyCombo> = mapOf(
    HotkeyAction.UNDO to KeyCombo(true, false, false, Key.Z.keyCode),
    HotkeyAction.REDO to KeyCombo(true, false, false, Key.Y.keyCode),
    HotkeyAction.SAVE to KeyCombo(true, false, false, Key.S.keyCode),
    HotkeyAction.EXPORT to KeyCombo(true, false, false, Key.E.keyCode),
    HotkeyAction.FIND_REPLACE to KeyCombo(true, false, false, Key.F.keyCode),
    HotkeyAction.DUPLICATE_LINE to KeyCombo(true, false, false, Key.D.keyCode),
    HotkeyAction.DELETE_LINE to KeyCombo(true, false, false, Key.Delete.keyCode),
    HotkeyAction.SPLIT_LINE to KeyCombo(true, true, false, Key.K.keyCode),
    HotkeyAction.JOIN_KEEP_FIRST to KeyCombo(true, false, false, Key.J.keyCode),
    HotkeyAction.JOIN_CONCAT to KeyCombo(true, true, false, Key.J.keyCode),
    HotkeyAction.MOVE_LINE_UP to KeyCombo(false, false, true, Key.DirectionUp.keyCode),
    HotkeyAction.MOVE_LINE_DOWN to KeyCombo(false, false, true, Key.DirectionDown.keyCode),
    HotkeyAction.INSERT_AFTER to KeyCombo(true, true, false, Key.I.keyCode),
    HotkeyAction.SELECT_PREV to KeyCombo(false, false, false, Key.W.keyCode),
    HotkeyAction.SELECT_NEXT to KeyCombo(false, false, false, Key.D.keyCode),
    HotkeyAction.PLAY_PAUSE to KeyCombo(false, false, false, Key.Spacebar.keyCode),
    HotkeyAction.SEEK_BACK to KeyCombo(false, false, false, Key.DirectionLeft.keyCode),
    HotkeyAction.SEEK_FORWARD to KeyCombo(false, false, false, Key.DirectionRight.keyCode),
    HotkeyAction.FRAME_BACK to KeyCombo(true, false, false, Key.DirectionLeft.keyCode),
    HotkeyAction.FRAME_FORWARD to KeyCombo(true, false, false, Key.DirectionRight.keyCode),
    HotkeyAction.SET_START_TO_POS to KeyCombo(false, false, false, Key.A.keyCode),
    HotkeyAction.SET_END_TO_POS to KeyCombo(false, false, false, Key.S.keyCode),
)

/** 热键控制器：持有当前绑定表，匹配按键事件 → 动作。 */
class HotkeyController(val map: Map<HotkeyAction, KeyCombo>) {
    fun match(event: KeyEvent): HotkeyAction? {
        if (event.type != KeyEventType.KeyDown) return null
        val combo = KeyCombo(event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.key.keyCode)
        return map.entries.firstOrNull { it.value == combo }?.key
    }
}

/**
 * 热键 ViewModel：读取用户自定义绑定，与默认表合并；提供改键/重置。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class HotkeyViewModel @Inject constructor(
    private val repo: HotkeyConfigRepository,
) : ViewModel() {
    val hotkeys: StateFlow<Map<HotkeyAction, KeyCombo>> = repo.bindings
        .map { custom ->
            val merged = DEFAULT_HOTKEYS.toMutableMap()
            custom.forEach { (name, comboStr) ->
                runCatching { HotkeyAction.valueOf(name) }.getOrNull()?.let { action ->
                    KeyCombo.decode(comboStr)?.let { merged[action] = it }
                }
            }
            merged.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_HOTKEYS)

    fun setBinding(action: HotkeyAction, combo: KeyCombo) {
        viewModelScope.launch { repo.setBinding(action.name, combo.encode()) }
    }

    fun resetAll() {
        viewModelScope.launch { repo.resetAll() }
    }
}

/** 屏级获取热键控制器（订阅当前绑定）。app / feature 共用。 */
@Composable
fun rememberHotkeyController(): HotkeyController {
    val vm: HotkeyViewModel = hiltViewModel()
    val map by vm.hotkeys.collectAsStateWithLifecycle()
    return remember(map) { HotkeyController(map) }
}

/** 设置屏改键用：暴露当前绑定 + 改键/重置。 */
@Composable
fun rememberHotkeyEditor(): HotkeyViewModel = hiltViewModel()
