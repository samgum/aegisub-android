package io.github.samgum.aegisub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * DataStoreSettingsRepository：默认值/写入持久/非法值容错。
 *
 * 用 runBlocking 而非 runTest：DataStore 内部用 Dispatchers.IO，runTest 的虚拟时间
 * 不推进 IO，会挂起；runBlocking 真实等待 IO 完成。
 *
 * @author 伤感咩吖
 */
class DataStoreSettingsRepositoryTest {

    @Test fun defaults_to_system() = runBlocking {
        val repo = DataStoreSettingsRepository(newStore())
        assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
    }

    @Test fun set_theme_persists() = runBlocking {
        val store = newStore()
        val repo = DataStoreSettingsRepository(store)
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.settings.first().themeMode)
    }

    @Test fun corrupt_value_falls_back_to_system() = runBlocking {
        val store = newStore()
        // 直接写入非法枚举名，模拟旧版本/手改损坏
        store.edit { it[stringPreferencesKey("theme_mode")] = "BOGUS" }
        val repo = DataStoreSettingsRepository(store)
        assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
    }

    private fun newStore(): DataStore<Preferences> {
        val file = File.createTempFile("test_settings", ".preferences_pb").apply { delete() }
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }
}
