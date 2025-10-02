// utils/FileUtils.kt
package com.quick.voice.recorder.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {
    
    fun createAudioFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Recording_$timestamp.m4a"
        
        val recordingsDir = File(context.getExternalFilesDir(null), "recordings")
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }
        
        return File(recordingsDir, fileName)
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
