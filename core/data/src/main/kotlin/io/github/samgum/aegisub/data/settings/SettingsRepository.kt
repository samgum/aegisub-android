package io.github.samgum.aegisub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户设置仓储：读写持久化偏好。
 *
 * @author 伤感咩吖
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setThemeMode(mode: ThemeMode)
}

/**
 * 基于 DataStore Preferences 的实现。非法持久化值（如旧版本枚举名）容错回退到默认。
 *
 * @author 伤感咩吖
 */
class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = store.data.map { prefs ->
        UserSettings(
            themeMode = prefs[KEY_THEME]
                ?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
    }
}
