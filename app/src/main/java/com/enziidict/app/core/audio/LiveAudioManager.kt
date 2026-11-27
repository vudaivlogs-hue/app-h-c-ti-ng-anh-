package com.enziidict.app.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Full-duplex PCM audio engine for Gemini Live API:
 * - Captures 16 kHz mono 16-bit PCM via AudioRecord with hardware AcousticEchoCanceler (AEC)
 * - Streams 24 kHz mono 16-bit PCM responses via AudioTrack
 * - Emits normalized mic & AI output amplitude levels (0f..1f) for real-time waveform rendering
 */
@Singleton
class LiveAudioManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var audioTrack: AudioTrack? = null

    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var playbackChannel: Channel<ByteArray>? = null

    private val isRunning = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _aiAmplitude = MutableStateFlow(0f)
    val aiAmplitude: StateFlow<Float> = _aiAmplitude.asStateFlow()

    private val _aecEnabled = MutableStateFlow(false)
    val aecEnabled: StateFlow<Boolean> = _aecEnabled.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(onPcm16kChunk: (ByteArray) -> Unit): Result<Unit> {
        stopAll()

        return runCatching {
            val minRecBuf = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(INPUT_CHUNK_BYTES * 2)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minRecBuf * 2
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "Không thể khởi tạo AudioRecord (16kHz PCM)."
            }
            audioRecord = recorder

            // Enable hardware Acoustic Echo Cancellation & Noise Suppression if supported
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = runCatching {
                    AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }.getOrNull()
                _aecEnabled.value = echoCanceler?.enabled == true
            } else {
                _aecEnabled.value = false
            }

            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = runCatching {
                    NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                        enabled = true
                    }
                }.getOrNull()
            }

            val minPlayBuf = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(OUTPUT_MIN_BUFFER_BYTES)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minPlayBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack = track

            isRunning.set(true)
            recorder.startRecording()
            track.play()

            val channel = Channel<ByteArray>(capacity = Channel.UNLIMITED)
            playbackChannel = channel

            playbackJob = scope.launch {
                for (chunk in channel) {
                    if (!isRunning.get()) break
                    _aiAmplitude.value = computePcm16Amplitude(chunk, chunk.size)
                    runCatching {
                        audioTrack?.write(chunk, 0, chunk.size)
                    }
                }
                _aiAmplitude.value = 0f
            }

            captureJob = scope.launch {
                val buffer = ByteArray(INPUT_CHUNK_BYTES)
                while (isActive && isRunning.get()) {
                    val readBytes = recorder.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        if (isMuted.get()) {
                            _micAmplitude.value = 0f
                        } else {
                            _micAmplitude.value = computePcm16Amplitude(buffer, readBytes)
                            onPcm16kChunk(buffer.copyOf(readBytes))
                        }
                    }
                }
                _micAmplitude.value = 0f
            }
        }.onFailure {
            stopAll()
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
        if (muted) {
            _micAmplitude.value = 0f
        }
    }

    fun enqueueAiAudioPcm24k(pcmBytes: ByteArray) {
        if (!isRunning.get() || pcmBytes.isEmpty()) return
        playbackChannel?.trySend(pcmBytes)
    }

    /**
     * Immediately flushes buffered AI audio when the learner barges in / interrupts the AI.
     */
    fun flushPlayback() {
        val ch = playbackChannel
        if (ch != null) {
            while (ch.tryReceive().isSuccess) {
                // Drain queued audio chunks
            }
        }
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            if (isRunning.get()) {
                audioTrack?.play()
            }
        }
        _aiAmplitude.value = 0f
    }

    fun stopAll() {
        isRunning.set(false)
        isMuted.set(false)

        // Stop hardware first so blocking AudioRecord.read / AudioTrack.write unblock immediately
        runCatching { audioRecord?.stop() }
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        }

        captureJob?.cancel()
        captureJob = null

        playbackChannel?.close()
        playbackChannel = null
        playbackJob?.cancel()
        playbackJob = null

        runCatching { echoCanceler?.release() }
        echoCanceler = null
        _aecEnabled.value = false

        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null

        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { audioTrack?.release() }
        audioTrack = null

        _micAmplitude.value = 0f
        _aiAmplitude.value = 0f
    }

    private fun computePcm16Amplitude(bytes: ByteArray, length: Int): Float {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0f
        var sumSquares = 0.0
        var idx = 0
        while (idx + 1 < length) {
            val lo = bytes[idx].toInt() and 0xFF
            val hi = bytes[idx + 1].toInt()
            val sample = (hi shl 8) or lo
            val normalized = abs(sample) / 32768.0
            sumSquares += normalized * normalized
            idx += 2
        }
        val rms = sqrt(sumSquares / sampleCount)
        return (rms * 3.5).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000
        private const val INPUT_CHUNK_BYTES = 3200 // 100ms of 16kHz 16-bit mono PCM
        private const val OUTPUT_MIN_BUFFER_BYTES = 4800
    }
}
