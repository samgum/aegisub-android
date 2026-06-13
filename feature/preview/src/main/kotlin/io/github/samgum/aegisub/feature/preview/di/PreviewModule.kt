package io.github.samgum.aegisub.feature.preview.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.samgum.aegisub.feature.preview.Media3VideoPlayer
import io.github.samgum.aegisub.feature.preview.VideoPlayer
import io.github.samgum.aegisub.feature.preview.audio.MediaCodecWaveformExtractor
import io.github.samgum.aegisub.feature.preview.audio.WaveformExtractor

/**
 * 预览模块 DI 绑定。VideoPlayer 不加 @Singleton：每个 PreviewViewModel 注入新实例，
 * 随 VM onCleared() 释放，避免跨屏共享已释放的 ExoPlayer。
 *
 * @author 伤感咩吖
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PreviewModule {
    @Binds
    abstract fun bindVideoPlayer(impl: Media3VideoPlayer): VideoPlayer

    @Binds
    abstract fun bindWaveformExtractor(impl: MediaCodecWaveformExtractor): WaveformExtractor
}
