package io.github.samgum.aegisub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.samgum.aegisub.domain.format.TimePrecision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * DataStoreSettingsRepository：默认值/写入持久/非法值容错（主题 / 导出精度 / 布局）。
 *
 * 用 runBlocking 而非 runTest：DataStore 内部用 Dispatchers.IO，runTest 的虚拟时间
 * 不推进 IO，会挂起；runBlocking 真实等待 IO 完成。
 *
 * @author 伤感咩吖
 */
class DataStoreSettingsRepositoryTest {

    @Test fun defaults() = runBlocking {
        val repo = DataStoreSettingsRepository(newStore())
        val s = repo.settings.first()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals(TimePrecision.AUTO, s.exportPrecision)
        assertEquals(LayoutMode.AUTO, s.layoutMode)
    }

    @Test fun set_theme_persists() = runBlocking {
        val repo = DataStoreSettingsRepository(newStore())
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.settings.first().themeMode)
    }

    @Test fun set_export_precision_persists() = runBlocking {
        val repo = DataStoreSettingsRepository(newStore())
        repo.setExportPrecision(TimePrecision.THREE_MS)
        assertEquals(TimePrecision.THREE_MS, repo.settings.first().exportPrecision)
    }

    @Test fun set_layout_mode_persists() = runBlocking {
        val repo = DataStoreSettingsRepository(newStore())
        repo.setLayoutMode(LayoutMode.EXPANDED)
        assertEquals(LayoutMode.EXPANDED, repo.settings.first().layoutMode)
    }

    @Test fun corrupt_theme_falls_back() = runBlocking {
        val store = newStore()
        store.edit { it[stringPreferencesKey("theme_mode")] = "BOGUS" }
        val repo = DataStoreSettingsRepository(store)
        assertEquals(ThemeMode.SYSTEM, repo.settings.first().themeMode)
    }

    @Test fun corrupt_precision_falls_back() = runBlocking {
        val store = newStore()
        store.edit { it[stringPreferencesKey("export_precision")] = "NOPE" }
        val repo = DataStoreSettingsRepository(store)
        assertEquals(TimePrecision.AUTO, repo.settings.first().exportPrecision)
    }

    @Test fun all_three_fields_persist_together() = runBlocking {
        // 单仓储一次写三个字段，一次读全部，确认无串键/覆盖
        val repo = DataStoreSettingsRepository(newStore())
        repo.setThemeMode(ThemeMode.LIGHT)
        repo.setExportPrecision(TimePrecision.TWO_MS)
        repo.setLayoutMode(LayoutMode.COMPACT)
        val s = repo.settings.first()
        assertEquals(ThemeMode.LIGHT, s.themeMode)
        assertEquals(TimePrecision.TWO_MS, s.exportPrecision)
        assertEquals(LayoutMode.COMPACT, s.layoutMode)
    }

    private fun newStore(): DataStore<Preferences> {
        val file = File.createTempFile("test_settings", ".preferences_pb").apply { delete() }
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }
}
