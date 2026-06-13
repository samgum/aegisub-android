package io.github.samgum.aegisub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.samgum.aegisub.domain.format.TimePrecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户设置仓储：读写持久化偏好（主题 / 导出精度 / 布局）。
 *
 * @author 伤感咩吖
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setExportPrecision(precision: TimePrecision)
    suspend fun setLayoutMode(mode: LayoutMode)
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
            exportPrecision = prefs[KEY_PRECISION]
                ?.let { name -> runCatching { TimePrecision.valueOf(name) }.getOrNull() }
                ?: TimePrecision.AUTO,
            layoutMode = prefs[KEY_LAYOUT]
                ?.let { name -> runCatching { LayoutMode.valueOf(name) }.getOrNull() }
                ?: LayoutMode.AUTO,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    override suspend fun setExportPrecision(precision: TimePrecision) {
        store.edit { it[KEY_PRECISION] = precision.name }
    }

    override suspend fun setLayoutMode(mode: LayoutMode) {
        store.edit { it[KEY_LAYOUT] = mode.name }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_PRECISION = stringPreferencesKey("export_precision")
        val KEY_LAYOUT = stringPreferencesKey("layout_mode")
    }
}
