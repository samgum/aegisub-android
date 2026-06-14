package io.github.samgum.aegisub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 热键配置仓储：存储动作 → 键位组合（编码串 "ctrl|shift|alt|keyCode"）。
 * 编码层与平台 Key 解耦；UI 层负责 Compose Key ↔ 编码串转换。
 *
 * @author 伤感咩吖
 */
interface HotkeyConfigRepository {
    /** 当前全部绑定（动作名 → 编码串）。缺失的动作由 UI 层用默认表补齐。 */
    val bindings: Flow<Map<String, String>>
    suspend fun setBinding(action: String, combo: String)
    suspend fun resetAll()
}

/**
 * 基于 DataStore Preferences 的实现：每个动作一个 string key。
 *
 * @author 伤感咩吖
 */
class DataStoreHotkeyConfigRepository(
    private val store: DataStore<Preferences>,
) : HotkeyConfigRepository {

    override val bindings: Flow<Map<String, String>> = store.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            (value as? String)?.let { key.name to it }
        }.toMap()
    }

    override suspend fun setBinding(action: String, combo: String) {
        store.edit { it[stringPreferencesKey(action)] = combo }
    }

    override suspend fun resetAll() {
        store.edit { it.clear() }
    }
}
