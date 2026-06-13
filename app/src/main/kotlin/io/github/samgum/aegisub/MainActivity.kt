package io.github.samgum.aegisub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.samgum.aegisub.navigation.AppNavigation

/**
 * 应用入口。Hilt 注入 + Compose 启动到导航图（主题在 AppNavigation 内按用户偏好套用）。
 *
 * @author 伤感咩吖
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation()
        }
    }
}
