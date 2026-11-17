package io.github.samgum.aegisub.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.samgum.aegisub.domain.format.TimePrecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 用户设置仓储：读写持久化偏好（主题 / 导出精度 / 布局 / SGMY 溢出 / 打轴 / AI 分析 / 全局样式库）。
 *
 * @author 伤感咩吖
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setExportPrecision(precision: TimePrecision)
    suspend fun setLayoutMode(mode: LayoutMode)
    suspend fun setLangCode(code: String)
    suspend fun setTagDisplayMode(mode: TagDisplayMode) {}
    suspend fun setHighlightOverflow(enabled: Boolean) {}
    suspend fun setCpsWarningThreshold(cps: Int) {}
    suspend fun updateTimingPrefs(
        autoCommit: Boolean? = null,
        autoNext: Boolean? = null,
        snapKeyframes: Boolean? = null,
        leadInMs: Int? = null,
        leadOutMs: Int? = null,
    ) {}
    suspend fun setHdrPreviewOptimize(enabled: Boolean) {}
    suspend fun updateAiConfig(
        enabled: Boolean,
        providerIndex: Int,
        baseUrl: String,
        model: String,
        targetLang: String,
        temperature: Float,
        maxTokens: Int,
        thinking: Boolean,
        cacheSession: Boolean,
    ) {}
    suspend fun setStyleStorageLines(lines: List<String>) {}
}

/**
 * 基于 DataStore Preferences 的实现。非法持久化值容错回退到默认。
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
            langCode = prefs[KEY_LANG] ?: "system",
            tagDisplayMode = prefs[KEY_TAG_DISPLAY]
                ?.let { name -> runCatching { TagDisplayMode.valueOf(name) }.getOrNull() }
                ?: TagDisplayMode.SIMPLIFY_TAGS,
            highlightOverflow = prefs[KEY_OVERFLOW] ?: true,
            cpsWarningThreshold = prefs[KEY_CPS_WARN] ?: 22,
            autoCommitTiming = prefs[KEY_AUTO_COMMIT] ?: true,
            autoNextOnCommit = prefs[KEY_AUTO_NEXT] ?: true,
            snapToKeyframes = prefs[KEY_SNAP_KF] ?: true,
            defaultLeadInMs = prefs[KEY_LEAD_IN] ?: 80,
            defaultLeadOutMs = prefs[KEY_LEAD_OUT] ?: 120,
            hdrPreviewOptimize = prefs[KEY_HDR_OPT] ?: true,
            aiEnabled = prefs[KEY_AI_ENABLED] ?: true,
            aiProviderIndex = prefs[KEY_AI_PROVIDER] ?: 0,
            aiBaseUrl = prefs[KEY_AI_URL] ?: "https://api.deepseek.com",
            aiModel = prefs[KEY_AI_MODEL] ?: "deepseek-chat",
            aiTargetLang = prefs[KEY_AI_LANG] ?: "Chinese",
            aiTemperature = prefs[KEY_AI_TEMP] ?: 0.3f,
            aiMaxTokens = prefs[KEY_AI_MAX_TOKENS] ?: 2048,
            aiThinking = prefs[KEY_AI_THINKING] ?: false,
            aiCacheSession = prefs[KEY_AI_CACHE] ?: true,
            styleStorageLines = prefs[KEY_STYLE_STORAGE]
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.startsWith("Style:", ignoreCase = true) }
                ?: emptyList(),
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

    override suspend fun setLangCode(code: String) {
        store.edit { it[KEY_LANG] = code }
    }

    override suspend fun setTagDisplayMode(mode: TagDisplayMode) {
        store.edit { it[KEY_TAG_DISPLAY] = mode.name }
    }

    override suspend fun setHighlightOverflow(enabled: Boolean) {
        store.edit { it[KEY_OVERFLOW] = enabled }
    }

    override suspend fun setCpsWarningThreshold(cps: Int) {
        store.edit { it[KEY_CPS_WARN] = cps.coerceIn(8, 60) }
    }

    override suspend fun updateTimingPrefs(
        autoCommit: Boolean?,
        autoNext: Boolean?,
        snapKeyframes: Boolean?,
        leadInMs: Int?,
        leadOutMs: Int?,
    ) {
        store.edit { p ->
            autoCommit?.let { p[KEY_AUTO_COMMIT] = it }
            autoNext?.let { p[KEY_AUTO_NEXT] = it }
            snapKeyframes?.let { p[KEY_SNAP_KF] = it }
            leadInMs?.let { p[KEY_LEAD_IN] = it.coerceIn(0, 2000) }
            leadOutMs?.let { p[KEY_LEAD_OUT] = it.coerceIn(0, 2000) }
        }
    }

    override suspend fun setHdrPreviewOptimize(enabled: Boolean) {
        store.edit { it[KEY_HDR_OPT] = enabled }
    }

    override suspend fun updateAiConfig(
        enabled: Boolean,
        providerIndex: Int,
        baseUrl: String,
        model: String,
        targetLang: String,
        temperature: Float,
        maxTokens: Int,
        thinking: Boolean,
        cacheSession: Boolean,
    ) {
        store.edit { p ->
            p[KEY_AI_ENABLED] = enabled
            p[KEY_AI_PROVIDER] = providerIndex
            p[KEY_AI_URL] = baseUrl.trim()
            p[KEY_AI_MODEL] = model.trim()
            p[KEY_AI_LANG] = targetLang.trim()
            p[KEY_AI_TEMP] = temperature.coerceIn(0f, 2f)
            p[KEY_AI_MAX_TOKENS] = maxTokens.coerceIn(128, 32_000)
            p[KEY_AI_THINKING] = thinking
            p[KEY_AI_CACHE] = cacheSession
        }
    }

    override suspend fun setStyleStorageLines(lines: List<String>) {
        store.edit { it[KEY_STYLE_STORAGE] = lines.joinToString("\n") }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_PRECISION = stringPreferencesKey("export_precision")
        val KEY_LAYOUT = stringPreferencesKey("layout_mode")
        val KEY_LANG = stringPreferencesKey("lang_code")
        val KEY_TAG_DISPLAY = stringPreferencesKey("tag_display_mode")
        val KEY_OVERFLOW = booleanPreferencesKey("highlight_overflow")
        val KEY_CPS_WARN = intPreferencesKey("cps_warning_threshold")
        val KEY_AUTO_COMMIT = booleanPreferencesKey("auto_commit_timing")
        val KEY_AUTO_NEXT = booleanPreferencesKey("auto_next_on_commit")
        val KEY_SNAP_KF = booleanPreferencesKey("snap_to_keyframes")
        val KEY_LEAD_IN = intPreferencesKey("default_lead_in_ms")
        val KEY_LEAD_OUT = intPreferencesKey("default_lead_out_ms")
        val KEY_HDR_OPT = booleanPreferencesKey("hdr_preview_optimize")
        val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val KEY_AI_PROVIDER = intPreferencesKey("ai_provider_index")
        val KEY_AI_URL = stringPreferencesKey("ai_base_url")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_AI_LANG = stringPreferencesKey("ai_target_lang")
        val KEY_AI_TEMP = floatPreferencesKey("ai_temperature")
        val KEY_AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val KEY_AI_THINKING = booleanPreferencesKey("ai_thinking")
        val KEY_AI_CACHE = booleanPreferencesKey("ai_cache_session")
        val KEY_STYLE_STORAGE = stringPreferencesKey("style_storage_lines")
    }
}
