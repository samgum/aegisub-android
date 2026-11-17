package io.github.samgum.aegisub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.samgum.aegisub.data.settings.ThemeMode

private val StudioDarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF06202E),
    primaryContainer = Color(0xFF0E3A53),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF052E1E),
    secondaryContainer = Color(0xFF0F3D2C),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF362200),
    tertiaryContainer = Color(0xFF4F3404),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = Color(0xFFF87171),
    errorContainer = Color(0xFF52181B),
    onErrorContainer = Color(0xFFFECACA),
    background = Color(0xFF0F131A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF151B24),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1F2735),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF0B0E14),
    surfaceContainerLow = Color(0xFF131821),
    surfaceContainer = Color(0xFF19202C),
    surfaceContainerHigh = Color(0xFF212A39),
    surfaceContainerHighest = Color(0xFF2B3648),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF293445),
)

private val StudioLightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF072F49),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
)

/**
 * Aegisub SGMY 专业工作台主题：深邃低疲劳石墨暗色与高对比浅色双模式。
 *
 * @author 伤感咩吖
 */
@Composable
fun AegisubTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) StudioDarkColors else StudioLightColors,
        content = content,
    )
}
