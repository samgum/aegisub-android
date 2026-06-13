package io.github.samgum.aegisub.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.data.settings.ThemeMode
import io.github.samgum.aegisub.domain.format.TimePrecision

/**
 * 设置屏：主题模式 / ASS 导出时间精度 / 编辑器布局。所有偏好经 DataStore 持久化。
 *
 * @author 伤感咩吖
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val exportPrecision by viewModel.exportPrecision.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            SectionTitle("主题")
            OptionRow("跟随系统", selected = themeMode == ThemeMode.SYSTEM) { viewModel.setThemeMode(ThemeMode.SYSTEM) }
            OptionRow("浅色", selected = themeMode == ThemeMode.LIGHT) { viewModel.setThemeMode(ThemeMode.LIGHT) }
            OptionRow("深色", selected = themeMode == ThemeMode.DARK) { viewModel.setThemeMode(ThemeMode.DARK) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("导出时间精度")
            OptionRow("自动（ASS 标准：厘秒）", selected = exportPrecision == TimePrecision.AUTO) {
                viewModel.setExportPrecision(TimePrecision.AUTO)
            }
            OptionRow("厘秒（H:MM:SS.cc，2 位）", selected = exportPrecision == TimePrecision.TWO_MS) {
                viewModel.setExportPrecision(TimePrecision.TWO_MS)
            }
            OptionRow("毫秒（H:MM:SS.mmm，3 位）", selected = exportPrecision == TimePrecision.THREE_MS) {
                viewModel.setExportPrecision(TimePrecision.THREE_MS)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SectionTitle("编辑器布局")
            OptionRow("跟随屏宽自动", selected = layoutMode == LayoutMode.AUTO) { viewModel.setLayoutMode(LayoutMode.AUTO) }
            OptionRow("紧凑（列表 + 底栏）", selected = layoutMode == LayoutMode.COMPACT) { viewModel.setLayoutMode(LayoutMode.COMPACT) }
            OptionRow("双栏（列表 | 详情）", selected = layoutMode == LayoutMode.EXPANDED) { viewModel.setLayoutMode(LayoutMode.EXPANDED) }
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
