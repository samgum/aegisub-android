package io.github.samgum.aegisub

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import io.github.samgum.aegisub.navigation.AppNavigation

/**
 * 应用入口。Hilt 注入 + Compose 启动到导航图（主题在 AppNavigation 内按用户偏好套用）。
 *
 * 继承 [AppCompatActivity] 以使 [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales]
 * 在全 API（含 minSdk 26）生效——ComponentActivity 不会挂载 AppCompatDelegate，导致 per-app
 * 语言切换在 Android 13 以下不生效。
 *
 * @author 伤感咩吖
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}
