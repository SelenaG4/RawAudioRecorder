package com.example.rawaudiorecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rawaudiorecorder.data.Recording
import com.example.rawaudiorecorder.data.RecordingsRepository
import com.example.rawaudiorecorder.playback.AudioPlayer

/**
 * Browsable list of everything saved so far, newest first.
 *
 * Tapping a row plays it; the playing row expands to show a seek bar.
 * Rename and delete are per-row actions.
 */
@Composable
fun HistoryScreen(
    recordings: List<Recording>,
    player: AudioPlayer,
    playerState: AudioPlayer.State,
    onRename: (Recording, String) -> Unit,
    onDelete: (Recording) -> Unit,
    modifier: Modifier = Modifier
) {
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }

    if (recordings.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No recordings yet.\nTap record to make your first one.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recordings, key = { it.path }) { rec ->
            RecordingRow(
                recording = rec,
                isCurrent = playerState.path == rec.path,
                isPlaying = playerState.isPlaying && playerState.path == rec.path,
                progress = if (playerState.path == rec.path) playerState.progress else 0f,
                positionMs = if (playerState.path == rec.path) playerState.positionMs else 0,
                onToggle = { player.toggle(rec.file) },
                onSeek = { player.seekToFraction(it) },
                onRenameClick = { renaming = rec },
                onDeleteClick = { deleting = rec }
            )
        }
    }

    renaming?.let { rec ->
        RenameDialog(
            current = rec.name,
            onConfirm = { newName ->
                onRename(rec, newName)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete recording?") },
            text = { Text("\"${rec.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(rec)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    isCurrent: Boolean,
    isPlaying: Boolean,
    progress: Float,
    positionMs: Int,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                    if (isPlaying) {
                        PauseIcon(tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play ${recording.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = recording.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append(RecordingsRepository.formatDuration(recording.durationMs))
                            append("  ·  ")
                            append(RecordingsRepository.formatSize(recording.sizeBytes))
                            append("  ·  ")
                            append(RecordingsRepository.formatDate(recording.recordedAt))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onRenameClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "Rename")
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }

            if (isCurrent) {
                Slider(
                    value = progress,
                    onValueChange = onSeek,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        RecordingsRepository.formatDuration(positionMs.toLong()),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        RecordingsRepository.formatDuration(recording.durationMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    val cleaned = RecordingsRepository.sanitize(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename recording") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
                isError = cleaned.isEmpty(),
                supportingText = {
                    if (cleaned.isEmpty()) Text("Enter a name")
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(cleaned) },
                enabled = cleaned.isNotEmpty()
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
