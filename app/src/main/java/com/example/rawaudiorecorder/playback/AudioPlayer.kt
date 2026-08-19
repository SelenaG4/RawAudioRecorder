package com.example.rawaudiorecorder.playback

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays back saved WAV files.
 *
 * Deliberately uses [MediaPlayer] rather than AudioTrack: playback here is a
 * convenience feature, not part of the low-level capture demonstration, and
 * MediaPlayer handles WAV headers, seeking and completion for free.
 *
 * All state is exposed through a single [StateFlow] so Compose can observe it.
 * Call [release] from the owning Activity/ViewModel when it goes away.
 */
class AudioPlayer {

    data class State(
        /** Absolute path of the file loaded into the player, or null when idle. */
        val path: String? = null,
        val isPlaying: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0
    ) {
        val progress: Float
            get() = if (durationMs <= 0) 0f
                    else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var ticker: Job? = null

    /** True when [file] is the currently loaded track and it is playing. */
    fun isPlaying(file: File): Boolean {
        val s = _state.value
        return s.isPlaying && s.path == file.absolutePath
    }

    /**
     * One-tap behaviour for a play button: starts [file], or pauses/resumes it
     * if it is already the loaded track.
     */
    fun toggle(file: File) {
        val s = _state.value
        if (s.path == file.absolutePath && player != null) {
            if (s.isPlaying) pause() else resume()
        } else {
            play(file)
        }
    }

    fun play(file: File) {
        teardown()
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
        } catch (e: Exception) {
            mp.release()
            _state.value = State()
            return
        }
        mp.setOnCompletionListener { done ->
            ticker?.cancel()
            done.seekTo(0)
            _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
        }
        player = mp
        mp.start()
        _state.value = State(
            path = file.absolutePath,
            isPlaying = true,
            positionMs = 0,
            durationMs = mp.duration.coerceAtLeast(0)
        )
        startTicker()
    }

    fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) mp.pause()
        ticker?.cancel()
        _state.value = _state.value.copy(isPlaying = false, positionMs = mp.currentPosition)
    }

    fun resume() {
        val mp = player ?: return
        mp.start()
        _state.value = _state.value.copy(isPlaying = true)
        startTicker()
    }

    fun seekTo(ms: Int) {
        val mp = player ?: return
        val target = ms.coerceIn(0, mp.duration)
        mp.seekTo(target)
        _state.value = _state.value.copy(positionMs = target)
    }

    /** Seek using a 0f..1f fraction, for slider callbacks. */
    fun seekToFraction(fraction: Float) {
        val d = _state.value.durationMs
        if (d > 0) seekTo((fraction.coerceIn(0f, 1f) * d).toInt())
    }

    /** Stops playback and unloads the track. */
    fun stop() {
        teardown()
        _state.value = State()
    }

    /**
     * Stops playback only if [file] is the loaded track. Call this before
     * renaming or deleting a file so the player never holds a stale handle.
     */
    fun stopIfPlaying(file: File) {
        if (_state.value.path == file.absolutePath) stop()
    }

    /** Release native resources. Safe to call more than once. */
    fun release() {
        teardown()
        _state.value = State()
    }

    private fun teardown() {
        ticker?.cancel()
        ticker = null
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        player = null
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val mp = player ?: break
                if (!mp.isPlaying) break
                _state.value = _state.value.copy(positionMs = mp.currentPosition)
                delay(100)
            }
        }
    }
}
