package com.example.rawaudiorecorder.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File-backed store for recordings.
 *
 * Everything lives in the app-specific external files directory, so no runtime
 * storage permission is needed and the files are removed on uninstall:
 *   /Android/data/<package>/files/
 */
object RecordingsRepository {

    const val EXTENSION = "wav"

    /** Characters Android/FAT will not accept in a file name. */
    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    private const val MAX_NAME_LENGTH = 60

    fun dir(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /** All saved recordings, newest first. */
    fun list(context: Context): List<Recording> =
        dir(context)
            .listFiles { f -> f.isFile && f.extension.equals(EXTENSION, ignoreCase = true) }
            ?.map { it.toRecording() }
            ?.sortedByDescending { it.recordedAt }
            ?: emptyList()

    /**
     * Most recently modified WAV in the recordings directory.
     *
     * Fallback for wiring the save dialog when AudioCapture does not already
     * hand back the File it wrote. Prefer using the returned File directly —
     * this is a convenience, not a substitute for a real return value.
     */
    fun newest(context: Context): File? =
        dir(context)
            .listFiles { f -> f.isFile && f.extension.equals(EXTENSION, ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }

    fun File.toRecording(): Recording = Recording(
        file = this,
        name = nameWithoutExtension,
        sizeBytes = length(),
        durationMs = WavInfo.durationMs(this),
        recordedAt = lastModified()
    )

    /**
     * Strips characters that cannot appear in a file name and trims length.
     * Returns an empty string if nothing usable is left — callers should treat
     * that as invalid input rather than saving it.
     */
    fun sanitize(raw: String): String =
        raw.replace(ILLEGAL, "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(MAX_NAME_LENGTH)

    /** True if a recording with this display name already exists. */
    fun exists(context: Context, name: String): Boolean =
        File(dir(context), "$name.$EXTENSION").exists()

    /**
     * Resolves a name collision by appending " (2)", " (3)" and so on.
     * Returns [name] untouched when it is already free.
     */
    fun uniqueName(context: Context, name: String): String {
        if (!exists(context, name)) return name
        var n = 2
        while (exists(context, "$name ($n)")) n++
        return "$name ($n)"
    }

    /** Default suggestion shown in the save dialog. */
    fun suggestedName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date())
        return "Recording $stamp"
    }

    /**
     * Renames [file] to [newName] (extension added automatically).
     * Returns the new File on success, or null if the rename failed.
     */
    fun rename(context: Context, file: File, newName: String): File? {
        val clean = sanitize(newName)
        if (clean.isEmpty()) return null
        val target = File(dir(context), "${uniqueName(context, clean)}.$EXTENSION")
        if (target.absolutePath == file.absolutePath) return file
        return if (file.renameTo(target)) target else null
    }

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    // ---- display helpers -------------------------------------------------

    fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024        -> "%.0f KB".format(bytes / 1024f)
        else                 -> "$bytes B"
    }

    fun formatDate(millis: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
}
