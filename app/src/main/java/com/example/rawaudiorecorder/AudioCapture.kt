package com.example.rawaudiorecorder

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class AudioCapture {

    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE_HZ = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_16BIT = 32767f
        const val FFT_SIZE = 1024
    }

    private var audioRecord: AudioRecord? = null
    private var bufferSizeInBytes: Int = 0

    @Volatile
    private var isRecording = false
    private var recordingThread: Thread? = null

    private var pcmFile: File? = null
    private var pcmOutputStream: BufferedOutputStream? = null

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _spectrum = MutableStateFlow(FloatArray(FFT_SIZE / 2))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    // FFT working state
    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat()
    }
    private val fftInput = FloatArray(FFT_SIZE)
    private val fftReal = FloatArray(FFT_SIZE)
    private val fftImag = FloatArray(FFT_SIZE)
    private var fftIndex = 0

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun initialize(): Boolean {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        if (minBufferSize == AudioRecord.ERROR ||
            minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Bad buffer size: $minBufferSize")
            return false
        }
        bufferSizeInBytes = minBufferSize * 4
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT,
            bufferSizeInBytes
        )
        return if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            Log.i(TAG, "AudioRecord ready — ${SAMPLE_RATE_HZ}Hz, buffer=$bufferSizeInBytes bytes")
            true
        } else {
            Log.e(TAG, "AudioRecord failed to initialize (state=${audioRecord?.state})")
            release()
            false
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(outputDir: File) {
        val recorder = audioRecord
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Cannot start — recorder not initialized")
            return
        }
        if (isRecording) return

        val pcm = File(outputDir, "recording_temp.pcm")
        pcmFile = pcm
        pcmOutputStream = BufferedOutputStream(FileOutputStream(pcm))
        fftIndex = 0

        recorder.startRecording()
        isRecording = true

        recordingThread = Thread {
            val audioBuffer = ByteArray(bufferSizeInBytes)
            Log.i(TAG, "Capture loop started")
            while (isRecording) {
                val bytesRead = recorder.read(audioBuffer, 0, audioBuffer.size)
                if (bytesRead > 0) {
                    pcmOutputStream?.write(audioBuffer, 0, bytesRead)

                    var peak = 0
                    var i = 0
                    while (i + 1 < bytesRead) {
                        val low = audioBuffer[i].toInt() and 0xFF
                        val high = audioBuffer[i + 1].toInt() and 0xFF
                        val sample = ((high shl 8) or low).toShort().toInt()
                        val level = abs(sample)
                        if (level > peak) peak = level

                        // Feed the FFT accumulator (normalized to -1..1)
                        fftInput[fftIndex++] = sample / MAX_16BIT
                        if (fftIndex >= FFT_SIZE) {
                            computeSpectrum()
                            fftIndex = 0
                        }
                        i += 2
                    }
                    _amplitude.value = peak / MAX_16BIT
                }
            }
            _amplitude.value = 0f
            Log.i(TAG, "Capture loop stopped")
        }.also { it.start() }
    }

    private fun computeSpectrum() {
        for (k in 0 until FFT_SIZE) {
            fftReal[k] = fftInput[k] * hannWindow[k]
            fftImag[k] = 0f
        }
        Fft.transform(fftReal, fftImag)

        val half = FFT_SIZE / 2
        val mags = FloatArray(half)
        for (k in 0 until half) {
            val mag = sqrt(fftReal[k] * fftReal[k] + fftImag[k] * fftImag[k])
            val normalized = mag / (FFT_SIZE / 2f)
            mags[k] = sqrt(normalized).coerceIn(0f, 1f) // sqrt = better visibility
        }
        _spectrum.value = mags
    }

    fun stop(): File? {
        if (!isRecording) return null
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        _amplitude.value = 0f
        _spectrum.value = FloatArray(FFT_SIZE / 2)

        pcmOutputStream?.flush()
        pcmOutputStream?.close()
        pcmOutputStream = null

        val pcm = pcmFile ?: return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val wav = File(pcm.parentFile, "recording_$stamp.wav")
        return try {
            pcmToWav(pcm, wav)
            pcm.delete()
            Log.i(TAG, "Saved WAV: ${wav.absolutePath} (${wav.length()} bytes)")
            wav
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV", e)
            null
        } finally {
            pcmFile = null
        }
    }

    fun release() {
        stop()
        audioRecord?.release()
        audioRecord = null
    }

    private fun pcmToWav(pcm: File, wav: File) {
        val pcmSize = pcm.length().toInt()
        val totalDataLen = pcmSize + 36
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE_HZ * channels * bitsPerSample / 8

        FileInputStream(pcm).use { input ->
            FileOutputStream(wav).use { output ->
                val header = ByteArray(44)
                header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
                writeIntLE(header, 4, totalDataLen)
                header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
                writeIntLE(header, 16, 16)
                writeShortLE(header, 20, 1)
                writeShortLE(header, 22, channels)
                writeIntLE(header, 24, SAMPLE_RATE_HZ)
                writeIntLE(header, 28, byteRate)
                writeShortLE(header, 32, channels * bitsPerSample / 8)
                writeShortLE(header, 34, bitsPerSample)
                header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
                writeIntLE(header, 40, pcmSize)
                output.write(header)
                input.copyTo(output)
            }
        }
    }

    private fun writeIntLE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xFF).toByte()
        b[offset + 1] = ((value shr 8) and 0xFF).toByte()
        b[offset + 2] = ((value shr 16) and 0xFF).toByte()
        b[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(b: ByteArray, offset: Int, value: Int) {
        b[offset] = (value and 0xFF).toByte()
        b[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}