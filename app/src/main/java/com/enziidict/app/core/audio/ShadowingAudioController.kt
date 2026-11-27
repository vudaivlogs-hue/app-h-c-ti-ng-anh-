package com.enziidict.app.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Shadowing audio capture (.m4a AAC in internal cache), live amplitude metering,
 * playback comparison via MediaPlayer, best-take persistence, and strict lifecycle cleanup.
 */
@Singleton
class ShadowingAudioController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var meterJob: Job? = null
    private var recordStartTimeMs: Long = 0L
    private var activeTempFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPlayingUserAudio = MutableStateFlow(false)
    val isPlayingUserAudio: StateFlow<Boolean> = _isPlayingUserAudio.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val cacheFolder: File
        get() = File(context.cacheDir, "shadowing").apply { mkdirs() }

    private val bestTakesFolder: File
        get() = File(context.filesDir, "shadowing_best").apply { mkdirs() }

    fun getBestTakeFile(itemId: String): File? {
        val file = File(bestTakesFolder, "${sanitizeFileName(itemId)}.m4a")
        return if (file.exists() && file.length() > 0L) file else null
    }

    fun startRecording(itemId: String): Result<File> {
        stopPlayback()
        stopRecordingInternal(discardFile = true)

        return runCatching {
            val outFile = File(cacheFolder, "take_${sanitizeFileName(itemId)}.m4a")
            if (outFile.exists()) outFile.delete()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            activeTempFile = outFile
            recordStartTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L

            meterJob?.cancel()
            meterJob = scope.launch {
                while (isActive && _isRecording.value) {
                    val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    _recordingAmplitude.value = (amp / 22_000f).coerceIn(0f, 1f)
                    _recordingDurationMs.value = (System.currentTimeMillis() - recordStartTimeMs).coerceAtLeast(0L)
                    delay(80L)
                }
            }
            outFile
        }.onFailure {
            stopRecordingInternal(discardFile = true)
        }
    }

    fun stopRecording(): File? {
        return stopRecordingInternal(discardFile = false)
    }

    private fun stopRecordingInternal(discardFile: Boolean): File? {
        meterJob?.cancel()
        meterJob = null
        _isRecording.value = false
        _recordingAmplitude.value = 0f

        val recorder = mediaRecorder
        mediaRecorder = null

        val outFile = activeTempFile
        activeTempFile = null

        var stopSucceeded = false
        if (recorder != null) {
            stopSucceeded = runCatching {
                recorder.stop()
                true
            }.getOrDefault(false)
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }

        if (discardFile || !stopSucceeded || outFile == null || !outFile.exists() || outFile.length() == 0L) {
            runCatching { outFile?.delete() }
            return null
        }
        return outFile
    }

    fun playAudioFile(
        file: File,
        onCompleted: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!file.exists() || file.length() == 0L) {
            onError("Không tìm thấy tệp ghi âm để phát lại.")
            return
        }
        stopPlayback()
        runCatching {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    stopPlayback()
                    onCompleted()
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    onError("Không thể phát tệp ghi âm trên thiết bị.")
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _isPlayingUserAudio.value = true
        }.onFailure { err ->
            stopPlayback()
            onError("Lỗi phát lại ghi âm: ${err.localizedMessage ?: "Audio error"}")
        }
    }

    fun stopPlayback() {
        val player = mediaPlayer
        mediaPlayer = null
        _isPlayingUserAudio.value = false
        if (player != null) {
            runCatching {
                if (player.isPlaying) player.stop()
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }

    fun saveAsBestTake(itemId: String, sourceFile: File): Result<File> {
        return runCatching {
            check(sourceFile.exists() && sourceFile.length() > 0L) {
                "Bản ghi tạm không hợp lệ."
            }
            val dest = File(bestTakesFolder, "${sanitizeFileName(itemId)}.m4a")
            sourceFile.copyTo(dest, overwrite = true)
            dest
        }
    }

    fun playSubtleBeep(highPitch: Boolean = false) {
        runCatching {
            val tone = if (highPitch) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            generator.startTone(tone, 90)
            scope.launch {
                delay(150L)
                runCatching { generator.release() }
            }
        }
    }

    fun releaseAll() {
        stopRecordingInternal(discardFile = true)
        stopPlayback()
        _recordingDurationMs.value = 0L
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .take(48)
            .ifBlank { "clip" }
    }
}
