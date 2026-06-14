package com.example.rawaudiorecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.rawaudiorecorder.ui.theme.RawAudioRecorderTheme
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.RECORD_AUDIO] == true) setupAudio()
            else Log.w("MainActivity", "Microphone permission denied")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensurePermissions()
        setContent {
            RawAudioRecorderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecorderScreen(
                        modifier = Modifier.padding(innerPadding),
                        amplitudeFlow = RecorderHolder.audioCapture.amplitude,
                        spectrumFlow = RecorderHolder.audioCapture.spectrum,
                        onStart = { sendCommand(RecordingService.ACTION_START) },
                        onStop = { sendCommand(RecordingService.ACTION_STOP) }
                    )
                }
            }
        }
    }

    private fun ensurePermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val allGranted = needed.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) setupAudio()
        else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun setupAudio() {
        val ready = RecorderHolder.audioCapture.initialize()
        Log.i("MainActivity", "Audio setup successful: $ready")
    }

    private fun sendCommand(action: String) {
        val intent = Intent(this, RecordingService::class.java).apply { this.action = action }
        if (action == RecordingService.ACTION_START) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun RecorderScreen(
    modifier: Modifier = Modifier,
    amplitudeFlow: StateFlow<Float>,
    spectrumFlow: StateFlow<FloatArray>,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var isRecording by remember { mutableStateOf(false) }
    val amplitude by amplitudeFlow.collectAsState()
    val spectrum by spectrumFlow.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = if (isRecording) "Recording…" else "Ready")
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Waveform")
        WaveformView(
            amplitude = amplitude,
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Spectrum")
        SpectrumView(
            spectrum = spectrum,
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            if (isRecording) onStop() else onStart()
            isRecording = !isRecording
        }) {
            Text(text = if (isRecording) "Stop" else "Start Recording")
        }
    }
}

@Composable
fun WaveformView(amplitude: Float, modifier: Modifier = Modifier) {
    val maxBars = 80
    val history = remember { mutableStateListOf<Float>() }

    LaunchedEffect(amplitude) {
        history.add(amplitude.coerceIn(0f, 1f))
        if (history.size > maxBars) history.removeAt(0)
    }

    Canvas(modifier = modifier) {
        if (history.isEmpty()) return@Canvas
        val slot = size.width / maxBars
        history.forEachIndexed { index, amp ->
            val barHeight = (amp * size.height).coerceAtLeast(2f)
            drawRect(
                color = Color(0xFF4CAF50),
                topLeft = Offset(index * slot, (size.height - barHeight) / 2f),
                size = Size(slot * 0.6f, barHeight)
            )
        }
    }
}

@Composable
fun SpectrumView(spectrum: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (spectrum.isEmpty()) return@Canvas
        val bars = 64
        val binsPerBar = (spectrum.size / bars).coerceAtLeast(1)
        val slot = size.width / bars
        for (b in 0 until bars) {
            var sum = 0f
            var count = 0
            val startBin = b * binsPerBar
            for (k in startBin until (startBin + binsPerBar)) {
                if (k < spectrum.size) { sum += spectrum[k]; count++ }
            }
            val value = if (count > 0) sum / count else 0f
            val barHeight = (value * size.height).coerceAtLeast(2f)
            drawRect(
                color = Color(0xFF2196F3),
                topLeft = Offset(b * slot, size.height - barHeight),
                size = Size(slot * 0.8f, barHeight)
            )
        }
    }
}