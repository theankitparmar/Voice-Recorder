package com.quick.voice.recorder.demo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class AudioWaveformExtractor(private val context: Context) {

    suspend fun extractWaveform(
        audioFilePath: String,
        targetSamples: Int = 500
    ): List<Float> = withContext(Dispatchers.IO) {
        try {
            val file = File(audioFilePath)
            if (!file.exists()) {
                Log.e(TAG, "Audio file does not exist: $audioFilePath")
                return@withContext emptyList()
            }

            when (file.extension.lowercase()) {
                "wav" -> extractWaveformFromWav(audioFilePath, targetSamples)
                "mp3", "aac", "m4a" -> extractWaveformFromCompressed(audioFilePath, targetSamples)
                else -> {
                    Log.e(TAG, "Unsupported audio format: ${file.extension}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting waveform: ${e.message}", e)
            emptyList()
        }
    }

    private fun extractWaveformFromWav(filePath: String, targetSamples: Int): List<Float> {
        val amplitudes = mutableListOf<Float>()
        
        try {
            File(filePath).inputStream().use { inputStream ->
                // Skip WAV header (44 bytes)
                inputStream.skip(44)

                val buffer = ByteArray(4096)
                val samples = mutableListOf<Short>()
                
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    for (i in 0 until bytesRead step 2) {
                        if (i + 1 < bytesRead) {
                            // Convert 2 bytes to 16-bit sample (little-endian)
                            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                            samples.add(sample)
                        }
                    }
                }

                // Downsample to target number of samples
                amplitudes.addAll(downsampleAmplitudes(samples, targetSamples))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading WAV file: ${e.message}", e)
        }

        return amplitudes
    }

    private fun extractWaveformFromCompressed(filePath: String, targetSamples: Int): List<Float> {
        val amplitudes = mutableListOf<Float>()
        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(filePath)
            
            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                Log.e(TAG, "No audio track found")
                return emptyList()
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

            // Create decoder
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val samples = mutableListOf<Short>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false

            while (!isEOS) {
                // Feed input
                val inputBufferId = decoder.dequeueInputBuffer(10000)
                if (inputBufferId >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferId)
                    inputBuffer?.clear()
                    
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }

                // Get output
                val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferId >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferId)
                    
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        // Read PCM data
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputBuffer.clear()

                        // Convert to 16-bit samples
                        for (i in chunk.indices step 2) {
                            if (i + 1 < chunk.size) {
                                val sample = ((chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)).toShort()
                                samples.add(sample)
                            }
                        }
                    }

                    decoder.releaseOutputBuffer(outputBufferId, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            // Downsample to target number of samples
            amplitudes.addAll(downsampleAmplitudes(samples, targetSamples))

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding compressed audio: ${e.message}", e)
            extractor.release()
        }

        return amplitudes
    }

    private fun downsampleAmplitudes(samples: List<Short>, targetSamples: Int): List<Float> {
        if (samples.isEmpty()) return emptyList()
        if (samples.size <= targetSamples) {
            return samples.map { abs(it.toFloat()) / Short.MAX_VALUE }
        }

        val amplitudes = mutableListOf<Float>()
        val samplesPerBucket = samples.size.toFloat() / targetSamples

        for (i in 0 until targetSamples) {
            val start = (i * samplesPerBucket).toInt()
            val end = min(((i + 1) * samplesPerBucket).toInt(), samples.size)

            // Calculate RMS (Root Mean Square) for better representation
            var sum = 0.0
            for (j in start until end) {
                val normalized = samples[j].toFloat() / Short.MAX_VALUE
                sum += normalized * normalized
            }
            val rms = kotlin.math.sqrt(sum / (end - start))
            amplitudes.add(rms.toFloat())
        }

        return normalizeAmplitudes(amplitudes)
    }

    private fun normalizeAmplitudes(amplitudes: List<Float>): List<Float> {
        if (amplitudes.isEmpty()) return emptyList()

        val maxAmplitude = amplitudes.maxOrNull() ?: 1f
        if (maxAmplitude == 0f) return amplitudes

        return amplitudes.map { it / maxAmplitude }
    }

    companion object {
        private const val TAG = "AudioWaveformExtractor"
    }
}
