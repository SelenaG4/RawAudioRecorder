package com.example.rawaudiorecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.rawaudiorecorder.data.RecordingsRepository
import com.example.rawaudiorecorder.playback.AudioPlayer
import java.io.File

/**
 * Shown after the user stops recording, before the file is committed.
 *
 * Offers three outcomes:
 *  - Save     → rename the pending file to the entered name
 *  - Redo     → discard and go straight back to recording
 *  - Discard  → delete the pending file and return to idle
 *
 * The user can replay the take here before deciding.
 *
 * @param pendingFile the WAV just written by the capture engine
 * @param durationMs  length of the take, for display
 * @param player      shared player, so preview and history never double up
 */
@Composable
fun SaveRecordingDialog(
    pendingFile: File,
    durationMs: Long,
    player: AudioPlayer,
    playerState: AudioPlayer.State,
    onSave: (String) -> Unit,
    onRedo: () -> Unit,
    onDiscard: () -> Unit,
    nameTaken: (String) -> Boolean
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(RecordingsRepository.suggestedName()) }

    val cleaned = RecordingsRepository.sanitize(name)
    val isEmpty = cleaned.isEmpty()
    val isDuplicate = !isEmpty && nameTaken(cleaned)
    val canSave = !isEmpty

    val isThisTrack = playerState.path == pendingFile.absolutePath
    val isPlaying = isThisTrack && playerState.isPlaying

    AlertDialog(
        onDismissRequest = { /* deliberate no-op: force an explicit choice */ },
        title = { Text("Save recording") },
        text = {
            Column {
                Text(
                    text = "Length ${RecordingsRepository.formatDuration(durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = isEmpty,
                    supportingText = {
                        when {
                            isEmpty -> Text("Enter a name")
                            isDuplicate -> Text(
                                "Name in use — will save as " +
                                    "\"${RecordingsRepository.uniqueName(context, cleaned)}\""
                            )
                            else -> Text("Saved as $cleaned.wav")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                // ---- preview playback ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    TextButton(onClick = { player.toggle(pendingFile) }) {
                        if (isPlaying) {
                            PauseIcon(tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play back")
                        }
                        Text(
                            text = if (isPlaying) "Pause" else "Replay",
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    Slider(
                        value = if (isThisTrack) playerState.progress else 0f,
                        onValueChange = { if (isThisTrack) player.seekToFraction(it) },
                        enabled = isThisTrack,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    player.stopIfPlaying(pendingFile)
                    onSave(cleaned)
                },
                enabled = canSave
            ) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    player.stopIfPlaying(pendingFile)
                    onDiscard()
                }) { Text("Discard") }

                TextButton(onClick = {
                    player.stopIfPlaying(pendingFile)
                    onRedo()
                }) { Text("Redo") }
            }
        }
    )
}
