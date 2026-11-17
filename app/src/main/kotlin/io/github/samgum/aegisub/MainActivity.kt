package io.github.samgum.aegisub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import io.github.samgum.aegisub.navigation.AppNavigation

/**
 * 应用主入口（支持系统文件管理器直接打开 `.ass / .ssa / .srt / .vtt / .ttml / .lrc / .sub` 文件）。
 *
 * 继承 [AppCompatActivity] 以使 [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]
 * 在全 API（含 minSdk 26）生效。
 *
 * @author 伤感咩吖
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var incomingSubtitleUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) {
            incomingSubtitleUri = intent.data
        }
        setContent {
            AppNavigation(
                externalSubtitleUri = incomingSubtitleUri,
                onExternalUriConsumed = { incomingSubtitleUri = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            incomingSubtitleUri = intent.data
        }
    }
}
