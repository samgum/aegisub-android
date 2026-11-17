package io.github.samgum.aegisub.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.SettingsRepository
import io.github.samgum.aegisub.data.settings.TagDisplayMode
import io.github.samgum.aegisub.data.settings.ThemeMode
import io.github.samgum.aegisub.data.settings.UserSettings
import io.github.samgum.aegisub.domain.format.TimePrecision
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全局偏好设置 ViewModel：管理主题、自适应形态模式、SGMY 溢出检测、打轴参数、HDR 预览优化、AI 开关、语言等全部选项。
 *
 * @author 伤感咩吖
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val themeMode: StateFlow<ThemeMode> = repo.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val langCode: StateFlow<String> = repo.settings
        .map { it.langCode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setExportPrecision(precision: TimePrecision) = viewModelScope.launch { repo.setExportPrecision(precision) }
    fun setLayoutMode(mode: LayoutMode) = viewModelScope.launch { repo.setLayoutMode(mode) }
    fun setTagDisplayMode(mode: TagDisplayMode) = viewModelScope.launch { repo.setTagDisplayMode(mode) }
    fun setHighlightOverflow(enabled: Boolean) = viewModelScope.launch { repo.setHighlightOverflow(enabled) }
    fun setCpsWarningThreshold(cps: Int) = viewModelScope.launch { repo.setCpsWarningThreshold(cps) }
    fun setHdrPreviewOptimize(enabled: Boolean) = viewModelScope.launch { repo.setHdrPreviewOptimize(enabled) }

    fun updateTimingPrefs(
        autoNext: Boolean? = null,
        snapKeyframes: Boolean? = null,
        leadInMs: Int? = null,
        leadOutMs: Int? = null,
    ) {
        viewModelScope.launch {
            repo.updateTimingPrefs(
                autoNext = autoNext,
                snapKeyframes = snapKeyframes,
                leadInMs = leadInMs,
                leadOutMs = leadOutMs,
            )
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        val cur = userSettings.value
        viewModelScope.launch {
            repo.updateAiConfig(
                enabled = enabled,
                providerIndex = cur.aiProviderIndex,
                baseUrl = cur.aiBaseUrl,
                model = cur.aiModel,
                targetLang = cur.aiTargetLang,
                temperature = cur.aiTemperature,
                maxTokens = cur.aiMaxTokens,
                thinking = cur.aiThinking,
                cacheSession = cur.aiCacheSession,
            )
        }
    }

    fun setLangCode(code: String) {
        viewModelScope.launch {
            repo.setLangCode(code)
            AppCompatDelegate.setApplicationLocales(
                when (code) {
                    "zh" -> LocaleListCompat.forLanguageTags("zh")
                    "en" -> LocaleListCompat.forLanguageTags("en")
                    else -> LocaleListCompat.getEmptyLocaleList()
                },
            )
        }
    }
}
