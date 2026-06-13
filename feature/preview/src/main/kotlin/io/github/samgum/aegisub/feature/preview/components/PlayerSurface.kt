package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import io.github.samgum.aegisub.feature.preview.Media3VideoPlayer
import io.github.samgum.aegisub.feature.preview.VideoPlayer

/**
 * 视频画面：把 ExoPlayer 绑定到 Media3 PlayerView（AndroidView）。
 * 安全转型仅限预览模块：真实环境 player 为 Media3VideoPlayer；测试/Preview 退化为黑底。
 *
 * @author 伤感咩吖
 */
@Composable
fun PlayerSurface(player: VideoPlayer, modifier: Modifier = Modifier) {
    val exo = (player as? Media3VideoPlayer)?.exoPlayer
    if (exo != null) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                PlayerView(context).apply {
                    this.player = exo
                    useController = false
                }
            },
        )
    } else {
        Box(modifier.fillMaxSize().background(Color.Black))
    }
}
