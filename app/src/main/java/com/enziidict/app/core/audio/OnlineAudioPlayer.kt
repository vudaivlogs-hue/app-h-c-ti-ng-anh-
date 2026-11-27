package com.enziidict.app.core.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineAudioPlayer @Inject constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null

    fun playUrl(url: String, onFallbackTts: () -> Unit) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            onFallbackTts()
            return
        }

        releasePlayer()

        val player = MediaPlayer()
        mediaPlayer = player
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(trimmed)
            player.setOnPreparedListener { mp ->
                runCatching { mp.start() }.onFailure {
                    releasePlayer()
                    mainHandler.post { onFallbackTts() }
                }
            }
            player.setOnCompletionListener {
                releasePlayer()
            }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer()
                mainHandler.post { onFallbackTts() }
                true
            }
            player.prepareAsync()
        }.onFailure {
            releasePlayer()
            mainHandler.post { onFallbackTts() }
        }
    }

    fun stop() {
        releasePlayer()
    }

    fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        val current = mediaPlayer
        mediaPlayer = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.reset() }
            runCatching { current.release() }
        }
    }
}
