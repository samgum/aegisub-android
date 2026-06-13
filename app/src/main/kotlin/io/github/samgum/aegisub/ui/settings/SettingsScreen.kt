package io.github.samgum.aegisub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.R
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.ThemeMode
import io.github.samgum.aegisub.domain.format.TimePrecision

/**
 * 设置屏：主题 / 导出精度 / 布局 / 语言 / 关于。所有偏好经 DataStore 持久化。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val exportPrecision by viewModel.exportPrecision.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    val langCode by viewModel.langCode.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.settings_section_theme))
            OptionRow(stringResource(R.string.settings_theme_system), selected = themeMode == ThemeMode.SYSTEM) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
            OptionRow(stringResource(R.string.settings_theme_light), selected = themeMode == ThemeMode.LIGHT) { viewModel.setThemeMode(ThemeMode.LIGHT) }
            OptionRow(stringResource(R.string.settings_theme_dark), selected = themeMode == ThemeMode.DARK) { viewModel.setThemeMode(ThemeMode.DARK) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_precision))
            OptionRow(stringResource(R.string.settings_precision_auto), selected = exportPrecision == TimePrecision.AUTO) {
                viewModel.setExportPrecision(TimePrecision.AUTO)
            }
            OptionRow(stringResource(R.string.settings_precision_two), selected = exportPrecision == TimePrecision.TWO_MS) {
                viewModel.setExportPrecision(TimePrecision.TWO_MS)
            }
            OptionRow(stringResource(R.string.settings_precision_three), selected = exportPrecision == TimePrecision.THREE_MS) {
                viewModel.setExportPrecision(TimePrecision.THREE_MS)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_layout))
            OptionRow(stringResource(R.string.settings_layout_auto), selected = layoutMode == LayoutMode.AUTO) { viewModel.setLayoutMode(LayoutMode.AUTO) }
            OptionRow(stringResource(R.string.settings_layout_compact), selected = layoutMode == LayoutMode.COMPACT) { viewModel.setLayoutMode(LayoutMode.COMPACT) }
            OptionRow(stringResource(R.string.settings_layout_expanded), selected = layoutMode == LayoutMode.EXPANDED) { viewModel.setLayoutMode(LayoutMode.EXPANDED) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_language))
            OptionRow(stringResource(R.string.settings_lang_system), selected = langCode == "system") { viewModel.setLangCode("system") }
            OptionRow(stringResource(R.string.settings_lang_zh), selected = langCode == "zh") { viewModel.setLangCode("zh") }
            OptionRow(stringResource(R.string.settings_lang_en), selected = langCode == "en") { viewModel.setLangCode("en") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
